package com.example.glassgpsreceiver;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.media.AudioManager;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollView;
import com.google.android.glass.widget.CardScrollAdapter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Glass GPS Receiver - Receives GPS data from companion phone app
 */
public class MainActivity extends Activity {
    private static final String TAG = "GlassGPSReceiver";
    private static final String APP_NAME = "GlassGPSReceiver";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket connectedSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread acceptThread;
    private Thread receiveThread;
    
    private GestureDetector mGestureDetector;
    private AudioManager mAudioManager;
    private CardScrollView mCardScroller;
    private GPSCardAdapter mAdapter;
    
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    
    // GPS Data
    private double latitude = 0.0;
    private double longitude = 0.0;
    private double altitude = 0.0;
    private float accuracy = 0.0f;
    private float speed = 0.0f;
    private String connectionStatus = "Waiting for connection...";
    private String lastUpdate = "Never";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize audio manager
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        // Initialize Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            connectionStatus = "Bluetooth not available";
        } else if (!bluetoothAdapter.isEnabled()) {
            connectionStatus = "Bluetooth is disabled";
        } else {
            startBluetoothServer();
        }
        
        // Setup UI
        setupCardScroller();
        
        // Create gesture detector
        mGestureDetector = createGestureDetector();
        
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    private void setupCardScroller() {
        mCardScroller = new CardScrollView(this);
        mAdapter = new GPSCardAdapter();
        mCardScroller.setAdapter(mAdapter);
        mCardScroller.activate();
        setContentView(mCardScroller);
    }
    
    private class GPSCardAdapter extends CardScrollAdapter {
        @Override
        public int getCount() {
            return 1;
        }
        
        @Override
        public Object getItem(int position) {
            return createGPSCard();
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return createGPSCard();
        }
        
        @Override
        public int getPosition(Object item) {
            return 0;
        }
        
        private View createGPSCard() {
            CardBuilder card = new CardBuilder(MainActivity.this, CardBuilder.Layout.TEXT);
            
            String text = String.format(
                "GPS Data from Phone\n\n" +
                "Latitude: %.6f\n" +
                "Longitude: %.6f\n" +
                "Altitude: %.1f m\n" +
                "Accuracy: %.1f m\n" +
                "Speed: %.1f m/s\n\n" +
                "Status: %s\n" +
                "Last Update: %s",
                latitude, longitude, altitude, accuracy, speed,
                connectionStatus, lastUpdate
            );
            
            card.setText(text);
            card.setFootnote("Swipe down to exit");
            
            return card.getView();
        }
    }
    
    private void startBluetoothServer() {
        connectionStatus = "Starting Bluetooth server...";
        updateUI();
        
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Create server socket
                    serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        APP_NAME, SPP_UUID);
                    
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            connectionStatus = "Waiting for phone connection...";
                            updateUI();
                        }
                    });
                    
                    // Wait for connection
                    connectedSocket = serverSocket.accept();
                    
                    // Connection established
                    inputStream = connectedSocket.getInputStream();
                    outputStream = connectedSocket.getOutputStream();
                    
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            connectionStatus = "Connected to phone";
                            mAudioManager.playSoundEffect(Sounds.SUCCESS);
                            updateUI();
                        }
                    });
                    
                    // Start receiving data
                    startReceivingData();
                    
                } catch (IOException e) {
                    Log.e(TAG, "Error in accept thread: " + e.getMessage());
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            connectionStatus = "Connection failed";
                            mAudioManager.playSoundEffect(Sounds.ERROR);
                            updateUI();
                        }
                    });
                }
            }
        });
        acceptThread.start();
    }
    
    private void startReceivingData() {
        receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                
                while (connectedSocket != null && connectedSocket.isConnected()) {
                    try {
                        line = reader.readLine();
                        if (line != null) {
                            handleReceivedData(line);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading data: " + e.getMessage());
                        break;
                    }
                }
                
                // Connection lost
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        connectionStatus = "Connection lost";
                        mAudioManager.playSoundEffect(Sounds.ERROR);
                        updateUI();
                        // Try to restart server
                        startBluetoothServer();
                    }
                });
            }
        });
        receiveThread.start();
    }
    
    private void handleReceivedData(String data) {
        try {
            JSONObject json = new JSONObject(data);
            String type = json.optString("type");
            
            if ("location".equals(type)) {
                // Update GPS data
                latitude = json.optDouble("latitude", 0.0);
                longitude = json.optDouble("longitude", 0.0);
                altitude = json.optDouble("altitude", 0.0);
                accuracy = (float) json.optDouble("accuracy", 0.0);
                speed = (float) json.optDouble("speed", 0.0);
                lastUpdate = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
                
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAudioManager.playSoundEffect(Sounds.TAP);
                        updateUI();
                    }
                });
                
                // Send acknowledgment
                sendResponse("location_received");
                
            } else if ("handshake".equals(type)) {
                // Handle handshake
                String device = json.optString("device");
                connectionStatus = "Connected to " + device;
                
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateUI();
                    }
                });
                
                // Send handshake response
                JSONObject response = new JSONObject();
                response.put("type", "handshake");
                response.put("device", "Google Glass");
                response.put("version", "1.0");
                sendMessage(response.toString());
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON: " + e.getMessage());
        }
    }
    
    private void sendMessage(String message) {
        if (outputStream != null) {
            try {
                outputStream.write((message + "\n").getBytes());
                outputStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Error sending message: " + e.getMessage());
            }
        }
    }
    
    private void sendResponse(String status) {
        try {
            JSONObject response = new JSONObject();
            response.put("type", "response");
            response.put("status", status);
            sendMessage(response.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating response: " + e.getMessage());
        }
    }
    
    private void updateUI() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }
    
    private GestureDetector createGestureDetector() {
        GestureDetector gestureDetector = new GestureDetector(this);
        
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                if (gesture == Gesture.TAP) {
                    mAudioManager.playSoundEffect(Sounds.TAP);
                    // Request location update
                    requestLocationUpdate();
                    return true;
                } else if (gesture == Gesture.SWIPE_DOWN) {
                    mAudioManager.playSoundEffect(Sounds.DISMISSED);
                    finish();
                    return true;
                }
                return false;
            }
        });
        
        return gestureDetector;
    }
    
    private void requestLocationUpdate() {
        try {
            JSONObject request = new JSONObject();
            request.put("type", "request");
            request.put("request", "location");
            sendMessage(request.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request: " + e.getMessage());
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
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up Bluetooth connections
        try {
            if (connectedSocket != null) {
                connectedSocket.close();
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing sockets: " + e.getMessage());
        }
        
        // Stop threads
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
        if (receiveThread != null) {
            receiveThread.interrupt();
        }
    }
}