# Google OAuth Setup for Glass Companion

## SHA-1 Certificate Fingerprints

These fingerprints are required when configuring Google OAuth 2.0 in the Google Cloud Console.

### Debug Certificate
- **SHA-1:** `F0:49:14:21:DC:C9:DB:98:3D:F1:87:AA:2D:69:F1:10:77:CC:C0:F6`
- **Keystore:** `debug.keystore`
- **Alias:** `androiddebugkey`
- **Password:** `android`

### Release Certificate
- **SHA-1:** `EA:FB:B7:BD:2D:71:5C:DE:DF:D1:8F:FD:FD:55:D8:D1:33:0A:E7:8D`
- **Keystore:** `release.keystore`
- **Alias:** `glasscompanion`
- **Password:** `glasscompanion2025`

## Google Cloud Console Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)

2. Create a new project or select existing project

3. Enable required APIs:
   - Google+ API
   - Google Identity Toolkit API

4. Create OAuth 2.0 credentials:
   - Go to APIs & Services > Credentials
   - Click "Create Credentials" > "OAuth client ID"
   - Select "Android" as application type
   - Enter the following:
     - **Name:** Glass Companion Debug (for debug)
     - **Package name:** `com.example.glasscompanion`
     - **SHA-1 certificate fingerprint:** Use debug SHA-1 above
   - Repeat for release version with release SHA-1

5. For OAuth web redirect (Chrome Custom Tabs):
   - Create another OAuth client ID
   - Select "Web application" as type
   - Add authorized redirect URI: `glasscompanion://google/oauth`

6. Copy the Client ID and update in `app/build.gradle.kts`:
   ```kotlin
   buildConfigField("String", "GOOGLE_CLIENT_ID", "\"YOUR_ACTUAL_CLIENT_ID.apps.googleusercontent.com\"")
   ```

## Important Security Notes

⚠️ **NEVER commit keystores to version control**
- Add `*.keystore` to `.gitignore`
- Store keystore passwords securely
- Keep release keystore in a safe location

## Testing OAuth Flow

1. Build and install the app with debug keystore
2. Navigate to Google Sign-In from main screen
3. Complete OAuth flow
4. Verify credentials are received
5. Test sync to Glass via Bluetooth

## Troubleshooting

If you get authentication errors:
- Verify SHA-1 matches exactly in Google Console
- Ensure package name is correct
- Check that all required APIs are enabled
- Wait a few minutes after creating credentials (propagation delay)