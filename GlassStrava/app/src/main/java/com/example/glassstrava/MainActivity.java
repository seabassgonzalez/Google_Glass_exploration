package com.example.glassstrava;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;

import com.example.glassstrava.activities.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Main activity for Glass Strava app
 * Provides card-based navigation to all Strava features
 */
public class MainActivity extends Activity {
    
    private CardScrollView mCardScroller;
    private StravaCardAdapter mAdapter;
    private GestureDetector mGestureDetector;
    private AudioManager mAudioManager;
    private SharedPreferences mPrefs;
    
    // Feature cards
    private static final int CARD_AUTH = 0;
    private static final int CARD_TRACKING = 1;
    private static final int CARD_SEGMENTS = 2;
    private static final int CARD_NAVIGATION = 3;
    private static final int CARD_PERFORMANCE = 4;
    private static final int CARD_SYNC = 5;
    private static final int CARD_SETTINGS = 6;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize components
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mPrefs = getSharedPreferences("StravaGlass", MODE_PRIVATE);
        
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Setup card scroller
        mCardScroller = new CardScrollView(this);
        mAdapter = new StravaCardAdapter(this);
        mCardScroller.setAdapter(mAdapter);
        mCardScroller.activate();
        setContentView(mCardScroller);
        
        // Setup gesture detection
        mGestureDetector = createGestureDetector(this);
        
        // Check authentication status
        checkAuthenticationStatus();
    }
    
    private void checkAuthenticationStatus() {
        String accessToken = mPrefs.getString("strava_access_token", null);
        if (accessToken == null) {
            // Navigate to auth card if not authenticated
            mCardScroller.setSelection(CARD_AUTH);
        }
    }
    
    private GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                switch (gesture) {
                    case TAP:
                        handleTap();
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
    
    private void handleTap() {
        int position = mCardScroller.getSelectedItemPosition();
        mAudioManager.playSoundEffect(Sounds.TAP);
        
        Intent intent = null;
        switch (position) {
            case CARD_AUTH:
                intent = new Intent(this, StravaAuthActivity.class);
                break;
            case CARD_TRACKING:
                intent = new Intent(this, ActivityTrackingActivity.class);
                break;
            case CARD_SEGMENTS:
                intent = new Intent(this, SegmentActivity.class);
                break;
            case CARD_NAVIGATION:
                intent = new Intent(this, RouteNavigationActivity.class);
                break;
            case CARD_PERFORMANCE:
                intent = new Intent(this, PerformanceActivity.class);
                break;
            case CARD_SYNC:
                syncActivities();
                return;
            case CARD_SETTINGS:
                openSettings();
                return;
        }
        
        if (intent != null) {
            startActivity(intent);
        }
    }
    
    private void syncActivities() {
        // Start sync service
        Intent syncIntent = new Intent(this, StravaApiService.class);
        syncIntent.setAction("SYNC_ACTIVITIES");
        startService(syncIntent);
        
        mAudioManager.playSoundEffect(Sounds.SUCCESS);
    }
    
    private void openSettings() {
        // TODO: Implement settings activity
        mAudioManager.playSoundEffect(Sounds.TAP);
    }
    
    private class StravaCardAdapter extends CardScrollAdapter {
        private List<CardBuilder> mCards;
        private Context mContext;
        
        public StravaCardAdapter(Context context) {
            mContext = context;
            mCards = new ArrayList<>();
            createCards();
        }
        
        private void createCards() {
            boolean isAuthenticated = mPrefs.getString("strava_access_token", null) != null;
            
            // Authentication card
            CardBuilder authCard = new CardBuilder(mContext, CardBuilder.Layout.MENU);
            authCard.setText(isAuthenticated ? "Connected to Strava" : "Connect to Strava");
            authCard.setFootnote(isAuthenticated ? "Tap to re-authenticate" : "Tap to sign in");
            authCard.setIcon(isAuthenticated ? 
                android.R.drawable.presence_online : 
                android.R.drawable.presence_offline);
            mCards.add(authCard);
            
            // Activity tracking card
            CardBuilder trackingCard = new CardBuilder(mContext, CardBuilder.Layout.MENU);
            trackingCard.setText("Track Activity");
            trackingCard.setFootnote("Start recording run or ride");
            trackingCard.setIcon(android.R.drawable.ic_menu_mylocation);
            mCards.add(trackingCard);
            
            // Segments card
            CardBuilder segmentsCard = new CardBuilder(mContext, CardBuilder.Layout.MENU);
            segmentsCard.setText("Live Segments");
            segmentsCard.setFootnote("View nearby segments & KOMs");
            segmentsCard.setIcon(android.R.drawable.ic_menu_sort_by_size);
            mCards.add(segmentsCard);
            
            // Navigation card
            CardBuilder navCard = new CardBuilder(mContext, CardBuilder.Layout.MENU);
            navCard.setText("Route Navigation");
            navCard.setFootnote("Follow Strava routes");
            navCard.setIcon(android.R.drawable.ic_menu_directions);
            mCards.add(navCard);
            
            // Performance card
            CardBuilder perfCard = new CardBuilder(mContext, CardBuilder.Layout.MENU);
            perfCard.setText("Performance Metrics");
            perfCard.setFootnote("Real-time stats & feedback");
            perfCard.setIcon(android.R.drawable.ic_menu_info_details);
            mCards.add(perfCard);
            
            // Sync card
            CardBuilder syncCard = new CardBuilder(mContext, CardBuilder.Layout.MENU);
            syncCard.setText("Sync Activities");
            syncCard.setFootnote("Upload to Strava");
            syncCard.setIcon(android.R.drawable.ic_menu_upload);
            mCards.add(syncCard);
            
            // Settings card
            CardBuilder settingsCard = new CardBuilder(mContext, CardBuilder.Layout.MENU);
            settingsCard.setText("Settings");
            settingsCard.setFootnote("Configure app preferences");
            settingsCard.setIcon(android.R.drawable.ic_menu_preferences);
            mCards.add(settingsCard);
        }
        
        @Override
        public int getCount() {
            return mCards.size();
        }
        
        @Override
        public Object getItem(int position) {
            return mCards.get(position);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return mCards.get(position).getView(convertView, parent);
        }
        
        @Override
        public int getPosition(Object item) {
            return mCards.indexOf(item);
        }
        
        @Override
        public int getViewTypeCount() {
            return CardBuilder.getViewTypeCount();
        }
        
        @Override
        public int getItemViewType(int position) {
            return mCards.get(position).getItemViewType();
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mAudioManager.playSoundEffect(Sounds.DISMISSED);
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (mCardScroller != null) {
            mCardScroller.activate();
            // Refresh cards to update auth status
            mAdapter.notifyDataSetChanged();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (mCardScroller != null) {
            mCardScroller.deactivate();
        }
    }
}