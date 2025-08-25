# Google Glass Explorer Edition Development Guide

## Overview
Google Glass Explorer Edition uses Android-based APIs through the Glass Development Kit (GDK). While the SDK is now deprecated, this guide provides comprehensive documentation for understanding Glass application architecture and development.

## Development Approaches

### 1. GDK (Glass Development Kit)
- Native Android applications running directly on Glass
- Full access to device hardware and sensors
- Best for real-time, interactive experiences
- Uses Android SDK with Glass-specific APIs

### 2. Mirror API
- Cloud-based web services interacting with Glass
- No code running directly on device
- Best for periodic updates and notifications
- Supports multiple programming languages

## Design Principles

### Core Philosophy
1. **Design for Glass** - Create unique experiences, not smartphone ports
2. **Don't Get in the Way** - Supplement life without interrupting it
3. **Keep it Relevant** - Deliver contextual information at the right time
4. **Avoid the Unexpected** - Be predictable and respectful
5. **Build for People** - Use natural interfaces and interactions

## UI Components

### Card Types

#### Static Cards
- Display text, HTML, images, and video
- Fixed content that doesn't update frequently
- Created using CardBuilder

#### Live Cards
- Real-time, frequently updated content
- Appear in timeline's "present" section
- Two rendering methods:
  - **Low-frequency**: Updates every few seconds using RemoteViews
  - **High-frequency**: Complex graphics, 2D/3D rendering

#### Immersions
- Full-screen Android activities
- Complete control over user experience
- Use sparingly - only when necessary
- Built with standard Android layouts

### CardBuilder Layouts
1. **TEXT** - Full-bleed text with optional background
2. **COLUMNS** - Image/icon left, text right
3. **CAPTION** - Image background with bottom text
4. **TITLE** - Centered title over image
5. **AUTHOR** - Message with author avatar
6. **MENU** - Centered icon and title
7. **ALERT** - Large centered icon with message
8. **EMBED_INSIDE** - Custom layout within Glass template

## Interaction Patterns

### Voice Commands

#### Main Voice Triggers
```xml
<!-- res/xml/voice_trigger.xml -->
<trigger keyword="@string/glass_voice_trigger">
    <input prompt="@string/glass_voice_prompt" />
</trigger>
```

```xml
<!-- AndroidManifest.xml -->
<intent-filter>
    <action android:name="com.google.android.glass.action.VOICE_TRIGGER" />
</intent-filter>
<meta-data
    android:name="com.google.android.glass.VoiceTrigger"
    android:resource="@xml/voice_trigger" />
```

#### Contextual Voice Commands
```java
// Enable voice commands in activity
@Override
public boolean onCreatePanelMenu(int featureId, Menu menu) {
    if (featureId == WindowUtils.FEATURE_VOICE_COMMANDS) {
        getMenuInflater().inflate(R.menu.voice_menu, menu);
        return true;
    }
    return super.onCreatePanelMenu(featureId, menu);
}
```

#### Speech Recognition
```java
private static final int SPEECH_REQUEST = 0;

private void displaySpeechRecognizer() {
    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    startActivityForResult(intent, SPEECH_REQUEST);
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK) {
        List<String> results = data.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS);
        String spokenText = results.get(0);
        // Process the spoken text
    }
    super.onActivityResult(requestCode, resultCode, data);
}
```

### Touch Gestures

#### Basic Gesture Detection
```java
@Override
public boolean onKeyDown(int keycode, KeyEvent event) {
    switch (keycode) {
        case KeyEvent.KEYCODE_DPAD_CENTER:
            // Handle tap
            return true;
        case KeyEvent.KEYCODE_BACK:
            // Handle swipe down
            finish();
            return true;
        case KeyEvent.KEYCODE_CAMERA:
            // Handle camera button
            return true;
    }
    return super.onKeyDown(keycode, event);
}
```

