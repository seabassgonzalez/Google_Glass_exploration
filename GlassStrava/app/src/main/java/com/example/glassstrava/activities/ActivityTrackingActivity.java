package com.example.glassstrava.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.view.WindowUtils;
import com.google.android.glass.widget.CardBuilder;

import com.example.glassstrava.services.ActivityTrackingService;

import java.util.Locale;

/**
 * Activity for tracking runs, rides, and other activities in real-time
 */
public class ActivityTrackingActivity extends Activity {
    
    private static final String TAG = "ActivityTracking";
    
    private GestureDetector mGestureDetector;
    private AudioManager mAudioManager;
    private PowerManager.WakeLock mWakeLock;
    private Handler mUpdateHandler;
    
    private ActivityTrackingService mTrackingService;
    private boolean mServiceBound = false;
    
    // Activity state
    private boolean mIsTracking = false;
    private boolean mIsPaused = false;
    private String mActivityType = "Run"; // Default to running
    
    // Metrics
    private long mStartTime = 0;
    private long mElapsedTime = 0;
    private double mDistance = 0.0;
    private double mCurrentSpeed = 0.0;
    private double mAverageSpeed = 0.0;
    private double mElevationGain = 0.0;
    private int mHeartRate = 0;
    private Location mLastLocation = null;
    
