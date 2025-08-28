package com.example.glasspomodoro;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Glass Pomodoro Timer - A productivity timer app for Google Glass
 * Uses the Pomodoro Technique: 25 minutes work, 5 minutes break
 */
public class MainActivity extends Activity {
    
    // Timer constants (in milliseconds)
    private static final long WORK_DURATION = 25 * 60 * 1000; // 25 minutes
    private static final long SHORT_BREAK_DURATION = 5 * 60 * 1000; // 5 minutes
    private static final long LONG_BREAK_DURATION = 15 * 60 * 1000; // 15 minutes
    private static final int POMODOROS_BEFORE_LONG_BREAK = 4;
    
    // UI components
    private CardScrollView mCardScroller;
    private PomodoroCardAdapter mAdapter;
    private GestureDetector mGestureDetector;
    private AudioManager mAudioManager;
    private Vibrator mVibrator;
    private PowerManager.WakeLock mWakeLock;
    
    // Timer state
    private CountDownTimer mCurrentTimer;
    private TimerState mCurrentState = TimerState.READY;
    private long mTimeRemaining = WORK_DURATION;
    private int mCompletedPomodoros = 0;
    private int mTotalPomodoros = 0;
    private boolean mIsPaused = false;
    
    // Timer states
    private enum TimerState {
        READY,
        WORKING,
        SHORT_BREAK,
        LONG_BREAK,
        PAUSED
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize services
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        // Keep screen on during timer
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, 
                "GlassPomodoro:WakeLock");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Setup card scroller
        mCardScroller = new CardScrollView(this);
        mAdapter = new PomodoroCardAdapter();
        mCardScroller.setAdapter(mAdapter);
        mCardScroller.activate();
        setContentView(mCardScroller);
        
