# Glass Strava - Complete Strava Integration for Google Glass

A comprehensive Strava integration app for Google Glass that brings fitness tracking, navigation, and performance metrics to the revolutionary wearable platform.

## 🎯 Features

### 1. **OAuth Authentication**
- Secure Strava OAuth 2.0 implementation
- Mobile-optimized authentication flow for Glass
- Automatic token refresh for uninterrupted access
- Stores athlete profile information

### 2. **Real-time Activity Tracking**
- Track runs, rides, and walks with GPS precision
- Live metrics display:
  - Elapsed time
  - Distance covered
  - Current/average speed
  - Elevation gain
  - Heart rate (when available)
- Pause/resume functionality
- Auto-pause when stationary
- Voice commands for hands-free control
- Automatic activity upload to Strava

### 3. **Live Segments**
- Discovers nearby Strava segments automatically
- Real-time segment matching during activities
- Leaderboard display showing:
  - KOM/QOM times
  - Personal records
  - Top 10 efforts
- Audio/haptic feedback when entering segments
- Live segment time comparison

### 4. **GPS Route Navigation**
- Turn-by-turn navigation for Strava routes
- Voice-guided directions
- Vibration patterns for turn alerts:
  - Single vibration: continue straight
  - Double vibration: turn right
  - Long vibration: turn left
- Distance to next waypoint
- Remaining route distance
- Automatic route completion detection

### 5. **Performance Metrics & Feedback**
- Real-time performance analysis
- Audio cues for pace zones
- Vibration alerts for:
  - Target pace achievement
  - Segment PRs
  - Milestone distances
- Visual power/heart rate zones
- Cadence tracking (when available)

### 6. **Activity Sync**
- Automatic background sync to Strava
- Queue management for offline activities
- Batch upload support
- Activity metadata:
  - Device name: "Google Glass"
  - GPS track with timestamps
  - Elevation profile
  - Heart rate data (if available)

## 📱 User Interface

### Card-Based Navigation
The app uses Glass's native Card UI system for intuitive navigation:

```
Main Menu Cards:
├── Connect to Strava (Authentication)
├── Track Activity (Start recording)
├── Live Segments (View nearby segments)
├── Route Navigation (Follow routes)
├── Performance Metrics (Real-time stats)
├── Sync Activities (Upload to Strava)
└── Settings (Configure preferences)
```

### Gesture Controls
- **TAP**: Select/Start/Pause
- **TWO_TAP**: Open menu/Reset
- **SWIPE_RIGHT**: Next card/Skip
- **SWIPE_LEFT**: Previous card/Statistics
- **SWIPE_DOWN**: Exit/Go back

### Voice Commands
- "OK Glass, start strava" - Launch app
- "OK Glass, track activity" - Start tracking
- "OK Glass, show segments" - View segments
- "OK Glass, navigate route" - Start navigation

## 🏗️ Technical Architecture

### Core Components

#### Activities
- **MainActivity**: Central hub with card navigation
- **StravaAuthActivity**: OAuth authentication handler
- **ActivityTrackingActivity**: Real-time tracking UI
- **SegmentActivity**: Segment discovery and leaderboards
- **RouteNavigationActivity**: Turn-by-turn navigation
- **PerformanceActivity**: Metrics dashboard

#### Services
- **ActivityTrackingService**: GPS tracking and metrics calculation
- **StravaApiService**: API communication and sync
- **LocationTrackingService**: Background location updates

#### Models
- **Segment**: Strava segment data model
- **SegmentEffort**: Leaderboard entry model
- **Route**: Navigation route model
- **Activity**: Tracked activity model

### API Integration

#### Authentication Flow
```java
// OAuth URL construction
https://www.strava.com/oauth/mobile/authorize
  ?client_id=YOUR_CLIENT_ID
  &redirect_uri=glassstrava://oauth
  &response_type=code
  &scope=activity:read_all,activity:write,profile:read_all
```

#### Key API Endpoints Used
- `/oauth/token` - Token exchange and refresh
- `/athlete` - Profile information
- `/activities` - Activity upload
- `/segments/explore` - Nearby segments
- `/segments/{id}/leaderboard` - Segment rankings
- `/routes/{id}` - Route details
- `/athletes/{id}/routes` - User's routes

### Data Management
- **SharedPreferences**: Token and user data storage
- **In-memory caching**: Segments and routes
- **Queue system**: Offline activity storage
- **GPS tracking**: Location point collection with timestamps

## 🚀 Setup Instructions