    // UI update runnable
    private Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsTracking && !mIsPaused) {
                updateMetrics();
                updateCard();
                mUpdateHandler.postDelayed(this, 1000); // Update every second
            }
        }
    };
    
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ActivityTrackingService.LocalBinder binder = 
                (ActivityTrackingService.LocalBinder) service;
            mTrackingService = binder.getService();
            mServiceBound = true;
            
            // Register for location updates
            mTrackingService.setLocationListener(new ActivityTrackingService.LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    handleLocationUpdate(location);
                }
            });
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Request voice commands
        getWindow().requestFeature(WindowUtils.FEATURE_VOICE_COMMANDS);
        
        // Initialize components
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mUpdateHandler = new Handler();
        
        // Keep screen on during tracking
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
            "GlassStrava:ActivityTracking");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Setup gesture detection
        mGestureDetector = createGestureDetector(this);
        
        // Show initial card
        updateCard();
        
        // Bind to tracking service
        Intent serviceIntent = new Intent(this, ActivityTrackingService.class);
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }
    
    private GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                switch (gesture) {
                    case TAP:
                        if (!mIsTracking) {
                            startTracking();
                        } else if (mIsPaused) {
                            resumeTracking();
                        } else {
                            pauseTracking();
                        }
                        return true;
                    case TWO_TAP:
                        if (mIsTracking) {
                            openOptionsMenu();
                        }
                        return true;
                    case SWIPE_DOWN:
                        if (!mIsTracking) {
                            finish();
                        } else {
                            openOptionsMenu();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
        
        return gestureDetector;
    }
    
    private void startTracking() {
        if (!mServiceBound) return;
        
        mIsTracking = true;
        mIsPaused = false;
        mStartTime = System.currentTimeMillis();
        mDistance = 0.0;
        mElevationGain = 0.0;
        
        // Start location tracking
        mTrackingService.startTracking(mActivityType);
        
        // Acquire wake lock
        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
        
        // Start UI updates
        mUpdateHandler.post(mUpdateRunnable);
        
        // Play sound
        mAudioManager.playSoundEffect(Sounds.SUCCESS);
        
        updateCard();
    }
    
    private void pauseTracking() {
        mIsPaused = true;
        if (mServiceBound) {
            mTrackingService.pauseTracking();
        }
        mAudioManager.playSoundEffect(Sounds.TAP);
        updateCard();
    }
    
    private void resumeTracking() {
        mIsPaused = false;
        if (mServiceBound) {
            mTrackingService.resumeTracking();
        }
        mUpdateHandler.post(mUpdateRunnable);
        mAudioManager.playSoundEffect(Sounds.TAP);
        updateCard();
    }
    
    private void stopTracking() {
        mIsTracking = false;
        mIsPaused = false;
        
        if (mServiceBound) {
            mTrackingService.stopTracking();
            
            // Save activity to Strava
            mTrackingService.saveActivity();
        }
        
        // Release wake lock
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        
        // Stop UI updates
        mUpdateHandler.removeCallbacks(mUpdateRunnable);
        
        mAudioManager.playSoundEffect(Sounds.SUCCESS);
        
        // Show summary
        showSummaryCard();
    }
    
    private void handleLocationUpdate(Location location) {
        if (mLastLocation != null && !mIsPaused) {
            // Calculate distance
            float distanceDelta = mLastLocation.distanceTo(location);
            mDistance += distanceDelta / 1000.0; // Convert to km
            
            // Calculate elevation gain
            if (location.hasAltitude() && mLastLocation.hasAltitude()) {
                double elevationDelta = location.getAltitude() - mLastLocation.getAltitude();
                if (elevationDelta > 0) {
                    mElevationGain += elevationDelta;
                }
            }
        }
        
        // Update speed
        if (location.hasSpeed()) {
            mCurrentSpeed = location.getSpeed() * 3.6; // Convert m/s to km/h
        }
        
        mLastLocation = location;
    }
    
    private void updateMetrics() {
        if (!mIsPaused) {
            mElapsedTime = System.currentTimeMillis() - mStartTime;
            
            // Calculate average speed
            if (mElapsedTime > 0 && mDistance > 0) {
                double hours = mElapsedTime / (1000.0 * 60.0 * 60.0);
                mAverageSpeed = mDistance / hours;
            }
        }
    }
    
    private void updateCard() {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.TEXT);
        
        if (!mIsTracking) {
            card.setText("Ready to Start " + mActivityType);
            card.setFootnote("Tap to begin • Swipe down to exit");
        } else if (mIsPaused) {
            card.setText("Activity Paused");
            card.setFootnote("Tap to resume • Double tap for menu");
        } else {
            // Format elapsed time
            long seconds = mElapsedTime / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            String timeStr = String.format(Locale.US, "%02d:%02d:%02d",
                hours, minutes % 60, seconds % 60);
            
            // Format metrics
            String metricsText = String.format(Locale.US,
                "%s\n\n" +
                "Time: %s\n" +
                "Distance: %.2f km\n" +
                "Speed: %.1f km/h\n" +
                "Avg Speed: %.1f km/h\n" +
                "Elevation: +%.0f m",
                mActivityType,
                timeStr,
                mDistance,
                mCurrentSpeed,
                mAverageSpeed,
                mElevationGain
            );
            
            card.setText(metricsText);
            card.setFootnote("Tap to pause • Double tap for menu");
        }
        
        setContentView(card.getView());
    }
    
    private void showSummaryCard() {
        // Format elapsed time
        long seconds = mElapsedTime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        String timeStr = String.format(Locale.US, "%02d:%02d:%02d",
            hours, minutes % 60, seconds % 60);
        
        // Calculate pace (min/km)
        double paceMinutes = 0;
        if (mDistance > 0) {
            paceMinutes = (mElapsedTime / 1000.0 / 60.0) / mDistance;
        }
        int paceMins = (int) paceMinutes;
        int paceSecs = (int) ((paceMinutes - paceMins) * 60);
        
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.TEXT);
        String summaryText = String.format(Locale.US,
            "Activity Complete!\n\n" +
            "Type: %s\n" +
            "Time: %s\n" +
            "Distance: %.2f km\n" +
            "Pace: %d:%02d min/km\n" +
            "Avg Speed: %.1f km/h\n" +
            "Elevation: +%.0f m",
            mActivityType,
            timeStr,
            mDistance,
            paceMins, paceSecs,
            mAverageSpeed,
            mElevationGain
        );
        
        card.setText(summaryText);
        card.setFootnote("Activity saved to Strava");
        setContentView(card.getView());
    }
    
    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        if (featureId == WindowUtils.FEATURE_VOICE_COMMANDS) {
            getMenuInflater().inflate(com.example.glassstrava.R.menu.tracking_menu, menu);
            return true;
        }
        return super.onCreatePanelMenu(featureId, menu);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(com.example.glassstrava.R.menu.tracking_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case com.example.glassstrava.R.id.action_stop:
                stopTracking();
                return true;
            case com.example.glassstrava.R.id.action_change_type:
                changeActivityType();
                return true;
            case com.example.glassstrava.R.id.action_discard:
                discardActivity();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    private void changeActivityType() {
        // Cycle through activity types
        if (mActivityType.equals("Run")) {
            mActivityType = "Ride";
        } else if (mActivityType.equals("Ride")) {
            mActivityType = "Walk";
        } else {
            mActivityType = "Run";
        }
        
        if (mServiceBound && mIsTracking) {
            mTrackingService.setActivityType(mActivityType);
        }
        
        updateCard();
        mAudioManager.playSoundEffect(Sounds.TAP);
    }
    
    private void discardActivity() {
        mIsTracking = false;
        if (mServiceBound) {
            mTrackingService.stopTracking();
        }
        
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        
        mUpdateHandler.removeCallbacks(mUpdateRunnable);
        mAudioManager.playSoundEffect(Sounds.DISMISSED);
        finish();
    }
    
    @Override
    public boolean onGenericMotionEvent(android.view.MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return super.onGenericMotionEvent(event);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (mServiceBound) {
            unbindService(mServiceConnection);
            mServiceBound = false;
        }
        
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        
        mUpdateHandler.removeCallbacks(mUpdateRunnable);
    }
}