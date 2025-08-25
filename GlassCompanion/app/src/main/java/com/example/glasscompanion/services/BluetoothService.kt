package com.example.glasscompanion.services

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothService(val context: Context) {
    companion object {
        private const val TAG = "BluetoothService"
        // Standard Serial Port Profile UUID for Bluetooth communication
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val APP_NAME = "GlassCompanion"
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var connectedDevice: BluetoothDevice? = null
    
    private val _connectionState = MutableStateFlow<BluetoothDevice?>(null)
    val connectionState: StateFlow<BluetoothDevice?> = _connectionState
    
    private val _incomingMessages = MutableStateFlow<String>("")
    val incomingMessages: StateFlow<String> = _incomingMessages
    
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermission()) {
            Log.e(TAG, "Missing Bluetooth permission")
            return emptyList()
        }
        
        val pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
        // Filter for Glass devices (you might want to check for specific device names)
        return pairedDevices.filter { device ->
            device.name?.contains("Glass", ignoreCase = true) == true || 
            device.name?.contains("Google", ignoreCase = true) == true ||
            // Include all paired devices for now
            true
        }
    }
    
    suspend fun connectToDevice(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermission()) {
            Log.e(TAG, "Missing Bluetooth permission")
            return@withContext false
        }
        
        try {
            // Cancel any ongoing connection attempts
            disconnect()
            
            Log.d(TAG, "Attempting to connect to ${device.name} (${device.address})")
            
            // Create a socket to connect with the device
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            
            // Cancel discovery as it slows down connection
            bluetoothAdapter?.cancelDiscovery()
            
            // Connect to the device
            bluetoothSocket?.connect()
            
            // Get the input and output streams
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream
            
            connectedDevice = device
            _connectionState.value = device
            
            Log.d(TAG, "Successfully connected to ${device.name}")
            
            // Start listening for incoming data
            startListening()
            
            // Send initial handshake
            sendMessage(JSONObject().apply {
                put("type", "handshake")
                put("device", "Samsung S21 Ultra")
                put("version", "1.0")
            }.toString())
            
            return@withContext true
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed: ${e.message}")
            disconnect()
            return@withContext false
        }
    }
    
    fun disconnect() {
        try {
            bluetoothSocket?.close()
            inputStream?.close()
            outputStream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing connection: ${e.message}")
        } finally {
            bluetoothSocket = null
            inputStream = null
            outputStream = null
            connectedDevice = null
            _connectionState.value = null
        }
    }
    
    fun sendMessage(message: String): Boolean {
        return try {
            outputStream?.let { stream ->
                // Add newline as message delimiter
                val messageWithDelimiter = "$message\n"
                stream.write(messageWithDelimiter.toByteArray())
                stream.flush()
                Log.d(TAG, "Sent message: $message")
                true
            } ?: false
        } catch (e: IOException) {
            Log.e(TAG, "Error sending message: ${e.message}")
            disconnect()
            false
        }
    }
    
    fun sendLocation(location: Location): Boolean {
        val locationJson = JSONObject().apply {
            put("type", "location")
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("altitude", location.altitude)
            put("accuracy", location.accuracy)
            put("speed", location.speed)
            put("bearing", location.bearing)
            put("timestamp", location.time)
            put("provider", location.provider)
        }
        
        return sendMessage(locationJson.toString())
    }
    
    fun sendCommand(command: String, parameters: Map<String, Any> = emptyMap()): Boolean {
        val commandJson = JSONObject().apply {
            put("type", "command")
            put("command", command)
            parameters.forEach { (key, value) ->
                put(key, value)
            }
        }
        
        return sendMessage(commandJson.toString())
    }
    
    private fun startListening() {
        Thread {
            val buffer = ByteArray(1024)
            var bytes: Int
            
            while (bluetoothSocket?.isConnected == true) {
                try {
                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes)
                        Log.d(TAG, "Received message: $message")
                        _incomingMessages.value = message
                        handleIncomingMessage(message)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error reading from socket: ${e.message}")
                    break
                }
            }
            
            // Connection lost
            disconnect()
        }.start()
    }
    
    private fun handleIncomingMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "request" -> handleRequest(json)
                "response" -> handleResponse(json)
                "status" -> handleStatus(json)
                else -> Log.d(TAG, "Unknown message type: $message")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}")
        }
    }
    
    private fun handleRequest(json: JSONObject) {
        when (json.optString("request")) {
            "location" -> {
                // Glass is requesting current location
                // The location service will automatically send updates
            }
            "battery" -> {
                // Send phone battery status
                sendPhoneBatteryStatus()
            }
        }
    }
    
    private fun handleResponse(json: JSONObject) {
        // Handle responses from Glass
        Log.d(TAG, "Received response: ${json.toString()}")
    }
    
    private fun handleStatus(json: JSONObject) {
        // Handle status updates from Glass
        Log.d(TAG, "Glass status: ${json.optString("status")}")
    }
    
    private fun sendPhoneBatteryStatus() {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        val batteryJson = JSONObject().apply {
            put("type", "battery")
            put("level", batteryLevel)
            put("device", "phone")
        }
        
        sendMessage(batteryJson.toString())
    }
    
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun isConnected(): Boolean = bluetoothSocket?.isConnected == true
    
    fun getConnectedDeviceName(): String? {
        if (!hasBluetoothPermission()) return null
        return connectedDevice?.name
    }
}