#### Advanced Gesture Detection
```java
private GestureDetector mGestureDetector;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mGestureDetector = createGestureDetector(this);
}

private GestureDetector createGestureDetector(Context context) {
    GestureDetector gestureDetector = new GestureDetector(context);
    gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
        @Override
        public boolean onGesture(Gesture gesture) {
            if (gesture == Gesture.TAP) {
                // Handle tap
                return true;
            } else if (gesture == Gesture.TWO_TAP) {
                // Handle two finger tap
                return true;
            }
            return false;
        }
    });
    return gestureDetector;
}

@Override
public boolean onGenericMotionEvent(MotionEvent event) {
    if (mGestureDetector != null) {
        return mGestureDetector.onMotionEvent(event);
    }
    return false;
}
```

## Hardware Integration

### Camera

#### Taking Pictures
```java
private static final int TAKE_PICTURE_REQUEST = 1;

private void takePicture() {
    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    startActivityForResult(intent, TAKE_PICTURE_REQUEST);
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == TAKE_PICTURE_REQUEST && resultCode == RESULT_OK) {
        String thumbnailPath = data.getStringExtra(
                Intents.EXTRA_THUMBNAIL_FILE_PATH);
        String picturePath = data.getStringExtra(
                Intents.EXTRA_PICTURE_FILE_PATH);
        
        processPictureWhenReady(picturePath);
    }
    super.onActivityResult(requestCode, resultCode, data);
}

private void processPictureWhenReady(final String picturePath) {
    final File pictureFile = new File(picturePath);
    
    if (pictureFile.exists()) {
        // Picture is ready
    } else {
        // Use FileObserver to wait for picture to be written
        final File parentDirectory = pictureFile.getParentFile();
        FileObserver observer = new FileObserver(parentDirectory.getPath(),
                FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
            @Override
            public void onEvent(int event, String path) {
                if (path != null && path.equals(pictureFile.getName())) {
                    // Picture is ready
                    stopWatching();
                }
            }
        };
        observer.startWatching();
    }
}
```

#### Recording Video
```java
private static final int CAPTURE_VIDEO_REQUEST = 2;

private void captureVideo() {
    Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
    startActivityForResult(intent, CAPTURE_VIDEO_REQUEST);
}
```

### Location & Sensors

#### Location Services
```java
private LocationManager mLocationManager;
private LocationListener mLocationListener;

private void startLocationUpdates() {
    mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // Handle location update
        }
        
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
        
        @Override
        public void onProviderEnabled(String provider) {}
        
        @Override
        public void onProviderDisabled(String provider) {}
    };
    
    // Request updates from all available providers
    List<String> providers = mLocationManager.getProviders(true);
    for (String provider : providers) {
        mLocationManager.requestLocationUpdates(provider, 
                MIN_TIME, MIN_DISTANCE, mLocationListener);
    }
}
```

#### Sensor Access
```java
private SensorManager mSensorManager;
private Sensor mAccelerometer;
private SensorEventListener mSensorListener;

private void setupSensors() {
    mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    
    mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                // Process accelerometer data
            }
        }
        
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };
}

@Override
protected void onResume() {
    super.onResume();
    mSensorManager.registerListener(mSensorListener, mAccelerometer,
            SensorManager.SENSOR_DELAY_NORMAL);
}

@Override
protected void onPause() {
    super.onPause();
    mSensorManager.unregisterListener(mSensorListener);
}
```

### Available Sensors
- **Supported**: Accelerometer, Gravity, Gyroscope, Light, Linear Acceleration, Magnetic Field, Rotation Vector
- **Not Supported**: Temperature, Proximity, Pressure, Relative Humidity

## Live Card Implementation

### Low-Frequency Rendering
```java
public class LiveCardService extends Service {
    private static final String LIVE_CARD_TAG = "LiveCardDemo";
    private LiveCard mLiveCard;
    private RemoteViews mRemoteViews;
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mLiveCard == null) {
            mLiveCard = new LiveCard(this, LIVE_CARD_TAG);
            
            mRemoteViews = new RemoteViews(getPackageName(),
                    R.layout.live_card_layout);
            mLiveCard.setViews(mRemoteViews);
            
            // Set pending intent for menu
            Intent menuIntent = new Intent(this, MenuActivity.class);
            mLiveCard.setAction(PendingIntent.getActivity(this, 0,
                    menuIntent, 0));
            
            mLiveCard.publish(PublishMode.REVEAL);
        } else {
            mLiveCard.navigate();
        }
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        if (mLiveCard != null && mLiveCard.isPublished()) {
            mLiveCard.unpublish();
            mLiveCard = null;
        }
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
```

