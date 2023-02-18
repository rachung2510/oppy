package com.example.gesturecontrolleddrumsapp;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SerialInput extends AppCompatActivity {

    String TAG = "SerialInput";

    // UI components
    Button btnStartStop, btnDisconnect;
    String address = null;
    TextView serialInput;
    ScrollView scrollView;

    // Bluetooth connection components
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());
    boolean connectSuccess = true;
    AlertDialog dialog;

    // Serial reading
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    InputStream btInputStream;
    volatile boolean stopWorker;
    boolean readingInput;

    // GestureData
    Date currTime = null;
    int NUM_SAMPLES = 25; // approx 1s
    Float[] indexVal = new Float[NUM_SAMPLES];
    Float[] middleVal = new Float[NUM_SAMPLES];
    Float[] ringVal = new Float[NUM_SAMPLES];
    Float[] pinkyVal = new Float[NUM_SAMPLES];
    Float[] accX = new Float[NUM_SAMPLES];
    Float[] accY = new Float[NUM_SAMPLES];
    Float[] accZ = new Float[NUM_SAMPLES];
    String gesture = "01";

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_serial_input);

        Intent intent = getIntent();
        address = intent.getStringExtra(MainActivity.EXTRA_ADDRESS);

        btnStartStop = findViewById(R.id.btnStartStop);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        serialInput = findViewById(R.id.serialInputText);
        scrollView = findViewById(R.id.scrollView);

        // Connect to bluetooth
        executor.execute(() -> connectBT());

        // Start reading
        btnStartStop.setOnClickListener(view -> {
            if (!readingInput) {
                readingInput = true;
                serialInput.setText("");
                beginListenForData();
                btnStartStop.setText("Stop Reading");
            } else {
                readingInput = false;
                stopWorker = true;
                btnStartStop.setText("Start Reading");
            }
        });

        // Disconnect
        btnDisconnect.setOnClickListener(v -> disconnectBT());
    }

    // Helper functions
    private void msg(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    // Connect to / Disconnect from Bluetooth
    void connectBT() {
        // on pre-execute
        handler.post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(SerialInput.this);
            builder.setTitle("Connecting");
            builder.setCancelable(false); // if you want user to wait for some process to finish,
            builder.setView(R.layout.loading_dialog);
            dialog = builder.create();
            dialog.show();
        });

        // do in background
        try {
            if (btSocket == null || !isBtConnected) {
                if (ActivityCompat.checkSelfPermission(SerialInput.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        ActivityCompat.requestPermissions(SerialInput.this, MainActivity.ANDROID_12_BLE_PERMISSIONS, 2);
                    return;
                }

                myBluetooth = BluetoothAdapter.getDefaultAdapter();
                BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);
                btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                btSocket.connect();
                btInputStream = btSocket.getInputStream();
            }
        } catch (IOException e) {
            connectSuccess = false;
        }

        // on post-execute
        handler.post(() -> {
            if (!connectSuccess) {
                msg("Connection failed. Is it a SPP Bluetooth device? Try again.");
                finish();
            } else {
                msg("Connected to device");
                isBtConnected = true;
            }
            dialog.dismiss();
        });
    }
    void disconnectBT() {
        if (btSocket != null) {
            try {
                btSocket.close();
                msg("Device disconnected");
            } catch (IOException e) {
                msg("Error disconnecting");
            }
        }
        finish();
    }

    // Listen to Bluetooth serial input, printing line by line
    void beginListenForData() {
        final Handler handler = new Handler();
        final byte delimiter = 10; // ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(() -> {
            int lineCount = 0;
            int sampleCount = 0;
            while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                try {
                    int bytesAvailable = btInputStream.available();
                    if (bytesAvailable <= 0)
                        continue; // continue if input stream cannot read any bytes
                    byte[] packetBytes = new byte[bytesAvailable];
                    int bytesRead = btInputStream.read(packetBytes);
                    if (bytesRead < 0)
                        continue; // continue if no bytes read
                    for (int i = 0; i < bytesAvailable; i++) {
                        byte b = packetBytes[i];
                        if (b == delimiter) {
                            byte[] encodedBytes = new byte[readBufferPosition];
                            System.arraycopy(readBuffer,0, encodedBytes, 0, encodedBytes.length);
                            final String readInput = new String(encodedBytes, StandardCharsets.US_ASCII);
                            readBufferPosition = 0;

                            // parse read input and
                            // ignore data if it doesn't contain 4 flex values and 3 acc values
                            String[] data = readInput.split(",");
                            if (data.length != 7) continue;

                            // reset data array to zeros (init new data array)
                            if (lineCount == 0) sampleCount = 0;

                            // get time of start of data collection
                            if (sampleCount == 0) currTime = Calendar.getInstance().getTime();

                            // Store data
                            indexVal[sampleCount] = Float.valueOf(data[0]);
                            middleVal[sampleCount] = Float.valueOf(data[1]);
                            ringVal[sampleCount] = Float.valueOf(data[2]);
                            pinkyVal[sampleCount] = Float.valueOf(data[3]);
                            accX[sampleCount] = Float.valueOf(data[4]);
                            accY[sampleCount] = Float.valueOf(data[5]);
                            accZ[sampleCount] = Float.valueOf(data[6]);

                            // Continue while data array is not filled
                            if (sampleCount < NUM_SAMPLES - 1) {
                                sampleCount++;
                                continue;
                            }

                            // Send POST request
                            sendDataIndiv();

                            // Log to terminal
                            String logText = currTime + ": Data sent\n";
                            String currText = serialInput.getText().toString();
                            String newText = currText + logText;
                            handler.post(() -> {
                                serialInput.setText(newText);
                                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
                            });
                            sampleCount = 0; // reset to 0

                        } else {
                            readBuffer[readBufferPosition++] = b;
                        }
                    }
                    lineCount++;
                } catch (IOException ex) {
                    stopWorker = true;
                }
            }
        });

        workerThread.start();
    }

    // Send POST request
    public void sendData() {
        Thread thread = new Thread(() -> {
            try {
                URL url = new URL(MainActivity.EC2_URL + "/add-training-data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept","application/json");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                JSONObject jsonParam = new JSONObject();
                jsonParam.put("date", currTime.toString());
                jsonParam.put("index", Arrays.toString(indexVal));
                jsonParam.put("middle", Arrays.toString(middleVal));
                jsonParam.put("ring", Arrays.toString(ringVal));
                jsonParam.put("pinky", Arrays.toString(pinkyVal));
                jsonParam.put("accX", Arrays.toString(accX));
                jsonParam.put("accY", Arrays.toString(accY));
                jsonParam.put("accZ", Arrays.toString(accZ));
                jsonParam.put("gesture", gesture);

                Log.e(TAG, "JSON: " + jsonParam);
                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(jsonParam.toString());

                os.flush();
                os.close();

                Log.e(TAG, "STATUS: " + conn.getResponseCode());
                Log.e(TAG, "MSG: " + conn.getResponseMessage());

                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        });

        thread.start();
    }
}