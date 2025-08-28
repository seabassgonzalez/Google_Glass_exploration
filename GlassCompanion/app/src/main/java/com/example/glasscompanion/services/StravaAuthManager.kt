package com.example.glasscompanion.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.glasscompanion.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Manages Strava OAuth authentication and token storage
 */
class StravaAuthManager(private val context: Context) {
    
    companion object {
        private const val TAG = "StravaAuthManager"
        private const val PREFS_NAME = "strava_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_ATHLETE_ID = "athlete_id"
        private const val KEY_ATHLETE_NAME = "athlete_name"
        
        private const val AUTH_BASE_URL = "https://www.strava.com/oauth/mobile/authorize"
        private const val TOKEN_URL = "https://www.strava.com/oauth/token"
        private const val REDIRECT_URI = "glasscompanion://oauth"
        
        // Scopes for Strava access
        private const val SCOPE = "activity:read_all,activity:write,read,profile:read_all,read_all,profile:write"
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // Encrypted SharedPreferences for secure token storage
    private val encryptedPrefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // Authentication state
    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState
    
    data class AuthState(
        val isAuthenticated: Boolean = false,
        val athleteName: String? = null,
        val athleteId: String? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    )
    
    data class TokenResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("refresh_token") val refreshToken: String,
        @SerializedName("expires_at") val expiresAt: Long,
        @SerializedName("athlete") val athlete: Athlete
    )
    
    data class Athlete(
        @SerializedName("id") val id: Long,
        @SerializedName("firstname") val firstName: String,
        @SerializedName("lastname") val lastName: String,
        @SerializedName("profile") val profilePicture: String?
    )
    
    init {
        // Check if already authenticated
        checkAuthenticationStatus()
    }
    
    private fun checkAuthenticationStatus() {
        val accessToken = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = encryptedPrefs.getLong(KEY_EXPIRES_AT, 0)
        val athleteName = encryptedPrefs.getString(KEY_ATHLETE_NAME, null)
        val athleteId = encryptedPrefs.getString(KEY_ATHLETE_ID, null)
        
        if (accessToken != null && System.currentTimeMillis() / 1000 < expiresAt) {
            _authState.value = AuthState(
                isAuthenticated = true,
                athleteName = athleteName,
                athleteId = athleteId
            )
        } else if (accessToken != null) {
            // Token expired, need to refresh
            refreshAccessToken()
        }
    }
    
    /**
     * Initiates OAuth flow in Chrome Custom Tab
     */
    fun startOAuthFlow(context: Context) {
        val authUrl = Uri.parse(AUTH_BASE_URL)
            .buildUpon()
            .appendQueryParameter("client_id", BuildConfig.STRAVA_CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("approval_prompt", "auto")
            .appendQueryParameter("scope", SCOPE)
            .build()
        
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()
        
        customTabsIntent.launchUrl(context, authUrl)
        
        _authState.value = _authState.value.copy(isLoading = true)
    }
    
    /**
     * Handles OAuth callback and exchanges code for tokens
     */
    suspend fun handleOAuthCallback(uri: Uri): Boolean {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        
        if (error != null) {
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = "Authentication failed: $error"
            )
            return false
        }
        
        if (code != null) {
            return exchangeCodeForToken(code)
        }
        
        return false
    }
    
    private suspend fun exchangeCodeForToken(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val requestBody = FormBody.Builder()
                .add("client_id", BuildConfig.STRAVA_CLIENT_ID)
                .add("client_secret", BuildConfig.STRAVA_CLIENT_SECRET)
                .add("code", code)
                .add("grant_type", "authorization_code")
                .build()
            
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(requestBody)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val tokenResponse = gson.fromJson(responseBody, TokenResponse::class.java)
                
                // Save tokens securely
                saveTokens(tokenResponse)
                
                _authState.value = AuthState(
                    isAuthenticated = true,
                    athleteName = "${tokenResponse.athlete.firstName} ${tokenResponse.athlete.lastName}",
                    athleteId = tokenResponse.athlete.id.toString()
                )
                
                return@withContext true
            } else {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = "Failed to exchange code: ${response.code}"
                )
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging code for token", e)
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = "Error: ${e.message}"
            )
            return@withContext false
        }
    }
    
    private fun saveTokens(tokenResponse: TokenResponse) {
        encryptedPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, tokenResponse.accessToken)
            putString(KEY_REFRESH_TOKEN, tokenResponse.refreshToken)
            putLong(KEY_EXPIRES_AT, tokenResponse.expiresAt)
            putString(KEY_ATHLETE_ID, tokenResponse.athlete.id.toString())
            putString(KEY_ATHLETE_NAME, "${tokenResponse.athlete.firstName} ${tokenResponse.athlete.lastName}")
            apply()
        }
    }
    
    private fun refreshAccessToken() {
        // Implementation for token refresh
        val refreshToken = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null) ?: return
        
        // TODO: Implement refresh token flow
    }
    
    /**
     * Gets current access token for API calls
     */
    fun getAccessToken(): String? {
        val token = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = encryptedPrefs.getLong(KEY_EXPIRES_AT, 0)
        
        return if (token != null && System.currentTimeMillis() / 1000 < expiresAt) {
            token
        } else {
            null
        }
    }
    
    /**
     * Gets credentials for transfer to Glass
     */
    fun getCredentialsForGlass(): StravaCredentials? {
        val accessToken = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
        val expiresAt = encryptedPrefs.getLong(KEY_EXPIRES_AT, 0)
        val athleteId = encryptedPrefs.getString(KEY_ATHLETE_ID, null)
        val athleteName = encryptedPrefs.getString(KEY_ATHLETE_NAME, null)
        
        return if (accessToken != null && refreshToken != null) {
            StravaCredentials(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                athleteId = athleteId ?: "",
                athleteName = athleteName ?: ""
            )
        } else {
            null
        }
    }
    
    /**
     * Signs out and clears all credentials
     */
    fun signOut() {
        encryptedPrefs.edit().clear().apply()
        _authState.value = AuthState()
    }
    
    data class StravaCredentials(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
        val athleteId: String,
        val athleteName: String
    )
}