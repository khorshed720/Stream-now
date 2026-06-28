package com.example

import android.accounts.Account
import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.YouTubeRepository
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private var auth: FirebaseAuth? = null
    private var db: FirebaseFirestore? = null
    private val youtubeRepo = YouTubeRepository()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private var googleAccount: GoogleSignInAccount? = null

    init {
        try {
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()
            checkAuth()
        } catch (e: IllegalStateException) {
            _uiState.value = UiState.Error("Firebase is not initialized. Please add google-services.json to the app folder.")
        }
    }

    private fun checkAuth() {
        if (auth == null) {
            _uiState.value = UiState.Error("Firebase is not initialized.")
            return
        }
        val account = GoogleSignIn.getLastSignedInAccount(getApplication())
        if (account != null && auth?.currentUser != null) {
            googleAccount = account
            _uiState.value = UiState.Authenticated(account)
        } else {
            _uiState.value = UiState.Unauthenticated
        }
    }

    fun getGoogleSignInOptions(): GoogleSignInOptions {
        // Use default_web_client_id from google-services.json
        val clientId = getApplication<Application>().getString(R.string.default_web_client_id)
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/youtube"))
            .build()
    }

    fun handleSignInFailed(exception: ApiException) {
        val statusCode = exception.statusCode
        val errorMessage = when (statusCode) {
            10 -> "Sign in failed (Error 10: DEVELOPER_ERROR). Please ensure the SHA-1 fingerprint of the app's signing certificate is correctly registered in your Firebase project settings."
            12500 -> "Sign in failed (Error 12500). Please ensure your Firebase project's support email is set in project settings. Make sure your google services json has the correct info."
            else -> "Google Sign-In failed with status code: $statusCode"
        }
        _uiState.value = UiState.Error(errorMessage)
    }

    fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account == null) {
            _uiState.value = UiState.Error("Sign in failed or was cancelled. Please check your network connection and try again.")
            return
        }
        
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth?.signInWithCredential(credential)
                
                // Save user info to Firestore
                val user = auth?.currentUser
                if (user != null) {
                    db?.collection("users")?.document(user.uid)?.set(
                        mapOf(
                            "email" to user.email,
                            "displayName" to user.displayName,
                            "lastLogin" to System.currentTimeMillis()
                        )
                    )
                }
                
                googleAccount = account
                _uiState.value = UiState.Authenticated(account)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Firebase Auth failed")
            }
        }
    }

    fun enterStreamSetup() {
        if (googleAccount != null) {
            _uiState.value = UiState.StreamSetup
        }
    }

    var streamTitle: String = "Android Gameplay Live"
    var streamDescription: String = "Streaming directly from my Android phone using Streamer Controller!"
    var isForKids: Boolean = false
    var privacyStatus: String = "Public"
    var resolution: String = "1080p"
    var isScheduled: Boolean = false
    var scheduledTime: String = "" // e.g., "2026-10-31T15:00:00Z"
    var audioSource: String = "Internal"
    var bannerUrl: String = ""
    var bannerInterval: Int = 10
    var thumbnailBytes: ByteArray? = null
    var bannerBytes: ByteArray? = null

    fun startLiveStream(
        mediaProjectionResultCode: Int,
        mediaProjectionData: Intent
    ) {
        val account = googleAccount ?: return
        _uiState.value = UiState.StartingStream
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Get OAuth token for YouTube API
                val token = GoogleAuthUtil.getToken(
                    getApplication(),
                    account.account!!,
                    "oauth2:https://www.googleapis.com/auth/youtube"
                )

                // 2. Create Broadcast & Stream
                val (broadcast, stream) = youtubeRepo.createAndBindBroadcast(
                    token = token,
                    title = streamTitle,
                    description = streamDescription,
                    privacyStatus = privacyStatus,
                    resolution = resolution,
                    scheduledStartTime = if (isScheduled && scheduledTime.isNotEmpty()) scheduledTime else null
                )
                val streamId = stream.id ?: throw Exception("Invalid stream ID")
                val rtmpUrl = stream.cdn?.ingestionInfo?.ingestionAddress + "/" + stream.cdn?.ingestionInfo?.streamName

                val bId = broadcast.id
                if (bId != null && thumbnailBytes != null) {
                    youtubeRepo.setThumbnail(token, bId, thumbnailBytes!!)
                }

                // 3. Start Service to capture screen and push to RTMP
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    val serviceIntent = Intent(context, com.example.capture.StreamService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    
                    // We need to pass the media projection intent and RTMP url to the service.
                    // A better way is to bind the service and call a method, but for simplicity we can wait a bit or use a singleton/broadcast.
                    // Let's use a companion object in StreamService for simplicity, or just bind to it.
                    com.example.capture.ServiceLocator.rtmpUrl = rtmpUrl
                    com.example.capture.ServiceLocator.resultCode = mediaProjectionResultCode
                    com.example.capture.ServiceLocator.data = mediaProjectionData
                    com.example.capture.ServiceLocator.youtubeToken = token
                    com.example.capture.ServiceLocator.broadcastId = broadcast.id
                    com.example.capture.ServiceLocator.resolution = resolution
                    com.example.capture.ServiceLocator.audioSource = audioSource
                    com.example.capture.ServiceLocator.bannerBytes = bannerBytes
                    com.example.capture.ServiceLocator.bannerInterval = bannerInterval
                }

                _uiState.value = UiState.StreamReady(rtmpUrl)

            } catch (e: Exception) {
                Log.e("MainViewModel", "Start stream error", e)
                _uiState.value = UiState.Error(e.message ?: "Failed to start stream")
                withContext(Dispatchers.Main) {
                    checkAuth()
                }
            }
        }
    }

    fun signOut() {
        auth?.signOut()
        val gsc = GoogleSignIn.getClient(getApplication(), getGoogleSignInOptions())
        gsc.signOut()
        googleAccount = null
        _uiState.value = UiState.Unauthenticated
    }
}

sealed class UiState {
    object Loading : UiState()
    object Unauthenticated : UiState()
    data class Authenticated(val account: GoogleSignInAccount) : UiState()
    object StreamSetup : UiState()
    object StartingStream : UiState()
    data class StreamReady(val rtmpUrl: String) : UiState()
    data class Error(val message: String) : UiState()
}