### High-Frequency Rendering
```java
public class LiveCardService extends Service {
    private LiveCard mLiveCard;
    private LiveCardRenderer mRenderer;
    
    private class LiveCardRenderer implements DirectRenderingCallback {
        private SurfaceHolder mHolder;
        private boolean mPaused;
        private RenderThread mRenderThread;
        
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format,
                int width, int height) {
            // Surface size or format changed
        }
        
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mHolder = holder;
            mPaused = false;
            mRenderThread = new RenderThread();
            mRenderThread.start();
        }
        
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mHolder = null;
            mPaused = true;
            mRenderThread.quit();
        }
        
        @Override
        public void renderingPaused(SurfaceHolder holder, boolean paused) {
            mPaused = paused;
        }
        
        private class RenderThread extends Thread {
            private boolean mShouldRun = true;
            
            @Override
            public void run() {
                while (mShouldRun && !mPaused) {
                    Canvas canvas = mHolder.lockCanvas();
                    if (canvas != null) {
                        // Draw on canvas
                        canvas.drawColor(Color.BLACK);
                        // Add your drawing code here
                        mHolder.unlockCanvasAndPost(canvas);
                    }
                    
                    SystemClock.sleep(33); // ~30 FPS
                }
            }
            
            public void quit() {
                mShouldRun = false;
            }
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mLiveCard == null) {
            mLiveCard = new LiveCard(this, "LiveCardDemo");
            mRenderer = new LiveCardRenderer();
            mLiveCard.setDirectRenderingEnabled(true)
                    .getSurfaceHolder().addCallback(mRenderer);
            
            Intent menuIntent = new Intent(this, MenuActivity.class);
            mLiveCard.setAction(PendingIntent.getActivity(this, 0,
                    menuIntent, 0));
            
            mLiveCard.publish(PublishMode.REVEAL);
        }
        
        return START_STICKY;
    }
}
```

## Immersion Implementation

### Basic Immersion Activity
```java
public class ImmersionActivity extends Activity {
    private GestureDetector mGestureDetector;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Remove title bar
        getWindow().requestFeature(WindowUtils.FEATURE_VOICE_COMMANDS);
        
        setContentView(R.layout.immersion_layout);
        
        mGestureDetector = createGestureDetector(this);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.immersion_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.stop:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return false;
    }
}
```

### AndroidManifest.xml Configuration
```xml
<activity
    android:name=".ImmersionActivity"
    android:immersive="true"
    android:theme="@android:style/Theme.DeviceDefault">
    <intent-filter>
        <action android:name="com.google.android.glass.action.VOICE_TRIGGER" />
    </intent-filter>
    <meta-data
        android:name="com.google.android.glass.VoiceTrigger"
        android:resource="@xml/voice_trigger" />
</activity>
```

## Style Guidelines

### Color Palette
- **White**: #ffffff
- **Gray**: #808080  
- **Blue**: #34a7ff
- **Red**: #cc3333
- **Green**: #99cc33
- **Yellow**: #ddbb11

### Typography
- **Primary Font**: Roboto Light (32px minimum)
- **Large Text**: Roboto Thin (64px+)
- **Dynamic Sizing**: Adjust based on content volume

### Layout Specifications
- **Screen Resolution**: 640 × 360 pixels
- **Padding**: 40px for text content
- **Images**: Full-bleed recommended
- **Icons**: 50 × 50 pixels for menus, 36 × 36 for attribution

## Development Setup

### Prerequisites
1. Android Studio
2. Android 4.4.2 (API 19) SDK
3. Glass Development Kit Preview
4. Glass device with USB debugging enabled

### Project Configuration
```xml
<!-- build.gradle -->
android {
    compileSdkVersion "Glass Development Kit Developer Preview"
    
    defaultConfig {
        minSdkVersion 19
        targetSdkVersion 19
    }
}
```

### Enable USB Debugging on Glass
1. Settings → Device Info → Turn on debug
2. Connect Glass via USB
3. Accept debugging authorization

## Permissions

### Minimal Permissions (Recommended)
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Sensitive Permissions (Require User Consent)
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

