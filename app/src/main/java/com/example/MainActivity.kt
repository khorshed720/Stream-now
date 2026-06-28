package com.example

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                viewModel.handleSignInResult(account)
            } catch (e: ApiException) {
                Log.w("MainActivity", "Google sign in failed", e)
                viewModel.handleSignInResult(null)
            }
        }
    }
    
    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) {
            requestMediaProjection()
        } else {
            Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val mediaProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startLiveStream(
                title = "Android Gameplay Live",
                description = "Streaming directly from my Android phone using Streamer Controller!",
                mediaProjectionResultCode = result.resultCode,
                mediaProjectionData = result.data!!
            )
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel,
                        onSignInClick = {
                            val intent = GoogleSignIn.getClient(this, viewModel.getGoogleSignInOptions()).signInIntent
                            signInLauncher.launch(intent)
                        },
                        onStartStreamClick = {
                            checkPermissionsAndStart()
                        }
                    )
                }
            }
        }
    }
    
    private val permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                overlayPermissionLauncher.launch(intent)
            } else {
                requestMediaProjection()
            }
        } else {
            Toast.makeText(this, "Permissions are required for streaming", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }
    
    private fun requestMediaProjection() {
        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    onSignInClick: () -> Unit,
    onStartStreamClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = uiState) {
            is UiState.Loading -> {
                CircularProgressIndicator()
            }
            is UiState.Unauthenticated -> {
                Text(
                    text = "Welcome to Streamer Controller",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                Button(onClick = onSignInClick) {
                    Text("Sign in with Google")
                }
                Text(
                    text = "Required for YouTube Live integration",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            is UiState.Authenticated -> {
                Text(
                    text = "Welcome, ${state.account.displayName}!",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                Button(onClick = onStartStreamClick) {
                    Text("Setup Livestream")
                }
                Text(
                    text = "This will prompt for screen capture and overlay permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            is UiState.StartingStream -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Creating broadcast and setting up RTMP...")
            }
            is UiState.StreamReady -> {
                Text(
                    text = "Stream Ready!",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text("You can now control the stream from the floating widget.")
                Text("RTMP URL: ${state.rtmpUrl}", style = MaterialTheme.typography.bodySmall)
            }
            is UiState.Error -> {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(onClick = onSignInClick) {
                    Text("Retry Sign In")
                }
            }
        }
    }
}
