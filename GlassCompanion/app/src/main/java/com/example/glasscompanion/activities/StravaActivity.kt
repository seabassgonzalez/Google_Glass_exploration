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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.glasscompanion.services.BluetoothService
import com.example.glasscompanion.services.StravaAuthManager
import com.example.glasscompanion.ui.theme.GlassCompanionTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import org.json.JSONObject

class StravaActivity : ComponentActivity() {
    
    private lateinit var stravaAuthManager: StravaAuthManager
    private lateinit var bluetoothService: BluetoothService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        stravaAuthManager = StravaAuthManager(this)
        bluetoothService = BluetoothService(this)
        
        // Handle OAuth callback if this is a redirect
        intent?.data?.let { uri ->
            if (uri.scheme == "glasscompanion" && uri.host == "oauth") {
                lifecycleScope.launch {
                    val success = stravaAuthManager.handleOAuthCallback(uri)
                    if (success) {
                        Toast.makeText(this@StravaActivity, "Successfully connected to Strava!", Toast.LENGTH_SHORT).show()
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
                    StravaScreen(
                        authManager = stravaAuthManager,
                        bluetoothService = bluetoothService,
                        onSignIn = { stravaAuthManager.startOAuthFlow(this) },
                        onSendToGlass = { sendCredentialsToGlass() }
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { uri ->
            if (uri.scheme == "glasscompanion" && uri.host == "oauth") {
                lifecycleScope.launch {
                    stravaAuthManager.handleOAuthCallback(uri)
                }
            }
        }
    }
    
    private fun sendCredentialsToGlass() {
        val credentials = stravaAuthManager.getCredentialsForGlass()
        if (credentials != null && bluetoothService.isConnected()) {
            val success = bluetoothService.sendStravaCredentials(credentials)
            if (success) {
                Toast.makeText(this, "Strava credentials sent to Glass", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to send credentials. Connect to Glass first.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StravaScreen(
    authManager: StravaAuthManager,
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
                    imageVector = Icons.Filled.DirectionsBike,
                    contentDescription = "Strava",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Strava Integration",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = if (authState.isAuthenticated) 
                        "Connected as ${authState.athleteName}"
                    else 
                        "Connect your Strava account",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        // Authentication Status
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (authState.isAuthenticated) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = "Status",
                        tint = if (authState.isAuthenticated) Color.Green else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = if (authState.isAuthenticated) "Authenticated" else "Not Connected",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (authState.athleteId != null) {
                            Text(
                                text = "ID: ${authState.athleteId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                if (!authState.isAuthenticated) {
                    Button(
                        onClick = onSignIn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFC4C02) // Strava orange
                        )
                    ) {
                        Text("Sign In")
                    }
                } else {
                    IconButton(
                        onClick = { authManager.signOut() }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Logout,
                            contentDescription = "Sign Out"
                        )
                    }
                }
            }
        }
        
        // Actions
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
                            val qrBitmap = generateQRCode(credentials)
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
            
            // Features Card
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
                    
                    FeatureRow(Icons.Filled.DirectionsRun, "Activity Tracking", true)
                    FeatureRow(Icons.Filled.Timeline, "Live Segments", true)
                    FeatureRow(Icons.Filled.Navigation, "Route Navigation", true)
                    FeatureRow(Icons.Filled.Speed, "Performance Metrics", true)
                    FeatureRow(Icons.Filled.CloudUpload, "Auto Sync", true)
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
fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, enabled: Boolean) {
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

fun generateQRCode(credentials: StravaAuthManager.StravaCredentials): Bitmap? {
    try {
        val writer = QRCodeWriter()
        val json = JSONObject().apply {
            put("at", credentials.accessToken)
            put("rt", credentials.refreshToken)
            put("ea", credentials.expiresAt)
            put("id", credentials.athleteId)
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