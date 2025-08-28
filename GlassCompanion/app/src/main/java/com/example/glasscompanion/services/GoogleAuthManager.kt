package com.example.glasscompanion.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Manages Google OAuth authentication and token storage for Glass
 */
class GoogleAuthManager(private val context: Context) {
    
    companion object {
        private const val TAG = "GoogleAuthManager"
        private const val PREFS_NAME = "google_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "google_access_token"
        private const val KEY_REFRESH_TOKEN = "google_refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_PHOTO = "user_photo"
        
        // Google OAuth endpoints
        private const val AUTH_BASE_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
        private const val REDIRECT_URI = "glasscompanion://google/oauth"
        
        // OAuth Client ID for Glass Companion (you'll need to register this in Google Cloud Console)
        // For now, using the public client flow without client secret
        private const val CLIENT_ID = com.example.glasscompanion.BuildConfig.GOOGLE_CLIENT_ID
        
        // Scopes for Google Glass access
        private const val SCOPE = "openid email profile " +
                "https://www.googleapis.com/auth/userinfo.profile " +
                "https://www.googleapis.com/auth/userinfo.email " +
                "https://www.googleapis.com/auth/glass.timeline " +
                "https://www.googleapis.com/auth/glass.location"
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // PKCE (Proof Key for Code Exchange) parameters for secure OAuth
    private var codeVerifier: String? = null
    private var codeChallenge: String? = null
    
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
    private val _authState = MutableStateFlow(GoogleAuthState())
    val authState: StateFlow<GoogleAuthState> = _authState
    
    data class GoogleAuthState(
        val isAuthenticated: Boolean = false,
        val userEmail: String? = null,
        val userName: String? = null,
        val userId: String? = null,
        val userPhoto: String? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    )
    
    data class TokenResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("refresh_token") val refreshToken: String?,
        @SerializedName("expires_in") val expiresIn: Long,
        @SerializedName("token_type") val tokenType: String,
        @SerializedName("scope") val scope: String?
    )
    
    data class UserInfo(
        @SerializedName("id") val id: String,
        @SerializedName("email") val email: String,
        @SerializedName("name") val name: String,
        @SerializedName("picture") val picture: String?
    )
    
    init {
        // Check if already authenticated
        checkAuthenticationStatus()
    }
    
    private fun checkAuthenticationStatus() {
        val accessToken = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = encryptedPrefs.getLong(KEY_EXPIRES_AT, 0)
        val userEmail = encryptedPrefs.getString(KEY_USER_EMAIL, null)
        val userName = encryptedPrefs.getString(KEY_USER_NAME, null)
        val userId = encryptedPrefs.getString(KEY_USER_ID, null)
        val userPhoto = encryptedPrefs.getString(KEY_USER_PHOTO, null)
        
        if (accessToken != null && System.currentTimeMillis() / 1000 < expiresAt) {
            _authState.value = GoogleAuthState(
                isAuthenticated = true,
                userEmail = userEmail,
                userName = userName,
                userId = userId,
                userPhoto = userPhoto
            )
        } else if (accessToken != null) {
            // Token expired, need to refresh
            refreshAccessToken()
        }
    }
    
    /**
     * Generates PKCE code verifier and challenge for secure OAuth flow
     */
    private fun generatePKCEParameters() {
        // Generate code verifier (43-128 characters)
        val secureRandom = SecureRandom()
        val codeVerifierBytes = ByteArray(32)
        secureRandom.nextBytes(codeVerifierBytes)
        codeVerifier = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(codeVerifierBytes)
        
        // Generate code challenge (SHA256 hash of verifier)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(codeVerifier!!.toByteArray())
        codeChallenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(hash)
    }
    
    /**
     * Initiates Google OAuth flow in Chrome Custom Tab
     */
    fun startOAuthFlow(context: Context) {
        // Generate PKCE parameters for security
        generatePKCEParameters()
        
        // Generate random state for CSRF protection
        val state = UUID.randomUUID().toString()
        encryptedPrefs.edit().putString("oauth_state", state).apply()
        
        val authUrl = Uri.parse(AUTH_BASE_URL)
            .buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
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
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        
        // Verify state to prevent CSRF
        val savedState = encryptedPrefs.getString("oauth_state", null)
        if (state != savedState) {
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = "Invalid state parameter - possible security issue"
            )
            return false
        }
        
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
                .add("client_id", CLIENT_ID)
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier ?: "")
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
                
                // Fetch user info
                fetchUserInfo(tokenResponse.accessToken)
                
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
    
    private suspend fun fetchUserInfo(accessToken: String) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(USER_INFO_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val userInfo = gson.fromJson(responseBody, UserInfo::class.java)
                
                // Save user info
                encryptedPrefs.edit().apply {
                    putString(KEY_USER_EMAIL, userInfo.email)
                    putString(KEY_USER_NAME, userInfo.name)
                    putString(KEY_USER_ID, userInfo.id)
                    putString(KEY_USER_PHOTO, userInfo.picture)
                    apply()
                }
                
                _authState.value = GoogleAuthState(
                    isAuthenticated = true,
                    userEmail = userInfo.email,
                    userName = userInfo.name,
                    userId = userInfo.id,
                    userPhoto = userInfo.picture
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user info", e)
        }
    }
    
    private fun saveTokens(tokenResponse: TokenResponse) {
        val expiresAt = System.currentTimeMillis() / 1000 + tokenResponse.expiresIn
        
        encryptedPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, tokenResponse.accessToken)
            tokenResponse.refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
            putLong(KEY_EXPIRES_AT, expiresAt)
            apply()
        }
    }
    
    private fun refreshAccessToken() {
        val refreshToken = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null) ?: return
        
        // TODO: Implement refresh token flow
        // This would involve making a request to TOKEN_URL with grant_type=refresh_token
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
    fun getCredentialsForGlass(): GoogleCredentials? {
        val accessToken = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
        val expiresAt = encryptedPrefs.getLong(KEY_EXPIRES_AT, 0)
        val userEmail = encryptedPrefs.getString(KEY_USER_EMAIL, null)
        val userName = encryptedPrefs.getString(KEY_USER_NAME, null)
        val userId = encryptedPrefs.getString(KEY_USER_ID, null)
        
        return if (accessToken != null) {
            GoogleCredentials(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                userEmail = userEmail ?: "",
                userName = userName ?: "",
                userId = userId ?: ""
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
        _authState.value = GoogleAuthState()
    }
    
    data class GoogleCredentials(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAt: Long,
        val userEmail: String,
        val userName: String,
        val userId: String
    )
}