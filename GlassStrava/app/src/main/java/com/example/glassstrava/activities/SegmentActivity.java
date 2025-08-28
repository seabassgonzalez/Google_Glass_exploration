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
import android.view.View;
import android.view.ViewGroup;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;

import com.example.glassstrava.models.Segment;
import com.example.glassstrava.models.SegmentEffort;

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
 * Activity for viewing nearby Strava segments and leaderboards
 */
public class SegmentActivity extends Activity implements LocationListener {
    
    private static final String TAG = "SegmentActivity";
    private static final String STRAVA_API_BASE = "https://www.strava.com/api/v3";
    
    private CardScrollView mCardScroller;
    private SegmentCardAdapter mAdapter;
    private GestureDetector mGestureDetector;
    private AudioManager mAudioManager;
    private LocationManager mLocationManager;
    private SharedPreferences mPrefs;
    
    private List<Segment> mSegments = new ArrayList<>();
    private Location mCurrentLocation;
    private boolean mIsLoadingSegments = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mPrefs = getSharedPreferences("StravaGlass", MODE_PRIVATE);
        
        // Setup card scroller
        mCardScroller = new CardScrollView(this);
        mAdapter = new SegmentCardAdapter(this);
        mCardScroller.setAdapter(mAdapter);
        mCardScroller.activate();
        setContentView(mCardScroller);
        
        // Setup gesture detection
        mGestureDetector = createGestureDetector(this);
        
        // Start location updates
        startLocationUpdates();
        