        // Create gesture detector
        mGestureDetector = createGestureDetector(this);
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
                    case TWO_TAP:
                        resetTimer();
                        return true;
                    case SWIPE_RIGHT:
                        skipCurrentSession();
                        return true;
                    case SWIPE_LEFT:
                        showStatistics();
                        return true;
                    case SWIPE_DOWN:
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
        switch (mCurrentState) {
            case READY:
                startWorkSession();
                break;
            case WORKING:
            case SHORT_BREAK:
            case LONG_BREAK:
                if (mIsPaused) {
                    resumeTimer();
                } else {
                    pauseTimer();
                }
                break;
            case PAUSED:
                resumeTimer();
                break;
        }
        mAudioManager.playSoundEffect(Sounds.TAP);
    }
    
    private void startWorkSession() {
        mCurrentState = TimerState.WORKING;
        mTimeRemaining = WORK_DURATION;
        startTimer(WORK_DURATION);
        mWakeLock.acquire();
        updateCard();
    }
    
    private void startBreak(boolean isLongBreak) {
        if (isLongBreak) {
            mCurrentState = TimerState.LONG_BREAK;
            mTimeRemaining = LONG_BREAK_DURATION;
            startTimer(LONG_BREAK_DURATION);
        } else {
            mCurrentState = TimerState.SHORT_BREAK;
            mTimeRemaining = SHORT_BREAK_DURATION;
            startTimer(SHORT_BREAK_DURATION);
        }
        updateCard();
    }
    
    private void startTimer(long duration) {
        if (mCurrentTimer != null) {
            mCurrentTimer.cancel();
        }
        
        mIsPaused = false;
        mCurrentTimer = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                mTimeRemaining = millisUntilFinished;
                updateCard();
            }
            
            @Override
            public void onFinish() {
                onTimerComplete();
            }
        }.start();
    }
    
    private void pauseTimer() {
        if (mCurrentTimer != null) {
            mCurrentTimer.cancel();
            mIsPaused = true;
            TimerState previousState = mCurrentState;
            mCurrentState = TimerState.PAUSED;
            updateCard();
            mCurrentState = previousState; // Store the previous state
        }
    }
    
    private void resumeTimer() {
        if (mIsPaused && mTimeRemaining > 0) {
            mIsPaused = false;
            startTimer(mTimeRemaining);
            updateCard();
        }
    }
    
    private void onTimerComplete() {
        // Play sound and vibrate
        mAudioManager.playSoundEffect(Sounds.SUCCESS);
        long[] pattern = {0, 200, 100, 200, 100, 200};
        mVibrator.vibrate(pattern, -1);
        
        switch (mCurrentState) {
            case WORKING:
                mCompletedPomodoros++;
                mTotalPomodoros++;
                
                // Check if it's time for a long break
                if (mCompletedPomodoros % POMODOROS_BEFORE_LONG_BREAK == 0) {
                    startBreak(true);
                } else {
                    startBreak(false);
                }
                break;
                
            case SHORT_BREAK:
            case LONG_BREAK:
                // After break, ready to start new work session
                mCurrentState = TimerState.READY;
                mTimeRemaining = WORK_DURATION;
                updateCard();
                break;
        }
        
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }
    
    private void skipCurrentSession() {
        if (mCurrentTimer != null) {
            mCurrentTimer.cancel();
        }
        
        switch (mCurrentState) {
            case WORKING:
                // Skip to break without counting as completed
                startBreak(false);
                break;
            case SHORT_BREAK:
            case LONG_BREAK:
                // Skip break and go back to ready
                mCurrentState = TimerState.READY;
                mTimeRemaining = WORK_DURATION;
                updateCard();
                break;
        }
        
        mAudioManager.playSoundEffect(Sounds.DISMISSED);
    }
    
    private void resetTimer() {
        if (mCurrentTimer != null) {
            mCurrentTimer.cancel();
        }
        
        mCurrentState = TimerState.READY;
        mTimeRemaining = WORK_DURATION;
        mCompletedPomodoros = 0;
        mIsPaused = false;
        
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        
        updateCard();
        mAudioManager.playSoundEffect(Sounds.DISMISSED);
    }
    
    private void showStatistics() {
        // This would show a statistics card, but for now just play a sound
        mAudioManager.playSoundEffect(Sounds.TAP);
    }
    
    private void updateCard() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
    }
    
    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    private class PomodoroCardAdapter extends CardScrollAdapter {
        @Override
        public int getCount() {
            return 1;
        }
        
        @Override
        public Object getItem(int position) {
            return createCard();
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return createCard();
        }
        
        @Override
        public int getPosition(Object item) {
            return 0;
        }
        
        private View createCard() {
            CardBuilder card = new CardBuilder(MainActivity.this, CardBuilder.Layout.TEXT);
            
            String title = "";
            String text = formatTime(mTimeRemaining);
            String footnote = "";
            
            switch (mCurrentState) {
                case READY:
                    title = "Ready to Start";
                    text = "Tap to begin work session\n" + formatTime(WORK_DURATION);
                    footnote = "Pomodoros: " + mCompletedPomodoros;
                    break;
                case WORKING:
                    title = "Working";
                    footnote = "Tap to pause • Swipe right to skip";
                    if (mIsPaused) {
                        title = "Working (Paused)";
                        footnote = "Tap to resume";
                    }
                    break;
                case SHORT_BREAK:
                    title = "Short Break";
                    footnote = "Relax! Tap to pause";
                    if (mIsPaused) {
                        title = "Break (Paused)";
                        footnote = "Tap to resume";
                    }
                    break;
                case LONG_BREAK:
                    title = "Long Break";
                    footnote = "Great work! Take a longer break";
                    if (mIsPaused) {
                        title = "Long Break (Paused)";
                        footnote = "Tap to resume";
                    }
                    break;
                case PAUSED:
                    title = "Paused";
                    footnote = "Tap to resume • Double tap to reset";
                    break;
            }
            
            // Add pomodoro progress indicator
            if (mCurrentState != TimerState.READY) {
                String progress = "";
                for (int i = 0; i < POMODOROS_BEFORE_LONG_BREAK; i++) {
                    if (i < (mCompletedPomodoros % POMODOROS_BEFORE_LONG_BREAK)) {
                        progress += "● ";
                    } else {
                        progress += "○ ";
                    }
                }
                text = text + "\n\n" + progress.trim();
            }
            
            card.setText(title + "\n\n" + text);
            card.setFootnote(footnote);
            
            return card.getView();
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
            if (mCurrentState != TimerState.READY) {
                resetTimer();
                return true;
            }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (mCurrentTimer != null) {
            mCurrentTimer.cancel();
        }
        
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        
        if (mCardScroller != null) {
            mCardScroller.deactivate();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (mCurrentState == TimerState.WORKING || 
            mCurrentState == TimerState.SHORT_BREAK || 
            mCurrentState == TimerState.LONG_BREAK) {
            pauseTimer();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (mCardScroller != null) {
            mCardScroller.activate();
        }
    }
}