### Development Permission
```xml
<!-- For unlisted voice commands during development -->
<uses-permission android:name="com.google.android.glass.permission.DEVELOPMENT" />
```

## Best Practices

### Performance
- Use 50 Hz sampling rate for head motion tracking
- Process sensor events quickly (UI thread)
- Release resources when not in use
- Minimize battery consumption

### User Experience
- Follow "fire-and-forget" interaction model
- Provide clear, immediate feedback
- Always include "Stop" menu option for ongoing tasks
- Respect timeline navigation priority

### Code Organization
- Separate UI from business logic
- Use Services for background tasks
- Implement proper lifecycle management
- Handle configuration changes gracefully

## Common Patterns

### Ongoing Task Pattern
Used for long-running tasks like navigation or workout tracking:
1. Create Live Card with service
2. Update periodically with relevant information
3. Include "Stop" menu option
4. Clean up resources on completion

### Periodic Notification Pattern
For delivering timely updates:
1. Use background service
2. Insert Static Cards without user invocation
3. Respect user's attention and context
4. Avoid excessive notifications

### Immersive Experience Pattern
For focused, interactive experiences:
1. Use only when Live Cards insufficient
2. Override timeline gestures carefully
3. Provide clear exit mechanism
4. Design for Glass-specific interactions

## Troubleshooting

### Common Issues
1. **Voice commands not working**: Check DEVELOPMENT permission
2. **Camera not releasing**: Implement proper button handling
3. **Location not updating**: Request from all providers
4. **Sensors draining battery**: Unregister when not needed

### Debugging Tips
- Use `adb logcat` for debugging
- Test on actual Glass device when possible
- Monitor battery and performance impact
- Verify permissions in manifest

## Sample Application Structure

```
MyGlassApp/
├── AndroidManifest.xml
├── build.gradle
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/glass/
│       │       ├── MainActivity.java
│       │       ├── LiveCardService.java
│       │       ├── MenuActivity.java
│       │       └── ImmersionActivity.java
│       └── res/
│           ├── layout/
│           │   ├── card_layout.xml
│           │   ├── live_card_layout.xml
│           │   └── immersion_layout.xml
│           ├── menu/
│           │   └── main_menu.xml
│           ├── xml/
│           │   └── voice_trigger.xml
│           └── values/
│               └── strings.xml
```

## GDK API Reference

### Package Overview

The Glass Development Kit provides six main packages for building Glass applications:

#### com.google.android.glass.app
**Purpose**: General purpose hook-ins to Glass system and UI (Added in API level XE12)

**Key Classes:**
- **ContextualMenus**: Constants for contextual menu commands
  - Contains `ContextualMenus.Command` enum for menu operations
- **VoiceTriggers**: Register Glassware with main voice menu
  - Contains `VoiceTriggers.Command` enum for system voice commands

#### com.google.android.glass.content
**Purpose**: Platform intent actions and extras (Added in API level XE18.1)

**Intents Class Constants:**
```java
// Broadcast when user starts/stops wearing device
public static final String ACTION_ON_HEAD_STATE_CHANGED = 
    "com.google.android.glass.action.ON_HEAD_STATE_CHANGED";

// Boolean extra for wearing state
public static final String EXTRA_IS_ON_HEAD = "is_on_head";

// File path extras for media capture
public static final String EXTRA_PICTURE_FILE_PATH = "picture_file_path";
public static final String EXTRA_THUMBNAIL_FILE_PATH = "thumbnail_file_path";
public static final String EXTRA_VIDEO_FILE_PATH = "video_file_path";
```

**Usage Example:**
```java
// Check if user is wearing Glass
BroadcastReceiver headStateReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isOnHead = intent.getBooleanExtra(
            Intents.EXTRA_IS_ON_HEAD, false);
        if (isOnHead) {
            // User put on Glass
        } else {
            // User removed Glass
        }
    }
};

IntentFilter filter = new IntentFilter(Intents.ACTION_ON_HEAD_STATE_CHANGED);
registerReceiver(headStateReceiver, filter);
```

#### com.google.android.glass.media
**Purpose**: Capturing and playing media on Glass (Added in API level XE12)

