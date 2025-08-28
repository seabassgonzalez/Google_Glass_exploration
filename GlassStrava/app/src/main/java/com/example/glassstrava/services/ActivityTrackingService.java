package com.example.glassstrava.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Service for tracking GPS location and activity metrics
 */
public class ActivityTrackingService extends Service implements LocationListener {
    private static final String TAG = "ActivityTrackingService";
    
    private final IBinder mBinder = new LocalBinder();
    private LocationManager mLocationManager;
    private LocationListener mExternalLocationListener;
    private SharedPreferences mPrefs;
    
    // Tracking state
    private boolean mIsTracking = false;
    private boolean mIsPaused = false;
    private String mActivityType = "Run";
    private long mStartTime = 0;
    private long mPausedDuration = 0;
    private long mPauseStartTime = 0;
    
    // Location tracking
    private List<Location> mLocationPoints = new ArrayList<>();
    private Location mLastLocation = null;
    private double mTotalDistance = 0.0;
    private double mElevationGain = 0.0;
    
    public interface LocationListener {
        void onLocationChanged(Location location);
    }
    
    public class LocalBinder extends Binder {
        public ActivityTrackingService getService() {
            return ActivityTrackingService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mPrefs = getSharedPreferences("StravaGlass", MODE_PRIVATE);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
    public void setLocationListener(LocationListener listener) {
        mExternalLocationListener = listener;
    }
    
    public void startTracking(String activityType) {
        if (mIsTracking) return;
        
        mIsTracking = true;
        mIsPaused = false;
        mActivityType = activityType;
        mStartTime = System.currentTimeMillis();
        mPausedDuration = 0;
        mTotalDistance = 0.0;
        mElevationGain = 0.0;
        mLocationPoints.clear();
        
        // Request location updates
        try {
            mLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000, // 1 second
                2,    // 2 meters
                this
            );
            
            // Also use network provider as backup
            mLocationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                2000, // 2 seconds
                5,    // 5 meters
                this
            );
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission denied", e);
        }
        
        Log.d(TAG, "Started tracking " + activityType);
    }
    
    public void pauseTracking() {
        mIsPaused = true;
        mPauseStartTime = System.currentTimeMillis();
    }
    
    public void resumeTracking() {
        if (mIsPaused && mPauseStartTime > 0) {
            mPausedDuration += System.currentTimeMillis() - mPauseStartTime;
        }
        mIsPaused = false;
        mPauseStartTime = 0;
    }
    
    public void stopTracking() {
        mIsTracking = false;
        mLocationManager.removeUpdates(this);
        
        if (mIsPaused && mPauseStartTime > 0) {
            mPausedDuration += System.currentTimeMillis() - mPauseStartTime;
        }
        
        Log.d(TAG, "Stopped tracking. Points: " + mLocationPoints.size() + 
                   ", Distance: " + mTotalDistance);
    }
    
    public void setActivityType(String type) {
        mActivityType = type;
    }
    
    @Override
    public void onLocationChanged(Location location) {
        if (!mIsTracking || mIsPaused) return;
        
        // Add to points list
        mLocationPoints.add(location);
        
        // Calculate metrics
        if (mLastLocation != null) {
            float distance = mLastLocation.distanceTo(location);
            mTotalDistance += distance;
            
            // Calculate elevation gain
            if (location.hasAltitude() && mLastLocation.hasAltitude()) {
                double elevationDelta = location.getAltitude() - mLastLocation.getAltitude();
                if (elevationDelta > 0) {
                    mElevationGain += elevationDelta;
                }
            }
        }
        
        mLastLocation = location;
        
        // Notify external listener
        if (mExternalLocationListener != null) {
            mExternalLocationListener.onLocationChanged(location);
        }
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    
    @Override
    public void onProviderEnabled(String provider) {}
    
    @Override
    public void onProviderDisabled(String provider) {}
    
    public void saveActivity() {
        if (mLocationPoints.isEmpty()) {
            Log.w(TAG, "No location points to save");
            return;
        }
        
        // Calculate elapsed time (excluding pauses)
        long elapsedTime = System.currentTimeMillis() - mStartTime - mPausedDuration;
        
        try {
            // Create activity JSON
            JSONObject activity = new JSONObject();
            activity.put("name", mActivityType + " on Glass");
            activity.put("type", mActivityType);
            activity.put("start_date_local", 
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .format(new Date(mStartTime)));
            activity.put("elapsed_time", elapsedTime / 1000); // Convert to seconds
            activity.put("distance", mTotalDistance);
            activity.put("total_elevation_gain", mElevationGain);
            activity.put("device_name", "Google Glass");
            activity.put("description", "Recorded with Glass Strava app");
            
            // Add GPS track if available
            if (!mLocationPoints.isEmpty()) {
                JSONArray latlng = new JSONArray();
                JSONArray time = new JSONArray();
                JSONArray altitude = new JSONArray();
                
                for (int i = 0; i < mLocationPoints.size(); i++) {
                    Location loc = mLocationPoints.get(i);
                    
                    // Latitude, Longitude array
                    JSONArray point = new JSONArray();
                    point.put(loc.getLatitude());
                    point.put(loc.getLongitude());
                    latlng.put(point);
                    
                    // Time from start in seconds
                    time.put((loc.getTime() - mStartTime) / 1000);
                    
                    // Altitude if available
                    if (loc.hasAltitude()) {
                        altitude.put(loc.getAltitude());
                    }
                }
                
                activity.put("latlng", latlng);
                activity.put("time", time);
                if (altitude.length() > 0) {
                    activity.put("altitude", altitude);
                }
            }
            
            // Queue for upload via StravaApiService
            Intent uploadIntent = new Intent(this, StravaApiService.class);
            uploadIntent.setAction("UPLOAD_ACTIVITY");
            uploadIntent.putExtra("activity_json", activity.toString());
            startService(uploadIntent);
            
            Log.d(TAG, "Activity queued for upload to Strava");
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating activity JSON", e);
        }
    }
    
    // Getters for current metrics
    public double getTotalDistance() {
        return mTotalDistance;
    }
    
    public double getElevationGain() {
        return mElevationGain;
    }
    
    public int getLocationPointCount() {
        return mLocationPoints.size();
    }
    
    public boolean isTracking() {
        return mIsTracking;
    }
    
    public boolean isPaused() {
        return mIsPaused;
    }
}