        // Show loading card
        showLoadingCard();
    }
    
    private void startLocationUpdates() {
        try {
            mLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000,  // 5 seconds
                10,    // 10 meters
                this
            );
            
            // Get last known location
            Location lastLocation = mLocationManager.getLastKnownLocation(
                LocationManager.GPS_PROVIDER);
            if (lastLocation != null) {
                onLocationChanged(lastLocation);
            }
        } catch (SecurityException e) {
            showError("Location permission denied");
        }
    }
    
    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        
        if (!mIsLoadingSegments) {
            loadNearbySegments();
        }
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    
    @Override
    public void onProviderEnabled(String provider) {}
    
    @Override
    public void onProviderDisabled(String provider) {}
    
    private void loadNearbySegments() {
        if (mCurrentLocation == null) return;
        
        mIsLoadingSegments = true;
        
        new AsyncTask<Void, Void, List<Segment>>() {
            @Override
            protected List<Segment> doInBackground(Void... params) {
                List<Segment> segments = new ArrayList<>();
                
                try {
                    // Get access token
                    String accessToken = mPrefs.getString("strava_access_token", null);
                    if (accessToken == null) {
                        return segments;
                    }
                    
                    // Calculate bounds (roughly 5km radius)
                    double lat = mCurrentLocation.getLatitude();
                    double lng = mCurrentLocation.getLongitude();
                    double offset = 0.045; // ~5km in degrees
                    
                    String bounds = String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f",
                        lat - offset, lng - offset, lat + offset, lng + offset);
                    
                    // API call to explore segments
                    String urlStr = STRAVA_API_BASE + "/segments/explore"
                        + "?bounds=" + bounds
                        + "&activity_type=running";
                    
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
                        
                        // Parse response
                        JSONObject json = new JSONObject(response.toString());
                        JSONArray segmentsArray = json.getJSONArray("segments");
                        
                        for (int i = 0; i < segmentsArray.length(); i++) {
                            JSONObject segmentJson = segmentsArray.getJSONObject(i);
                            Segment segment = Segment.fromJson(segmentJson);
                            segments.add(segment);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                return segments;
            }
            
            @Override
            protected void onPostExecute(List<Segment> segments) {
                mIsLoadingSegments = false;
                mSegments = segments;
                
                if (segments.isEmpty()) {
                    showNoSegmentsCard();
                } else {
                    mAdapter.notifyDataSetChanged();
                    mAudioManager.playSoundEffect(Sounds.SUCCESS);
                }
            }
        }.execute();
    }
    
    private void loadSegmentLeaderboard(final Segment segment) {
        new AsyncTask<Void, Void, List<SegmentEffort>>() {
            @Override
            protected List<SegmentEffort> doInBackground(Void... params) {
                List<SegmentEffort> efforts = new ArrayList<>();
                
                try {
                    String accessToken = mPrefs.getString("strava_access_token", null);
                    if (accessToken == null) return efforts;
                    
                    // API call for segment leaderboard
                    String urlStr = STRAVA_API_BASE + "/segments/" + segment.id + "/leaderboard"
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
                        
                        JSONObject json = new JSONObject(response.toString());
                        JSONArray entries = json.getJSONArray("entries");
                        
                        for (int i = 0; i < entries.length(); i++) {
                            JSONObject entry = entries.getJSONObject(i);
                            SegmentEffort effort = SegmentEffort.fromJson(entry);
                            efforts.add(effort);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                return efforts;
            }
            
            @Override
            protected void onPostExecute(List<SegmentEffort> efforts) {
                segment.leaderboard = efforts;
                mAdapter.notifyDataSetChanged();
            }
        }.execute();
    }
    
    private GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                switch (gesture) {
                    case TAP:
                        int position = mCardScroller.getSelectedItemPosition();
                        if (position < mSegments.size()) {
                            Segment segment = mSegments.get(position);
                            if (segment.leaderboard == null) {
                                loadSegmentLeaderboard(segment);
                            }
                        }
                        mAudioManager.playSoundEffect(Sounds.TAP);
                        return true;
                    case SWIPE_DOWN:
                        mAudioManager.playSoundEffect(Sounds.DISMISSED);
                        finish();
                        return true;
                    default:
                        return false;
                }
            }
        });
        
        return gestureDetector;
    }
    
    private void showLoadingCard() {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.TEXT);
        card.setText("Loading Segments");
        card.setFootnote("Searching nearby...");
        setContentView(card.getView());
    }
    
    private void showNoSegmentsCard() {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.ALERT);
        card.setText("No Segments Found");
        card.setFootnote("No Strava segments nearby");
        card.setIcon(android.R.drawable.ic_dialog_info);
        setContentView(card.getView());
    }
    
    private void showError(String message) {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.ALERT);
        card.setText("Error");
        card.setFootnote(message);
        card.setIcon(android.R.drawable.ic_dialog_alert);
        setContentView(card.getView());
    }
    
    private class SegmentCardAdapter extends CardScrollAdapter {
        private Context mContext;
        
        public SegmentCardAdapter(Context context) {
            mContext = context;
        }
        
        @Override
        public int getCount() {
            return mSegments.size();
        }
        
        @Override
        public Object getItem(int position) {
            return mSegments.get(position);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Segment segment = mSegments.get(position);
            
            CardBuilder card = new CardBuilder(mContext, CardBuilder.Layout.TEXT);
            
            String text = segment.name + "\n\n";
            text += String.format(Locale.US, "Distance: %.1f km\n", segment.distance / 1000);
            text += String.format(Locale.US, "Elevation: %.0f m\n", segment.elevationHigh - segment.elevationLow);
            text += String.format(Locale.US, "Avg Grade: %.1f%%\n", segment.averageGrade);
            
            // Add leaderboard if loaded
            if (segment.leaderboard != null && !segment.leaderboard.isEmpty()) {
                text += "\nLeaderboard:\n";
                for (int i = 0; i < Math.min(3, segment.leaderboard.size()); i++) {
                    SegmentEffort effort = segment.leaderboard.get(i);
                    text += String.format("%d. %s - %s\n", 
                        effort.rank, effort.athleteName, formatTime(effort.elapsedTime));
                }
            }
            
            card.setText(text);
            card.setFootnote("Tap for leaderboard • Swipe for next");
            
            return card.getView();
        }
        
        @Override
        public int getPosition(Object item) {
            return mSegments.indexOf(item);
        }
        
        private String formatTime(int seconds) {
            int mins = seconds / 60;
            int secs = seconds % 60;
            return String.format(Locale.US, "%d:%02d", mins, secs);
        }
    }
    
    @Override
    public boolean onGenericMotionEvent(android.view.MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return super.onGenericMotionEvent(event);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (mCardScroller != null) {
            mCardScroller.activate();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (mCardScroller != null) {
            mCardScroller.deactivate();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLocationManager.removeUpdates(this);
    }
}