### Prerequisites
1. Google Glass Explorer or Enterprise Edition
2. Android SDK 19 (KitKat)
3. Glass Development Kit (GDK)
4. Strava Developer Account

### Configuration

1. **Register Strava App**:
   - Go to https://www.strava.com/settings/api
   - Create new application
   - Note Client ID and Client Secret
   - Set Authorization Callback: `glassstrava://oauth`

2. **Configure API Credentials (Secure Method)**:
   ```bash
   # Copy the example file
   cp local.properties.example local.properties
   
   # Edit local.properties and add your credentials
   nano local.properties
   ```
   
   Add your credentials:
   ```properties
   strava.client.id=YOUR_ACTUAL_CLIENT_ID
   strava.client.secret=YOUR_ACTUAL_CLIENT_SECRET
   ```
   
   **Important**: The `local.properties` file is gitignored and will NOT be committed to version control.

3. **Build and Install**:
   ```bash
   cd GlassStrava
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## 💡 Usage Examples

### Starting an Activity
1. Launch: "OK Glass, start strava"
2. Navigate to "Track Activity" card
3. TAP to start recording
4. View real-time metrics on screen
5. TAP to pause, TWO_TAP for menu
6. Select "Stop" to save and upload

### Navigating a Route
1. Open app and select "Route Navigation"
2. Choose from your saved routes
3. Follow turn-by-turn directions
4. Feel vibration alerts for turns
5. Complete route for celebration feedback

### Viewing Segments
1. Select "Live Segments"
2. App automatically finds nearby segments
3. TAP on segment for full leaderboard
4. View KOM times and personal records

## 🔋 Performance Optimizations

- **Battery Management**:
  - Adaptive GPS sampling rate
  - Screen dimming during long activities
  - Wake lock only when necessary
  - Background service optimization

- **Network Efficiency**:
  - Batch API requests
  - Offline queue for activities
  - Compressed data transmission
  - Smart sync scheduling

- **UI Responsiveness**:
  - Asynchronous API calls
  - Efficient card recycling
  - Minimal view inflation
  - Hardware acceleration

## 🛡️ Privacy & Security

- OAuth 2.0 secure authentication
- No password storage on device
- Encrypted token storage
- Auto-logout on extended inactivity
- Privacy-respecting location handling

## 📊 Metrics Tracked

- **Distance**: GPS-calculated with elevation correction
- **Speed**: Current, average, and maximum
- **Time**: Elapsed, moving, and stopped time
- **Elevation**: Gain, loss, and current altitude
- **Heart Rate**: When paired with compatible sensor
- **Cadence**: For cycling activities
- **Power**: When power meter connected
- **Calories**: Estimated based on activity

## 🔄 Sync Capabilities

- **Automatic Upload**: Activities sync when completed
- **Manual Sync**: Force sync from main menu
- **Offline Support**: Queue activities for later upload
- **Conflict Resolution**: Handles duplicate uploads
- **Data Integrity**: Validates GPS tracks before upload

## 🎨 Glass-Specific Features

- **Heads-up Display**: Minimal UI for safety
- **Voice Control**: Hands-free operation
- **Bone Conduction Audio**: Clear audio feedback
- **Prism Display**: Optimized for 640x360 resolution
- **Touchpad Gestures**: Natural interaction
- **Long Battery Life**: 4-6 hours continuous tracking

## 🚧 Known Limitations

- Strava API rate limits (600 requests/15min)
- Glass battery life during GPS tracking
- Limited offline map support
- No live streaming to followers
- Segment creation not supported

## 🔮 Future Enhancements

- [ ] Workout plans and training schedules
- [ ] Social features (kudos, comments)
- [ ] Photo integration during activities
- [ ] Live coaching and audio prompts
- [ ] Integration with external sensors
- [ ] Offline segment detection
- [ ] Custom segment creation
- [ ] Group activity support

## 📝 Development Notes

- Compiled against API 19 (Glass runs Android 4.4)
- Uses GDK for Glass-specific features
- Network operations use AsyncTask (pre-coroutines)
- Location services use GPS and Network providers
- Follows Glass design guidelines

## 🤝 Contributing

This project demonstrates Strava API integration with Google Glass. Contributions welcome for:
- Additional Strava API features
- Performance optimizations
- UI/UX improvements
- Bug fixes and testing

## 📄 License

MIT License - Free to use and modify

## ⚠️ Disclaimer

This is an unofficial app not affiliated with Strava, Inc. Use of the Strava API is subject to Strava's API Agreement.