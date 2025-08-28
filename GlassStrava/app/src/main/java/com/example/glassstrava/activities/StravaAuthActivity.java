package com.example.glassstrava.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardBuilder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Handles Strava OAuth authentication flow for Glass
 */
public class StravaAuthActivity extends Activity {
    private static final String TAG = "StravaAuth";
    
    // API credentials loaded from BuildConfig (stored in local.properties)
    private static final String CLIENT_ID = com.example.glassstrava.BuildConfig.STRAVA_CLIENT_ID;
    private static final String CLIENT_SECRET = com.example.glassstrava.BuildConfig.STRAVA_CLIENT_SECRET;
    private static final String REDIRECT_URI = "glassstrava://oauth";
    
    private static final String AUTH_URL = "https://www.strava.com/oauth/mobile/authorize"
            + "?client_id=" + CLIENT_ID
            + "&redirect_uri=" + REDIRECT_URI
            + "&response_type=code"
            + "&approval_prompt=auto"
            + "&scope=activity:read_all,activity:write,read,profile:read_all";
    
    private static final String TOKEN_URL = "https://www.strava.com/oauth/token";
    
    private SharedPreferences mPrefs;
    private AudioManager mAudioManager;
    private View mAuthCard;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mPrefs = getSharedPreferences("StravaGlass", MODE_PRIVATE);
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        // Check if we're handling OAuth callback
        Intent intent = getIntent();
        Uri data = intent.getData();
        
        if (data != null && data.getScheme().equals("glassstrava")) {
            handleOAuthCallback(data);
        } else {
            showAuthCard();
        }
    }
    
    private void showAuthCard() {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.TEXT);
        card.setText("Strava Authentication");
        card.setFootnote("Opening browser for sign in...");
        
        mAuthCard = card.getView();
        setContentView(mAuthCard);
        
        // Launch OAuth flow
        launchOAuthFlow();
    }
    
    private void launchOAuthFlow() {
        // Open browser for OAuth
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(AUTH_URL));
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(browserIntent);
    }
    
    private void handleOAuthCallback(Uri data) {
        String code = data.getQueryParameter("code");
        String error = data.getQueryParameter("error");
        
        if (error != null) {
            showError("Authentication failed: " + error);
            return;
        }
        
        if (code != null) {
            showProgress("Authenticating with Strava...");
            exchangeCodeForToken(code);
        }
    }
    
    private void exchangeCodeForToken(final String code) {
        new AsyncTask<Void, Void, Boolean>() {
            private String accessToken;
            private String refreshToken;
            private String athleteName;
            private long expiresAt;
            
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    URL url = new URL(TOKEN_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    
                    String postData = "client_id=" + CLIENT_ID
                            + "&client_secret=" + CLIENT_SECRET
                            + "&code=" + code
                            + "&grant_type=authorization_code";
                    
                    OutputStream os = conn.getOutputStream();
                    os.write(postData.getBytes());
                    os.flush();
                    os.close();
                    
                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line);
                        }
                        br.close();
                        
                        // Parse JSON response
                        JSONObject json = new JSONObject(response.toString());
                        accessToken = json.getString("access_token");
                        refreshToken = json.getString("refresh_token");
                        expiresAt = json.getLong("expires_at");
                        
                        // Get athlete info
                        JSONObject athlete = json.getJSONObject("athlete");
                        athleteName = athlete.getString("firstname") + " " + 
                                    athlete.getString("lastname");
                        
                        return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error exchanging code for token", e);
                }
                return false;
            }
            
            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    // Save tokens
                    SharedPreferences.Editor editor = mPrefs.edit();
                    editor.putString("strava_access_token", accessToken);
                    editor.putString("strava_refresh_token", refreshToken);
                    editor.putLong("strava_token_expires_at", expiresAt);
                    editor.putString("strava_athlete_name", athleteName);
                    editor.apply();
                    
                    showSuccess("Connected as " + athleteName);
                    mAudioManager.playSoundEffect(Sounds.SUCCESS);
                    
                    // Return to main activity
                    finish();
                } else {
                    showError("Failed to authenticate");
                    mAudioManager.playSoundEffect(Sounds.ERROR);
                }
            }
        }.execute();
    }
    
    private void refreshAccessToken() {
        String refreshToken = mPrefs.getString("strava_refresh_token", null);
        if (refreshToken == null) {
            launchOAuthFlow();
            return;
        }
        
        new AsyncTask<Void, Void, Boolean>() {
            private String newAccessToken;
            private String newRefreshToken;
            private long newExpiresAt;
            
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    URL url = new URL(TOKEN_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    
                    String postData = "client_id=" + CLIENT_ID
                            + "&client_secret=" + CLIENT_SECRET
                            + "&refresh_token=" + refreshToken
                            + "&grant_type=refresh_token";
                    
                    OutputStream os = conn.getOutputStream();
                    os.write(postData.getBytes());
                    os.flush();
                    os.close();
                    
                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line);
                        }
                        br.close();
                        
                        JSONObject json = new JSONObject(response.toString());
                        newAccessToken = json.getString("access_token");
                        newRefreshToken = json.getString("refresh_token");
                        newExpiresAt = json.getLong("expires_at");
                        
                        return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing token", e);
                }
                return false;
            }
            
            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    SharedPreferences.Editor editor = mPrefs.edit();
                    editor.putString("strava_access_token", newAccessToken);
                    editor.putString("strava_refresh_token", newRefreshToken);
                    editor.putLong("strava_token_expires_at", newExpiresAt);
                    editor.apply();
                    
                    showSuccess("Token refreshed");
                } else {
                    showError("Failed to refresh token");
                    launchOAuthFlow();
                }
            }
        }.execute();
    }
    
    private void showProgress(String message) {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.TEXT);
        card.setText("Authenticating");
        card.setFootnote(message);
        setContentView(card.getView());
    }
    
    private void showSuccess(String message) {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.ALERT);
        card.setText("Success!");
        card.setFootnote(message);
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
}