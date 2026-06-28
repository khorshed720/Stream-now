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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
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
                viewModel.handleSignInFailed(e)
            }
        } else {
            Log.w("MainActivity", "Google sign in cancelled or failed. Result code: ${result.resultCode}")
            viewModel.handleSignInResult(null)
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
                            viewModel.enterStreamSetup()
                        },
                        onConfirmSetupClick = {
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
    onStartStreamClick: () -> Unit,
    onConfirmSetupClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            // Header Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = "Stream Logo",
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            when (val state = uiState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
                is UiState.Unauthenticated -> {
                    Text(
                        text = "Streamer Controller",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Connect to broadcast live",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 48.dp)
                    )
                    
                    Button(
                        onClick = onSignInClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = "Google Icon",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with Google",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Required for YouTube Live integration",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UiState.Authenticated -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Welcome back,",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = state.account.displayName ?: "Streamer",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )
                            Button(
                                onClick = onStartStreamClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Setup Stream",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Setup Livestream",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Set title, description, and streaming preferences.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                is UiState.StreamSetup -> {
                    var title by remember { mutableStateOf(viewModel.streamTitle) }
                    var description by remember { mutableStateOf(viewModel.streamDescription) }
                    var isForKids by remember { mutableStateOf(viewModel.isForKids) }
                    
                    var expandedAudio by remember { mutableStateOf(false) }
                    var audioSource by remember { mutableStateOf(viewModel.audioSource) }

                    var expandedPrivacy by remember { mutableStateOf(false) }
                    var privacyStatus by remember { mutableStateOf(viewModel.privacyStatus) }

                    var expandedResolution by remember { mutableStateOf(false) }
                    var resolution by remember { mutableStateOf(viewModel.resolution) }

                    var bannerIntervalStr by remember { mutableStateOf(viewModel.bannerInterval.toString()) }

                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        Text("Stream Setup", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                        
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it; viewModel.streamTitle = it },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it; viewModel.streamDescription = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            minLines = 3
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            Checkbox(checked = isForKids, onCheckedChange = { isForKids = it; viewModel.isForKids = it })
                            Text("Yes, it's made for kids")
                        }

                        // Privacy Status Dropdown
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Button(onClick = { expandedPrivacy = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Privacy: $privacyStatus")
                            }
                            DropdownMenu(
                                expanded = expandedPrivacy,
                                onDismissRequest = { expandedPrivacy = false }
                            ) {
                                DropdownMenuItem(text = { Text("Public") }, onClick = { privacyStatus = "Public"; viewModel.privacyStatus = "Public"; expandedPrivacy = false })
                                DropdownMenuItem(text = { Text("Unlisted") }, onClick = { privacyStatus = "Unlisted"; viewModel.privacyStatus = "Unlisted"; expandedPrivacy = false })
                                DropdownMenuItem(text = { Text("Private") }, onClick = { privacyStatus = "Private"; viewModel.privacyStatus = "Private"; expandedPrivacy = false })
                            }
                        }

                        // Resolution Dropdown
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Button(onClick = { expandedResolution = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Quality: $resolution")
                            }
                            DropdownMenu(
                                expanded = expandedResolution,
                                onDismissRequest = { expandedResolution = false }
                            ) {
                                DropdownMenuItem(text = { Text("1080p") }, onClick = { resolution = "1080p"; viewModel.resolution = "1080p"; expandedResolution = false })
                                DropdownMenuItem(text = { Text("720p") }, onClick = { resolution = "720p"; viewModel.resolution = "720p"; expandedResolution = false })
                                DropdownMenuItem(text = { Text("480p") }, onClick = { resolution = "480p"; viewModel.resolution = "480p"; expandedResolution = false })
                            }
                        }

                        // Scheduling
                        var isScheduled by remember { mutableStateOf(viewModel.isScheduled) }
                        var scheduledTime by remember { mutableStateOf(viewModel.scheduledTime) }
                        
                        val context = androidx.compose.ui.platform.LocalContext.current
                        var hasThumbnail by remember { mutableStateOf(viewModel.thumbnailBytes != null) }
                        var hasBanner by remember { mutableStateOf(viewModel.bannerBytes != null) }
                        
                        val thumbnailPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
                            uri?.let {
                                context.contentResolver.openInputStream(it)?.use { inputStream ->
                                    viewModel.thumbnailBytes = inputStream.readBytes()
                                    hasThumbnail = true
                                }
                            }
                        }
                        
                        val bannerPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
                            uri?.let {
                                context.contentResolver.openInputStream(it)?.use { inputStream ->
                                    viewModel.bannerBytes = inputStream.readBytes()
                                    hasBanner = true
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            Checkbox(checked = isScheduled, onCheckedChange = { isScheduled = it; viewModel.isScheduled = it })
                            Text("Schedule for later")
                        }
                        
                        if (isScheduled) {
                            OutlinedTextField(
                                value = scheduledTime,
                                onValueChange = { scheduledTime = it; viewModel.scheduledTime = it },
                                label = { Text("Time (YYYY-MM-DDTHH:mm:ssZ)") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                placeholder = { Text("2026-12-31T15:00:00Z") }
                            )
                        }

                        // Thumbnail
                        Button(onClick = { thumbnailPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text(if (hasThumbnail) "Thumbnail Selected" else "Select Thumbnail")
                        }
                        
                        // Audio Source Dropdown
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Button(onClick = { expandedAudio = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Audio Source: $audioSource")
                            }
                            DropdownMenu(
                                expanded = expandedAudio,
                                onDismissRequest = { expandedAudio = false }
                            ) {
                                DropdownMenuItem(text = { Text("Internal") }, onClick = { audioSource = "Internal"; viewModel.audioSource = "Internal"; expandedAudio = false })
                                DropdownMenuItem(text = { Text("Mic") }, onClick = { audioSource = "Mic"; viewModel.audioSource = "Mic"; expandedAudio = false })
                                DropdownMenuItem(text = { Text("Internal + Mic") }, onClick = { audioSource = "Internal + Mic"; viewModel.audioSource = "Internal + Mic"; expandedAudio = false })
                            }
                        }

                        // Banner Settings
                        Button(onClick = { bannerPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text(if (hasBanner) "Banner Selected" else "Upload Banner")
                        }
                        OutlinedTextField(
                            value = bannerIntervalStr,
                            onValueChange = { bannerIntervalStr = it; viewModel.bannerInterval = it.toIntOrNull() ?: 10 },
                            label = { Text("Banner Interval (seconds)") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        
                        Button(
                            onClick = onConfirmSetupClick,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text("Go Live")
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
                is UiState.StartingStream -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Preparing Broadcast",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Setting up RTMP connection...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
                is UiState.StreamReady -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Ready",
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(bottom = 12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Stream Ready!",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "You can now control the stream from the floating widget overlay on your screen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "RTMP URL: ${state.rtmpUrl}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                is UiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Error",
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(bottom = 12.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Something went wrong",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                            Button(
                                onClick = onSignInClick,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                    contentColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text("Try Again")
                            }
                        }
                    }
                }
            }
        }
    }
}
