package com.example.glassstrava.activities;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.WindowManager;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.widget.CardBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Activity for turn-by-turn navigation of Strava routes
 */
public class RouteNavigationActivity extends Activity implements LocationListener {
    
    private static final String TAG = "RouteNavigation";
    private static final String STRAVA_API_BASE = "https://www.strava.com/api/v3";
    private static final float WAYPOINT_RADIUS = 20.0f; // meters
    
    private GestureDetector mGestureDetector;
    private AudioManager mAudioManager;
    private Vibrator mVibrator;
    private LocationManager mLocationManager;
    private SharedPreferences mPrefs;
    private Handler mUpdateHandler;
    
    // Route data
    private long mRouteId;
    private String mRouteName;
    private List<Location> mRoutePoints = new ArrayList<>();
    private int mCurrentWaypointIndex = 0;
    private double mTotalDistance = 0.0;
    private double mRemainingDistance = 0.0;
    
    // Current location
    private Location mCurrentLocation;
    private float mBearing = 0.0f;
    private boolean mIsNavigating = false;
    
    // UI update runnable
    private Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsNavigating) {
                updateNavigationCard();
                mUpdateHandler.postDelayed(this, 1000);
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mPrefs = getSharedPreferences("StravaGlass", MODE_PRIVATE);
        mUpdateHandler = new Handler();
        
        // Setup gesture detection
        mGestureDetector = createGestureDetector(this);
        
        // Get route ID from intent or load default
        mRouteId = getIntent().getLongExtra("route_id", -1);
        
        if (mRouteId != -1) {
            loadRoute();
        } else {
            loadRoutesList();
        }
    }
    
    private void loadRoute() {
        showLoadingCard("Loading route...");
        
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    String accessToken = mPrefs.getString("strava_access_token", null);
                    if (accessToken == null) return false;
                    
                    // Get route details
                    String urlStr = STRAVA_API_BASE + "/routes/" + mRouteId;
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                    
                    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        
                        JSONObject json = new JSONObject(response.toString());
                        mRouteName = json.getString("name");
                        mTotalDistance = json.getDouble("distance");
                        
                        // Parse route polyline
                        if (json.has("map") && json.getJSONObject("map").has("polyline")) {
                            String polyline = json.getJSONObject("map").getString("polyline");
                            mRoutePoints = decodePolyline(polyline);
                            mRemainingDistance = mTotalDistance;
                            return true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
            
            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    startNavigation();
                } else {
                    showError("Failed to load route");
                }
            }
        }.execute();
    }
    
    private void loadRoutesList() {
        showLoadingCard("Loading routes...");
        
        new AsyncTask<Void, Void, List<JSONObject>>() {
            @Override
            protected List<JSONObject> doInBackground(Void... params) {
                List<JSONObject> routes = new ArrayList<>();
                
                try {
                    String accessToken = mPrefs.getString("strava_access_token", null);
                    if (accessToken == null) return routes;
                    
                    // Get athlete's routes
                    String athleteId = mPrefs.getString("strava_athlete_id", "");
                    String urlStr = STRAVA_API_BASE + "/athletes/" + athleteId + "/routes"
                        + "?per_page=10";
                    
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                    
                    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        
                        JSONArray jsonArray = new JSONArray(response.toString());
                        for (int i = 0; i < jsonArray.length(); i++) {
                            routes.add(jsonArray.getJSONObject(i));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                return routes;
            }
            
            @Override
            protected void onPostExecute(List<JSONObject> routes) {
                if (!routes.isEmpty()) {
                    // For demo, just load the first route
                    try {
                        mRouteId = routes.get(0).getLong("id");
                        loadRoute();
                    } catch (Exception e) {
                        showError("No routes available");
                    }
                } else {
                    showError("No routes found");
                }
            }
        }.execute();
    }
    
    private void startNavigation() {
        mIsNavigating = true;
        mCurrentWaypointIndex = 0;
        
        // Start location updates
        try {
            mLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000,  // 1 second
                2,     // 2 meters
                this
            );
        } catch (SecurityException e) {
            showError("Location permission denied");
            return;
        }
        
        // Start UI updates
        mUpdateHandler.post(mUpdateRunnable);
        
        mAudioManager.playSoundEffect(Sounds.SUCCESS);
        updateNavigationCard();
    }
    
    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        
        if (!mIsNavigating || mRoutePoints.isEmpty()) return;
        
        // Check if we've reached current waypoint
        if (mCurrentWaypointIndex < mRoutePoints.size()) {
            Location waypoint = mRoutePoints.get(mCurrentWaypointIndex);
            float distance = location.distanceTo(waypoint);
            
            if (distance < WAYPOINT_RADIUS) {
                // Reached waypoint
                mCurrentWaypointIndex++;
                
                // Vibrate and play sound
                mVibrator.vibrate(200);
                mAudioManager.playSoundEffect(Sounds.TAP);
                
                if (mCurrentWaypointIndex >= mRoutePoints.size()) {
                    // Route complete
                    completeNavigation();
                } else {
                    // Calculate turn direction for next waypoint
                    calculateNextTurn();
                }
            }
            
            // Update remaining distance
            updateRemainingDistance();
        }
    }
    
    private void calculateNextTurn() {
        if (mCurrentLocation == null || mCurrentWaypointIndex >= mRoutePoints.size()) return;
        
        Location nextWaypoint = mRoutePoints.get(mCurrentWaypointIndex);
        mBearing = mCurrentLocation.bearingTo(nextWaypoint);
        
        // Determine turn instruction
        if (mCurrentWaypointIndex > 0) {
            Location prevWaypoint = mRoutePoints.get(mCurrentWaypointIndex - 1);
            float prevBearing = prevWaypoint.bearingTo(nextWaypoint);
            float turnAngle = (mBearing - prevBearing + 360) % 360;
            
            if (turnAngle > 45 && turnAngle < 135) {
                // Right turn
                mVibrator.vibrate(new long[]{0, 100, 50, 100}, -1);
            } else if (turnAngle > 225 && turnAngle < 315) {
                // Left turn
                mVibrator.vibrate(new long[]{0, 200}, -1);
            }
        }
    }
    
    private void updateRemainingDistance() {
        if (mCurrentLocation == null || mRoutePoints.isEmpty()) return;
        
        double remaining = 0.0;
        
        // Distance to current waypoint
        if (mCurrentWaypointIndex < mRoutePoints.size()) {
            remaining += mCurrentLocation.distanceTo(mRoutePoints.get(mCurrentWaypointIndex));
        }
        
        // Distance between remaining waypoints
        for (int i = mCurrentWaypointIndex; i < mRoutePoints.size() - 1; i++) {
            remaining += mRoutePoints.get(i).distanceTo(mRoutePoints.get(i + 1));
        }
        
        mRemainingDistance = remaining;
    }
    
    private void updateNavigationCard() {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.TEXT);
        
        if (mCurrentLocation != null && mCurrentWaypointIndex < mRoutePoints.size()) {
            Location nextWaypoint = mRoutePoints.get(mCurrentWaypointIndex);
            float distance = mCurrentLocation.distanceTo(nextWaypoint);
            
            // Determine turn direction
            String direction = getDirectionString(mBearing);
            
            String text = String.format(Locale.US,
                "%s\n\n" +
                "%s in %.0f m\n\n" +
                "Remaining: %.1f km\n" +
                "Waypoint %d of %d",
                mRouteName,
                direction,
                distance,
                mRemainingDistance / 1000.0,
                mCurrentWaypointIndex + 1,
                mRoutePoints.size()
            );
            
            card.setText(text);
            card.setFootnote("Following route...");
        } else {
            card.setText(mRouteName);
            card.setFootnote("Waiting for GPS...");
        }
        
        setContentView(card.getView());
    }
    
    private String getDirectionString(float bearing) {
        // Normalize bearing to 0-360
        bearing = (bearing + 360) % 360;
        
        if (bearing < 22.5 || bearing >= 337.5) {
            return "Continue North";
        } else if (bearing < 67.5) {
            return "Turn Northeast";
        } else if (bearing < 112.5) {
            return "Turn East";
        } else if (bearing < 157.5) {
            return "Turn Southeast";
        } else if (bearing < 202.5) {
            return "Turn South";
        } else if (bearing < 247.5) {
            return "Turn Southwest";
        } else if (bearing < 292.5) {
            return "Turn West";
        } else {
            return "Turn Northwest";
        }
    }
    
    private void completeNavigation() {
        mIsNavigating = false;
        mLocationManager.removeUpdates(this);
        mUpdateHandler.removeCallbacks(mUpdateRunnable);
        
        // Play success sound and vibrate
        mAudioManager.playSoundEffect(Sounds.SUCCESS);
        mVibrator.vibrate(new long[]{0, 200, 100, 200, 100, 200}, -1);
        
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.ALERT);
        card.setText("Route Complete!");
        card.setFootnote(String.format(Locale.US, "Distance: %.1f km", mTotalDistance / 1000));
        card.setIcon(android.R.drawable.ic_dialog_info);
        setContentView(card.getView());
    }
    
    private List<Location> decodePolyline(String encoded) {
        List<Location> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;
            
            Location point = new Location("route");
            point.setLatitude((double) lat / 1E5);
            point.setLongitude((double) lng / 1E5);
            poly.add(point);
        }
        
        return poly;
    }
    
    private GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                switch (gesture) {
                    case TAP:
                        // Toggle pause/resume
                        mAudioManager.playSoundEffect(Sounds.TAP);
                        return true;
                    case SWIPE_DOWN:
                        stopNavigation();
                        return true;
                    default:
                        return false;
                }
            }
        });
        
        return gestureDetector;
    }
    
    private void stopNavigation() {
        mIsNavigating = false;
        mLocationManager.removeUpdates(this);
        mUpdateHandler.removeCallbacks(mUpdateRunnable);
        mAudioManager.playSoundEffect(Sounds.DISMISSED);
        finish();
    }
    
    private void showLoadingCard(String message) {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.TEXT);
        card.setText("Loading");
        card.setFootnote(message);
        setContentView(card.getView());
    }
    
    private void showError(String message) {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.ALERT);
        card.setText("Error");
        card.setFootnote(message);
        card.setIcon(android.R.drawable.ic_dialog_alert);
        setContentView(card.getView());
    }
    
    @Override
    public boolean onGenericMotionEvent(android.view.MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return super.onGenericMotionEvent(event);
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    
    @Override
    public void onProviderEnabled(String provider) {}
    
    @Override
    public void onProviderDisabled(String provider) {}
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLocationManager.removeUpdates(this);
        mUpdateHandler.removeCallbacks(mUpdateRunnable);
    }
}