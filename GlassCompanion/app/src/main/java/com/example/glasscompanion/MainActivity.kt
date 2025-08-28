package com.example.glasscompanion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.glasscompanion.activities.StravaActivity
import com.example.glasscompanion.services.BluetoothService
import com.example.glasscompanion.services.LocationService
import com.example.glasscompanion.services.StravaAuthManager
import com.example.glasscompanion.ui.theme.GlassCompanionTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothService: BluetoothService
    private lateinit var locationService: LocationService
    private lateinit var stravaAuthManager: StravaAuthManager
    
    private val requestBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            initializeBluetooth()
        } else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
        }
    }
    
    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            locationService.startLocationUpdates()
        } else {
            Toast.makeText(this, "Location permission required for GPS sharing", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        bluetoothService = BluetoothService(this)
        locationService = LocationService(this)
        stravaAuthManager = StravaAuthManager(this)
        
        checkAndRequestPermissions()
        
        setContent {
            GlassCompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompanionScreen(
                        bluetoothService = bluetoothService,
                        locationService = locationService,
                        stravaAuthManager = stravaAuthManager,
                        onRequestLocationPermission = { requestLocationPermissions() },
                        onOpenStravaActivity = { openStravaActivity() }
                    )
                }
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        
        val hasBluetoothPermissions = bluetoothPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasBluetoothPermissions) {
            requestBluetoothPermission.launch(bluetoothPermissions)
        } else {
            initializeBluetooth()
        }
    }
    
    private fun requestLocationPermissions() {
        val locationPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        val hasLocationPermissions = locationPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasLocationPermissions) {
            requestLocationPermission.launch(locationPermissions)
        } else {
            locationService.startLocationUpdates()
        }
    }
    
    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
        }
    }
    
    private fun openStravaActivity() {
        val intent = Intent(this, StravaActivity::class.java)
        startActivity(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bluetoothService.disconnect()
        locationService.stopLocationUpdates()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionScreen(
    bluetoothService: BluetoothService,
    locationService: LocationService,
    stravaAuthManager: StravaAuthManager,
    onRequestLocationPermission: () -> Unit,
    onOpenStravaActivity: () -> Unit
) {
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf("Location not available") }
    var isGpsSharing by remember { mutableStateOf(false) }
    val stravaState by stravaAuthManager.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        pairedDevices = bluetoothService.getPairedDevices()
        bluetoothService.connectionState.collect { device ->
            connectedDevice = device
        }
    }
    
    LaunchedEffect(Unit) {
        locationService.currentLocation.collect { location ->
            currentLocation = if (location != null) {
                "Lat: ${location.latitude}, Lon: ${location.longitude}"
            } else {
                "Location not available"
            }
            
            // Send location to Glass if connected and sharing
            if (connectedDevice != null && isGpsSharing && location != null) {
                bluetoothService.sendLocation(location)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Glass Companion",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Connection Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (connectedDevice != null) {
                    Text(
                        text = "Connected to: ${connectedDevice!!.name ?: "Unknown Device"}",
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Button(
                        onClick = { bluetoothService.disconnect() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Disconnect")
                    }
                } else {
                    Text(
                        text = "Not connected",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // GPS Sharing Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "GPS Sharing",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = currentLocation,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Share GPS with Glass")
                    Switch(
                        checked = isGpsSharing,
                        onCheckedChange = { enabled ->
                            isGpsSharing = enabled
                            if (enabled) {
                                onRequestLocationPermission()
                            } else {
                                locationService.stopLocationUpdates()
                            }
                        },
                        enabled = connectedDevice != null
                    )
                }
            }
        }
        
        // Strava Integration Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            onClick = onOpenStravaActivity
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Strava for Glass",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = if (stravaState.isAuthenticated) 
                                "Connected: ${stravaState.athleteName}" 
                            else "Sign in to enable Glass features",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Open Strava",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (stravaState.isAuthenticated && connectedDevice != null) {
                    Button(
                        onClick = {
                            stravaAuthManager.getCredentialsForGlass()?.let { credentials ->
                                bluetoothService.sendStravaCredentials(credentials)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Sync Strava to Glass")
                    }
                }
            }
        }
        
        // Paired Devices
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Paired Devices",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                isScanning = true
                                pairedDevices = bluetoothService.getPairedDevices()
                                isScanning = false
                            }
                        }
                    ) {
                        Text("Refresh")
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                if (pairedDevices.isEmpty()) {
                    Text(
                        text = "No paired devices found. Pair your Glass in Bluetooth settings first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn {
                        items(pairedDevices) { device ->
                            DeviceItem(
                                device = device,
                                isConnected = device == connectedDevice,
                                onConnect = {
                                    coroutineScope.launch {
                                        bluetoothService.connectToDevice(device)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Open Bluetooth Settings Button
        Button(
            onClick = {
                val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                bluetoothService.context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Open Bluetooth Settings")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceItem(
    device: BluetoothDevice,
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    Card(
        onClick = { if (!isConnected) onConnect() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isConnected) {
                Text(
                    text = "Connected",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}