**Sounds Class - Audio Feedback Constants:**
```java
public static final int DISALLOWED = 10;  // User tried disallowed action
public static final int DISMISSED = 15;   // User dismissed an item
public static final int ERROR = 11;       // An error occurred
public static final int SELECTED = 14;    // Item became selected
public static final int SUCCESS = 12;     // Action completed successfully
public static final int TAP = 13;         // User tapped on item
```

**Usage Example:**
```java
// Play sound effect
AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
audioManager.playSoundEffect(Sounds.TAP);

// Play custom sound with feedback
audioManager.playSoundEffect(Sounds.SUCCESS);
```

#### com.google.android.glass.touchpad
**Purpose**: Detect gestures on the touchpad (Added in API level XE12)

**GestureDetector Class:**
```java
public class GestureDetector {
    // Constructor
    public GestureDetector(Context context);
    
    // Static utility methods
    public static boolean isForward(Gesture gesture);
    public static boolean isForward(float deltaX);
    
    // Main processing method
    public boolean onMotionEvent(MotionEvent event);
    
    // Configuration
    public GestureDetector setAlwaysConsumeEvents(boolean enabled);
    
    // Listener registration
    public GestureDetector setBaseListener(BaseListener listener);
    public GestureDetector setFingerListener(FingerListener listener);
    public GestureDetector setOneFingerScrollListener(OneFingerScrollListener listener);
    public GestureDetector setScrollListener(ScrollListener listener);
    public GestureDetector setTwoFingerScrollListener(TwoFingerScrollListener listener);
}
```

**Gesture Enum - Supported Gestures:**
```java
// Single finger gestures
TAP, LONG_PRESS
SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT

// Two finger gestures  
TWO_TAP, TWO_LONG_PRESS
TWO_SWIPE_UP, TWO_SWIPE_DOWN, TWO_SWIPE_LEFT, TWO_SWIPE_RIGHT

// Three finger gestures
THREE_TAP, THREE_LONG_PRESS
```

**Complete Gesture Detection Example:**
```java
public class GestureActivity extends Activity {
    private GestureDetector mGestureDetector;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGestureDetector = createGestureDetector(this);
    }
    
    private GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        
        // Set base listener for simple gestures
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
                
                switch (gesture) {
                    case TAP:
                        audio.playSoundEffect(Sounds.TAP);
                        // Handle tap
                        return true;
                    case TWO_TAP:
                        audio.playSoundEffect(Sounds.TAP);
                        // Handle two-finger tap
                        return true;
                    case SWIPE_RIGHT:
                        // Navigate forward
                        return true;
                    case SWIPE_LEFT:
                        // Navigate backward
                        return true;
                    case SWIPE_DOWN:
                        audio.playSoundEffect(Sounds.DISMISSED);
                        finish();
                        return true;
                }
                return false;
            }
        });
        
        // Set finger listener to track finger count
        gestureDetector.setFingerListener(new GestureDetector.FingerListener() {
            @Override
            public void onFingerCountChanged(int previousCount, int currentCount) {
                // Handle finger count changes
            }
        });
        
        // Set scroll listener for continuous scrolling
        gestureDetector.setScrollListener(new GestureDetector.ScrollListener() {
            @Override
            public boolean onScroll(float displacement, float delta, float velocity) {
                // Handle scrolling
                return true;
            }
        });
        
        return gestureDetector;
    }
    
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return false;
    }
}
```

#### com.google.android.glass.view
**Purpose**: Alter Glass-specific behavior of Android UI elements (Added in API level XE12)

**Key Classes:**
- **MenuUtils**: Extensions for Android Menu classes
- **WindowUtils**: Extensions for WindowManager and related classes
  - Contains `FEATURE_VOICE_COMMANDS` for enabling voice menu

**Usage Example:**
```java
// Enable voice commands in activity
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().requestFeature(WindowUtils.FEATURE_VOICE_COMMANDS);
    setContentView(R.layout.activity_main);
}
```

#### com.google.android.glass.widget
**Purpose**: Glass-themed UI widgets (Added in API level XE12)

