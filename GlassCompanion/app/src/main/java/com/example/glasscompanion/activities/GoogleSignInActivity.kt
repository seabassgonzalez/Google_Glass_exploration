package com.example.glasscompanion.activities

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.glasscompanion.services.BluetoothService
import com.example.glasscompanion.services.GoogleAuthManager
import com.example.glasscompanion.ui.theme.GlassCompanionTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import org.json.JSONObject

class GoogleSignInActivity : ComponentActivity() {
    
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var bluetoothService: BluetoothService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        googleAuthManager = GoogleAuthManager(this)
        bluetoothService = BluetoothService(this)
        
        // Handle OAuth callback if this is a redirect
        intent?.data?.let { uri ->
            if (uri.scheme == "glasscompanion" && uri.host == "google" && uri.path == "/oauth") {
                lifecycleScope.launch {
                    val success = googleAuthManager.handleOAuthCallback(uri)
                    if (success) {
                        Toast.makeText(this@GoogleSignInActivity, "Successfully signed in to Google!", Toast.LENGTH_SHORT).show()
                        // Send credentials to Glass if connected
                        sendCredentialsToGlass()
                    }
                }
            }
        }
        
        setContent {
            GlassCompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoogleSignInScreen(
                        authManager = googleAuthManager,
                        bluetoothService = bluetoothService,
                        onSignIn = { googleAuthManager.startOAuthFlow(this) },
                        onSendToGlass = { sendCredentialsToGlass() }
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { uri ->
            if (uri.scheme == "glasscompanion" && uri.host == "google" && uri.path == "/oauth") {
                lifecycleScope.launch {
                    googleAuthManager.handleOAuthCallback(uri)
                }
            }
        }
    }
    
    private fun sendCredentialsToGlass() {
        val credentials = googleAuthManager.getCredentialsForGlass()
        if (credentials != null && bluetoothService.isConnected()) {
            val success = bluetoothService.sendGoogleCredentials(credentials)
            if (success) {
                Toast.makeText(this, "Google credentials sent to Glass", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to send credentials. Connect to Glass first.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSignInScreen(
    authManager: GoogleAuthManager,
    bluetoothService: BluetoothService,
    onSignIn: () -> Unit,
    onSendToGlass: () -> Unit
) {
    val authState by authManager.authState.collectAsState()
    val context = LocalContext.current
    var showQRCode by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "Google Account",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Google Glass Sign-In",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = if (authState.isAuthenticated) 
                        "Signed in as ${authState.userEmail}"
                    else 
                        "Sign in to enable Glass features",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        // User Profile Card (if authenticated)
        if (authState.isAuthenticated) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile picture
                    authState.userPhoto?.let { photoUrl ->
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    // User info
                    Text(
                        text = authState.userName ?: "Glass User",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = authState.userEmail ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    
                    if (authState.userId != null) {
                        Text(
                            text = "ID: ${authState.userId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Sign out button
                    OutlinedButton(
                        onClick = { authManager.signOut() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Logout,
                            contentDescription = "Sign Out",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        } else {
            // Sign in card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sign in with Google",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Text(
                        text = "This will enable Glass to access your Google services including Timeline, Contacts, and more.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Button(
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4285F4) // Google blue
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "Google",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with Google")
                    }
                }
            }
        }
        
        // Glass Integration Actions (if authenticated)
        if (authState.isAuthenticated) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Glass Integration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Send to Glass button
                    Button(
                        onClick = onSendToGlass,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = bluetoothService.isConnected()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bluetooth,
                            contentDescription = "Send",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Credentials to Glass")
                    }
                    
                    if (!bluetoothService.isConnected()) {
                        Text(
                            text = "Connect to Glass via Bluetooth first",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // QR Code option
                    OutlinedButton(
                        onClick = { showQRCode = !showQRCode },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCode,
                            contentDescription = "QR",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (showQRCode) "Hide QR Code" else "Show QR Code")
                    }
                }
            }
            
            // QR Code Display
            if (showQRCode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Scan with Glass Camera",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        authManager.getCredentialsForGlass()?.let { credentials ->
                            val qrBitmap = generateGoogleQRCode(credentials)
                            qrBitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .padding(8.dp)
                                )
                            }
                        }
                        
                        Text(
                            text = "QR contains encrypted credentials",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            
            // Glass Features Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Glass Features Enabled",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    GoogleFeatureRow(Icons.Filled.Timeline, "Timeline Cards", true)
                    GoogleFeatureRow(Icons.Filled.Contacts, "Contact Integration", true)
                    GoogleFeatureRow(Icons.Filled.Map, "Maps & Navigation", true)
                    GoogleFeatureRow(Icons.Filled.Event, "Calendar Events", true)
                    GoogleFeatureRow(Icons.Filled.Email, "Gmail Notifications", true)
                    GoogleFeatureRow(Icons.Filled.PhotoCamera, "Photos Backup", true)
                }
            }
        }
        
        // Error display
        authState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun GoogleFeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
        )
        Spacer(modifier = Modifier.weight(1f))
        if (enabled) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Enabled",
                modifier = Modifier.size(16.dp),
                tint = Color.Green
            )
        }
    }
}

fun generateGoogleQRCode(credentials: GoogleAuthManager.GoogleCredentials): Bitmap? {
    try {
        val writer = QRCodeWriter()
        val json = JSONObject().apply {
            put("at", credentials.accessToken)
            put("rt", credentials.refreshToken)
            put("ea", credentials.expiresAt)
            put("email", credentials.userEmail)
            put("name", credentials.userName)
            put("id", credentials.userId)
        }.toString()
        
        val bitMatrix = writer.encode(json, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    } catch (e: WriterException) {
        e.printStackTrace()
        return null
    }
}