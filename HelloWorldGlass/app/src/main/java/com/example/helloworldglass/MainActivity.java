package com.example.helloworldglass;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.media.AudioManager;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.widget.CardBuilder;

/**
 * Hello World application for Google Glass
 * Displays "Hello World" message and handles touchpad gestures
 */
public class MainActivity extends Activity {
    
    private GestureDetector mGestureDetector;
    private AudioManager mAudioManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize audio manager for sound feedback
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        // Create and configure gesture detector
        mGestureDetector = createGestureDetector();
        
        // Create a simple card with "Hello World" message
        View card = new CardBuilder(this, CardBuilder.Layout.TEXT)
            .setText("Hello World")
            .setFootnote("Swipe down to exit")
            .getView();
        
        // Set the card as the content view
        setContentView(card);
        
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    /**
     * Create and configure the gesture detector
     */
    private GestureDetector createGestureDetector() {
        GestureDetector gestureDetector = new GestureDetector(this);
        
        // Set listener for gesture events
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                switch (gesture) {
                    case TAP:
                        // Play tap sound when user taps
                        mAudioManager.playSoundEffect(Sounds.TAP);
                        return true;
                        
                    case SWIPE_DOWN:
                        // Play dismissed sound and exit when swiping down
                        mAudioManager.playSoundEffect(Sounds.DISMISSED);
                        finish();
                        return true;
                        
                    case SWIPE_LEFT:
                    case SWIPE_RIGHT:
                        // Play navigation sound for left/right swipes
                        mAudioManager.playSoundEffect(Sounds.TAP);
                        return true;
                        
                    default:
                        return false;
                }
            }
        });
        
        return gestureDetector;
    }
    
    /**
     * Send touch events to the gesture detector
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return super.onGenericMotionEvent(event);
    }
    
    /**
     * Handle key events (alternative way to handle swipe down)
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Play dismissed sound and exit
            mAudioManager.playSoundEffect(Sounds.DISMISSED);
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}