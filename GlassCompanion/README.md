# Glass Companion App for Samsung S21 Ultra

A companion app for Samsung S21 Ultra (or any modern Android phone) that connects to Google Glass Explorer Edition via Bluetooth and shares GPS location data.

## Features

### Phone App (Glass Companion)
- **Bluetooth Connection**: Connects to paired Glass devices via Bluetooth SPP
- **GPS Sharing**: Real-time location sharing with Glass
- **Modern UI**: Built with Jetpack Compose for Android 15
- **Auto-reconnect**: Automatically attempts to reconnect if connection is lost
- **Background Location**: Continues sharing location when app is in background

### Glass App (GPS Receiver)
- **Bluetooth Server**: Accepts connections from companion phone
- **Real-time Updates**: Displays GPS data as it's received
- **Voice Control**: Launch via "OK Glass, show GPS data"
- **Gesture Control**: Tap to request location update, swipe down to exit

## Architecture

### Communication Protocol
- Uses Bluetooth Serial Port Profile (SPP) for communication
- JSON-based message protocol
- Bidirectional communication support

### Message Types
```json
// Location Update (Phone → Glass)
{
  "type": "location",
  "latitude": 37.4219999,
  "longitude": -122.0840575,
  "altitude": 10.5,
  "accuracy": 5.0,
  "speed": 2.5,
  "bearing": 180.0,
  "timestamp": 1234567890,
  "provider": "gps"
}

// Handshake
{
  "type": "handshake",
  "device": "Samsung S21 Ultra",
  "version": "1.0"
}

// Request (Glass → Phone)
{
  "type": "request",
  "request": "location"
}
```

## Setup Instructions

### Prerequisites
- Samsung S21 Ultra (or Android 12+ device)
- Google Glass Explorer Edition
- Android Studio (latest version)
- Glass Development Kit installed

### Phone App Setup

1. **Open the GlassCompanion project in Android Studio**
2. **Grant permissions when prompted**:
   - Bluetooth (for connection)
   - Location (for GPS sharing)
3. **Build and install on your phone**

### Glass App Setup

1. **Open the GlassGPSReceiver project in Android Studio**
2. **Ensure GDK is properly configured**
3. **Build and install on Glass**

### Pairing Devices

1. **On Glass**:
   - Settings → Bluetooth → Make discoverable

2. **On Phone**:
   - Settings → Bluetooth → Pair new device
   - Select your Glass device
   - Confirm pairing code

3. **Launch Apps**:
   - Start GPS Receiver on Glass first
   - Start Glass Companion on phone
   - Select Glass from paired devices list
   - Enable GPS sharing

## Usage

### On Phone
1. Launch Glass Companion app
2. Tap on your Glass device from the list
3. Toggle "Share GPS with Glass" switch
4. GPS data will be sent automatically every 5 seconds

### On Glass
1. Say "OK Glass, show GPS data" or launch from menu
2. View real-time GPS information
3. Tap touchpad to request immediate update
4. Swipe down to exit

## Technical Details

### Phone App Requirements
- Minimum SDK: 31 (Android 12)
- Target SDK: 34 (Android 14)
- Compile SDK: 34
- Kotlin 1.9.22
- Jetpack Compose

### Glass App Requirements
- API Level: 19 (Android 4.4.2)
- Glass Development Kit Preview
- Java 7 compatibility

### Permissions

#### Phone App
- `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` (Android 12+)
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`

#### Glass App
- `BLUETOOTH`, `BLUETOOTH_ADMIN`
- `com.google.android.glass.permission.DEVELOPMENT`

## Troubleshooting

### Connection Issues
- Ensure both devices have Bluetooth enabled
- Verify devices are paired in system settings
- Check that no other app is using Bluetooth SPP
- Try unpairing and re-pairing devices

### GPS Issues
- Ensure location services are enabled on phone
- Grant all location permissions to the app
- Check that phone has GPS fix (go outside if needed)
- Verify GPS sharing toggle is enabled

### Glass Display Issues
- Ensure Glass screen is on
- Try tapping touchpad to refresh display
- Check that app is in foreground on Glass

## Architecture Benefits

1. **Separation of Concerns**: Phone handles GPS, Glass handles display
2. **Battery Efficiency**: Glass doesn't need to run GPS constantly
3. **Better GPS Signal**: Phone typically has better GPS reception
4. **Modern Features**: Leverages phone's advanced location services

## Future Enhancements

- Add map visualization on Glass
- Support for waypoint navigation
- Compass integration
- Speed and distance tracking
- Route recording and playback
- Multiple phone support
- Web dashboard for tracking

## Known Limitations

- Glass Explorer Edition Bluetooth can be unstable
- Maximum Bluetooth range ~30 feet
- GPS updates limited to 5-second intervals
- No background operation on Glass

## License

This is a demonstration project for educational purposes.