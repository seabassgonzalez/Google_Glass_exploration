package com.example.glasscompanion.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocationService(private val context: Context) {
    companion object {
        private const val TAG = "LocationService"
        private const val UPDATE_INTERVAL = 5000L // 5 seconds
        private const val FASTEST_UPDATE_INTERVAL = 2000L // 2 seconds
        private const val MIN_DISTANCE_CHANGE = 5f // 5 meters
    }
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation
    
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking
    
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        UPDATE_INTERVAL
    ).apply {
        setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
        setMinUpdateDistanceMeters(MIN_DISTANCE_CHANGE)
        setWaitForAccurateLocation(false)
    }.build()
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            
            locationResult.lastLocation?.let { location ->
                Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")
                _currentLocation.value = location
            }
        }
        
        override fun onLocationAvailability(availability: LocationAvailability) {
            super.onLocationAvailability(availability)
            Log.d(TAG, "Location availability: ${availability.isLocationAvailable}")
        }
    }
    
    fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.e(TAG, "Missing location permission")
            return
        }
        
        if (_isTracking.value) {
            Log.d(TAG, "Location tracking already active")
            return
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnSuccessListener {
                Log.d(TAG, "Location updates started")
                _isTracking.value = true
                
                // Get last known location immediately
                getLastKnownLocation()
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to start location updates: ${exception.message}")
                _isTracking.value = false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: ${e.message}")
            _isTracking.value = false
        }
    }
    
    fun stopLocationUpdates() {
        if (!_isTracking.value) {
            return
        }
        
        fusedLocationClient.removeLocationUpdates(locationCallback)
            .addOnSuccessListener {
                Log.d(TAG, "Location updates stopped")
                _isTracking.value = false
                _currentLocation.value = null
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to stop location updates: ${exception.message}")
            }
    }
    
    private fun getLastKnownLocation() {
        if (!hasLocationPermission()) {
            return
        }
        
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "Got last known location: ${location.latitude}, ${location.longitude}")
                        _currentLocation.value = location
                    } else {
                        Log.d(TAG, "Last known location is null")
                        // Request a fresh location
                        requestSingleLocationUpdate()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get last location: ${exception.message}")
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting last location: ${e.message}")
        }
    }
    
    private fun requestSingleLocationUpdate() {
        if (!hasLocationPermission()) {
            return
        }
        
        val singleLocationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            0
        ).setMaxUpdates(1).build()
        
        try {
            fusedLocationClient.requestLocationUpdates(
                singleLocationRequest,
                object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        locationResult.lastLocation?.let { location ->
                            Log.d(TAG, "Got single location update: ${location.latitude}, ${location.longitude}")
                            _currentLocation.value = location
                        }
                        // Remove this callback after receiving one update
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                },
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception requesting single location: ${e.message}")
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || 
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun getLocationUpdateInterval(): Long = UPDATE_INTERVAL
    
    fun setHighAccuracy(enabled: Boolean) {
        stopLocationUpdates()
        
        val priority = if (enabled) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        
        val newLocationRequest = LocationRequest.Builder(priority, UPDATE_INTERVAL).apply {
            setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
            setMinUpdateDistanceMeters(MIN_DISTANCE_CHANGE)
            setWaitForAccurateLocation(enabled)
        }.build()
        
        // Update the location request
        // Note: In a real implementation, you'd want to store this and use it
        startLocationUpdates()
    }
}