**CardBuilder Class - Complete API:**
```java
public class CardBuilder {
    // Constructor
    public CardBuilder(Context context, CardBuilder.Layout layout);
    
    // Image management
    public CardBuilder addImage(Drawable imageDrawable);
    public CardBuilder addImage(Bitmap imageBitmap);
    public CardBuilder addImage(int imageResId);
    public CardBuilder clearImages();
    
    // Content setters
    public CardBuilder setHeading(CharSequence heading);
    public CardBuilder setHeading(int headingResId);
    public CardBuilder setSubheading(CharSequence subheading);
    public CardBuilder setSubheading(int subheadingResId);
    public CardBuilder setText(CharSequence text);
    public CardBuilder setText(int textResId);
    public CardBuilder setFootnote(CharSequence footnote);
    public CardBuilder setFootnote(int footnoteResId);
    public CardBuilder setTimestamp(CharSequence timestamp);
    public CardBuilder setTimestamp(int timestampResId);
    
    // Icons
    public CardBuilder setIcon(Bitmap iconBitmap);
    public CardBuilder setIcon(Drawable iconDrawable);
    public CardBuilder setIcon(int iconResId);
    public CardBuilder setAttributionIcon(Bitmap iconBitmap);
    public CardBuilder setAttributionIcon(Drawable iconDrawable);
    public CardBuilder setAttributionIcon(int iconResId);
    
    // Layout customization
    public CardBuilder setEmbeddedLayout(int layoutResId);
    public CardBuilder showStackIndicator(boolean visible);
    
    // View generation
    public View getView();
    public View getView(View convertView, ViewGroup parent);
    public RemoteViews getRemoteViews();
    public int getItemViewType();
    public static int getViewTypeCount();
}

// Layout types enum
public enum CardBuilder.Layout {
    TEXT,         // Full-bleed text
    COLUMNS,      // Multi-column layout
    CAPTION,      // Image with caption
    TITLE,        // Title over image
    AUTHOR,       // Author with avatar
    MENU,         // Menu style
    ALERT,        // Alert with icon
    EMBED_INSIDE  // Custom embedded layout
}
```

**CardScrollView Class - Complete API:**
```java
public class CardScrollView extends AdapterView<CardScrollAdapter> {
    // Lifecycle methods
    public void activate();
    public void deactivate();
    
    // Navigation
    public boolean animate(int position, CardScrollView.Animation animationType);
    public void setSelection(int position);
    
    // Adapter management
    public CardScrollAdapter getAdapter();
    public void setAdapter(CardScrollAdapter adapter);
    
    // Selection info
    public long getSelectedItemId();
    public int getSelectedItemPosition();
    public View getSelectedView();
}

// Animation types
public enum CardScrollView.Animation {
    NAVIGATION,  // Standard navigation
    INSERTION,   // Card insertion
    DELETION     // Card deletion
}
```

**CardScrollAdapter Example:**
```java
public class MyCardScrollAdapter extends CardScrollAdapter {
    private List<CardBuilder> mCards;
    private Context mContext;
    
    public MyCardScrollAdapter(Context context) {
        mContext = context;
        mCards = new ArrayList<>();
        createCards();
    }
    
    private void createCards() {
        mCards.add(new CardBuilder(mContext, CardBuilder.Layout.TEXT)
            .setText("First card")
            .setFootnote("Card 1"));
            
        mCards.add(new CardBuilder(mContext, CardBuilder.Layout.COLUMNS)
            .setText("Second card")
            .setFootnote("Card 2")
            .addImage(R.drawable.image));
            
        mCards.add(new CardBuilder(mContext, CardBuilder.Layout.CAPTION)
            .setText("Third card")
            .addImage(R.drawable.background));
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
    public int getViewTypeCount() {
        return CardBuilder.getViewTypeCount();
    }
    
    @Override
    public int getItemViewType(int position) {
        return mCards.get(position).getItemViewType();
    }
    
    @Override
    public int getPosition(Object item) {
        return mCards.indexOf(item);
    }
}
```

**Slider Class - Progress Indicators:**
```java
public class Slider {
    // Factory method
    public static Slider from(View view);
    
    // Slider types with their methods
    public static final class Scroller {
        public Slider startScroller(int maxPosition, float initialPosition);
        public Slider setPosition(float position);
    }
    
    public static final class Determinate {
        public Slider startDeterminate(int maxPosition, float initialPosition);
        public Slider setPosition(float position);
    }
    
    public static final class Indeterminate {
        public Slider startIndeterminate();
    }
    
    public static final class GracePeriod {
        public interface Listener {
            void onGracePeriodEnd();
            void onGracePeriodCancel();
        }
        public Slider startGracePeriod(Listener listener);
    }
    
    // Control methods
    public void hide();
    public void show();
}
```

