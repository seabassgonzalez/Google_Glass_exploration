# Hello World Glass Application

A simple "Hello World" application for Google Glass Explorer Edition that demonstrates basic Glass development concepts.

## Features

- Displays "Hello World" message using Glass CardBuilder
- Handles touchpad gestures:
  - **Swipe Down**: Exit the application
  - **Tap**: Plays tap sound feedback
  - **Swipe Left/Right**: Navigation sound feedback
- Voice command activation: "OK Glass, show hello world"
- Audio feedback using Glass system sounds

## Project Structure

```
HelloWorldGlass/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/example/helloworldglass/
│   │       │       └── MainActivity.java
│   │       ├── res/
│   │       │   ├── values/
│   │       │   │   └── strings.xml
│   │       │   └── xml/
│   │       │       └── voice_trigger.xml
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## Requirements

- Android Studio
- Android SDK API 19 (KitKat 4.4.2)
- Glass Development Kit Preview
- Google Glass Explorer Edition device

## Setup Instructions

1. **Install Android Studio and SDK**
   - Open Android Studio
   - Go to SDK Manager
   - Install Android 4.4.2 (API 19)
   - Install "Glass Development Kit Preview" under API 19

2. **Enable USB Debugging on Glass**
   - On Glass: Settings → Device Info → Turn on debug
   - Connect Glass to computer via USB
   - Accept debugging authorization on Glass

3. **Import Project**
   - Open Android Studio
   - Select "Open an existing Android Studio project"
   - Navigate to the HelloWorldGlass directory
   - Click OK

4. **Build and Run**
   - Connect Glass device via USB
   - Click "Run" in Android Studio
   - Select your Glass device
   - The app will be installed and launched

## How It Works

### MainActivity.java
The main activity creates a simple card with "Hello World" text using Glass's CardBuilder API. It implements:
- `GestureDetector` for handling touchpad gestures
- Audio feedback using Glass system sounds
- Proper lifecycle management

### Key Components

1. **CardBuilder**: Creates the Glass-styled card UI
```java
View card = new CardBuilder(this, CardBuilder.Layout.TEXT)
    .setText("Hello World")
    .setFootnote("Swipe down to exit")
    .getView();
```

2. **GestureDetector**: Handles touchpad input
```java
gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
    @Override
    public boolean onGesture(Gesture gesture) {
        // Handle gestures
    }
});
```

3. **Voice Trigger**: Allows launching via "OK Glass"
```xml
<trigger keyword="@string/voice_trigger">
</trigger>
```

## Usage

1. **Launch the app**:
   - Say "OK Glass, show hello world", or
   - Tap and select from the launcher

2. **Interact**:
   - The screen displays "Hello World"
   - Tap the touchpad to hear feedback
   - Swipe down to exit the application

## Troubleshooting

- **Voice command not working**: Make sure the DEVELOPMENT permission is included in AndroidManifest.xml
- **App not installing**: Verify Glass device is connected and USB debugging is enabled
- **Build errors**: Ensure Glass Development Kit Preview is installed in SDK Manager

## Notes

- This app uses the deprecated Google Glass Explorer Edition SDK
- The code serves as a reference for Glass application development patterns
- Audio feedback enhances user experience on Glass devices

## License

This is a sample application for educational purposes.