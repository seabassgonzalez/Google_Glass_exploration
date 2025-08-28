# Glass Pomodoro Timer

A productivity timer application for Google Glass implementing the Pomodoro Technique using the Glass Card UI system.

## Features

### Timer Functionality
- **25-minute work sessions** - Standard Pomodoro duration for focused work
- **5-minute short breaks** - Quick recovery between work sessions
- **15-minute long breaks** - Extended rest after 4 completed Pomodoros
- **Visual countdown timer** - Large, easy-to-read time display on Glass Card
- **Progress tracking** - Visual indicators showing completed Pomodoros (●○○○)

### Glass Card UI Implementation
The app uses Google Glass's native Card UI system:
- **CardBuilder** - Creates clean, Glass-optimized timer display cards
- **CardScrollView** - Enables smooth navigation through timer states
- **Dynamic card updates** - Real-time timer updates without screen flicker

### Gesture Controls
Intuitive Glass touchpad gestures:
- **TAP** - Start/Pause/Resume timer
- **TWO_TAP** - Reset timer completely
- **SWIPE_RIGHT** - Skip current session
- **SWIPE_LEFT** - View statistics (placeholder)
- **SWIPE_DOWN** - Exit application

### User Experience
- **Audio feedback** - Glass system sounds for timer events
- **Haptic feedback** - Vibration patterns for timer completion
- **Wake lock** - Keeps screen on during active timer
- **Auto-pause** - Timer pauses when app goes to background
- **Voice activation** - Launch with "OK Glass, start pomodoro timer"

## Technical Implementation

### Core Components

```java
// Timer states managed through enum
private enum TimerState {
    READY,      // Ready to start new session
    WORKING,    // Active work session
    SHORT_BREAK,// 5-minute break
    LONG_BREAK, // 15-minute break
    PAUSED      // Timer paused
}

// Card UI adapter for Glass display
private class PomodoroCardAdapter extends CardScrollAdapter {
    // Creates formatted timer cards with:
    // - Current state title
    // - Time remaining display
    // - Progress indicators
    // - Gesture hints in footnote
}
```

### State Management
- Tracks completed Pomodoros for long break scheduling
- Maintains timer state across pause/resume cycles
- Preserves progress when app is backgrounded

### Glass-Specific Features
- **Immersive mode** - Full screen timer display
- **GestureDetector** - Native Glass gesture recognition
- **Glass sounds** - Uses system sound effects (TAP, SUCCESS, DISMISSED)
- **CardBuilder layouts** - Optimized for Glass display resolution

## Building and Installation

### Prerequisites
- Google Glass Explorer Edition or Enterprise Edition
- Android SDK 19 (KitKat)
- Glass Development Kit (GDK)

### Build Steps
```bash
# Navigate to project directory
cd GlassPomodoro

# Build the APK
./gradlew assembleDebug

# Install on Glass
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Voice Trigger Setup
The app registers for voice command:
```xml
<action android:name="com.google.android.glass.action.VOICE_TRIGGER" />
```
Say "OK Glass, start pomodoro timer" to launch.

## Usage Guide

1. **Starting a Session**
   - Launch app via voice or launcher
   - TAP to begin 25-minute work session

2. **During Work Session**
   - Timer counts down on main card
   - TAP to pause/resume
   - SWIPE RIGHT to skip to break

3. **Break Time**
   - Automatic transition after work completion
   - Short break (5 min) after sessions 1-3
   - Long break (15 min) after session 4

4. **Progress Tracking**
   - Bottom of card shows Pomodoro progress (●●○○)
   - Filled circles = completed, empty = remaining

## Architecture Decisions

### Why Card UI?
- Native Glass UI paradigm
- Minimal battery consumption
- Instant visual updates
- Familiar to Glass users

### Timer Implementation
- CountDownTimer for accuracy
- 1-second update intervals
- WakeLock for screen persistence
- Background pause handling

### State Persistence
- In-memory state management
- No database needed for simple timer
- Quick app launch time

## Future Enhancements
- Statistics tracking and daily summaries
- Custom timer durations
- Task labeling for Pomodoros
- Sync with phone companion app
- Calendar integration

## Permissions
```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.VIBRATE" />
```

## Glass Development Notes
- Compiled against API 19 (Glass runs Android 4.4 KitKat)
- Uses GDK for Glass-specific features
- Optimized for 640x360 display resolution
- Designed for touchpad navigation (no touch screen)