**Slider Usage Examples:**
```java
// Scroller slider for navigation
View view = findViewById(R.id.my_view);
Slider slider = Slider.from(view);
slider.startScroller(10, 0);  // 10 items, start at position 0
slider.setPosition(5);         // Move to position 5

// Determinate progress
Slider.from(view).startDeterminate(100, 0);  // 0-100% progress
// Update progress
slider.setPosition(50);  // 50% complete

// Indeterminate progress
Slider.from(view).startIndeterminate();

// Grace period with callback
Slider.from(view).startGracePeriod(new Slider.GracePeriod.Listener() {
    @Override
    public void onGracePeriodEnd() {
        // Grace period completed
    }
    
    @Override
    public void onGracePeriodCancel() {
        // User cancelled
    }
});
```

### Complete Application Example Using All APIs

```java
public class GlassAppActivity extends Activity {
    private CardScrollView mCardScroller;
    private GestureDetector mGestureDetector;
    private Slider mSlider;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable voice commands
        getWindow().requestFeature(WindowUtils.FEATURE_VOICE_COMMANDS);
        
        // Setup card scroller
        mCardScroller = new CardScrollView(this);
        MyCardScrollAdapter adapter = new MyCardScrollAdapter(this);
        mCardScroller.setAdapter(adapter);
        mCardScroller.activate();
        setContentView(mCardScroller);
        
        // Setup gesture detection
        mGestureDetector = new GestureDetector(this);
        setupGestureDetector();
        
        // Setup slider
        mSlider = Slider.from(mCardScroller);
        
        // Register for head state changes
        registerHeadStateReceiver();
    }
    
    private void setupGestureDetector() {
        mGestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
                
                switch (gesture) {
                    case TAP:
                        audio.playSoundEffect(Sounds.TAP);
                        openOptionsMenu();
                        return true;
                    case SWIPE_DOWN:
                        audio.playSoundEffect(Sounds.DISMISSED);
                        finish();
                        return true;
                }
                return false;
            }
        });
        
        mGestureDetector.setScrollListener(new GestureDetector.ScrollListener() {
            @Override
            public boolean onScroll(float displacement, float delta, float velocity) {
                // Update slider position based on scroll
                int position = mCardScroller.getSelectedItemPosition();
                int count = mCardScroller.getAdapter().getCount();
                mSlider.startScroller(count, position);
                return true;
            }
        });
    }
    
    private void registerHeadStateReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean isOnHead = intent.getBooleanExtra(
                    Intents.EXTRA_IS_ON_HEAD, false);
                if (!isOnHead) {
                    // Pause any ongoing operations
                    pauseApp();
                } else {
                    // Resume operations
                    resumeApp();
                }
            }
        };
        
        IntentFilter filter = new IntentFilter(
            Intents.ACTION_ON_HEAD_STATE_CHANGED);
        registerReceiver(receiver, filter);
    }
    
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return super.onGenericMotionEvent(event);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        switch (item.getItemId()) {
            case R.id.action_stop:
                audio.playSoundEffect(Sounds.DISMISSED);
                finish();
                return true;
            case R.id.action_settings:
                audio.playSoundEffect(Sounds.TAP);
                // Open settings
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        mCardScroller.activate();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        mCardScroller.deactivate();
    }
    
    private void pauseApp() {
        // Pause logic
    }
    
    private void resumeApp() {
        // Resume logic
    }
}
```

## Conclusion

While the Google Glass Explorer Edition SDK is deprecated, understanding its architecture provides valuable insights into wearable computing and AR interface design. The principles of minimal intrusion, contextual relevance, and natural interaction remain applicable to modern AR/VR development.

Key takeaways:
- Design for the unique form factor and use cases
- Prioritize user context and attention
- Build responsive, battery-efficient applications
- Follow established patterns for consistency
- Test thoroughly on actual hardware

This guide serves as a comprehensive reference for understanding Glass application development patterns and can inform future wearable and AR application design.