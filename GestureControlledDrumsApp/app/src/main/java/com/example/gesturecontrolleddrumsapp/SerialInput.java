package com.example.gesturecontrolleddrumsapp;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SerialInput extends AppCompatActivity {

    String TAG = "SerialInput";

    // UI components
    Button btnDisconnect, btnBeat;
    ImageButton btnStartStop;
    String address = null;
    TextView trialTitle, serialInput;
    ScrollView scrollView;
    Spinner spinnerGesture1, spinnerGesture2;

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
    int beatBool = 0;
    int beatCount = 0;

    // GestureData
    String currTime = null;
    Float indexVal, middleVal, ringVal, pinkyVal;
    String gesture1, gesture2;
    int trialNum = -1;
    volatile boolean blockListen = false; // blocking call to set trial no.

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_serial_input);

        Intent intent = getIntent();
        address = intent.getStringExtra(MainActivity.EXTRA_ADDRESS);

        btnStartStop = findViewById(R.id.btnStartStop);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnBeat = findViewById(R.id.btnBeat);
        trialTitle = findViewById(R.id.trialTitle);
        serialInput = findViewById(R.id.serialInputText);
        scrollView = findViewById(R.id.scrollView);
        spinnerGesture1 = findViewById(R.id.spinner_gesture1);
        spinnerGesture2 = findViewById(R.id.spinner_gesture2);

        // Connect to bluetooth
        executor.execute(this::connectBT);

        // Start reading
        btnStartStop.setOnClickListener(view -> {
            if (!readingInput) { // start
                readingInput = true;
                serialInput.setText("");
                setTrialNum();
                while (blockListen);
                beginListenForData();
                btnStartStop.setImageResource(R.drawable.ic_baseline_stop_24);
                btnStartStop.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(SerialInput.this, R.color.red)));
            } else { // stop
                readingInput = false;
                stopWorker = true;
                btnStartStop.setImageResource(R.drawable.ic_baseline_play_arrow_24);
                btnStartStop.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(SerialInput.this, R.color.green)));
                beatCount = 0;
                btnBeat.setText("Beat");
            }
        });

        // Disconnect
        btnDisconnect.setOnClickListener(v -> disconnectBT());

        // Click to indicate beat
        btnBeat.setOnClickListener(view -> {
            if (beatBool == 1) return;
            beatBool = 1;
            beatCount++;
            btnBeat.setText("Beat " + beatCount);
        });

        // Set gestures dropdown spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.gestures, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        spinnerGesture1.setAdapter(adapter);
        spinnerGesture2.setAdapter(adapter);
        spinnerGesture1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long l) {
                gesture1 = Integer.toString(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        spinnerGesture2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long l) {
                gesture2 = Integer.toString(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        // Set trial
        setTrialNum();
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
                    int[] counts = new int[256];
                    for (byte b : packetBytes) {
                        counts[b & 0xFF]++;
                    }
                    int num_delimiter_in_arr = counts[delimiter];
                    for (int i = 0; i < bytesAvailable; i++) {
                        byte b = packetBytes[i];
                        if (b == delimiter) {
                            byte[] encodedBytes = new byte[readBufferPosition];
                            System.arraycopy(readBuffer,0, encodedBytes, 0, encodedBytes.length);
                            final String readInput = new String(encodedBytes, StandardCharsets.US_ASCII);
                            readBufferPosition = 0;

                            if ((sampleCount == 0) && (num_delimiter_in_arr > 1)) {
                                num_delimiter_in_arr--;
                                continue;
                            }

                            // parse read input and
                            // ignore data if it doesn't contain 4 flex values
                            String[] data = readInput.split(",");
                            if (data.length != 4) continue;

                            // get time of start of data collection
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK);
                            String date = sdf.format(Calendar.getInstance().getTime());
                            String millis = String.valueOf(Calendar.getInstance().getTimeInMillis());
                            currTime = date + "," + millis;

                            // Store data
                            if (data[0].isEmpty()) continue; // ignore if data is empty
                            indexVal = Float.valueOf(data[0]);
                            middleVal = Float.valueOf(data[1]);
                            ringVal = Float.valueOf(data[2]);
                            pinkyVal = Float.valueOf(data[3]);

                            // Send POST request
                            sendData();
                            sampleCount++;

                            // Log to terminal every ~1s
                            if ((sampleCount - 1) % 50 != 0) continue;
                            String currText = serialInput.getText().toString();
                            String newText = currText + date + ": Data sent\n";
                            handler.post(() -> {
                                serialInput.setText(newText);
                                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
                            });
                        } else {
                            readBuffer[readBufferPosition++] = b;
                        }
                    }
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
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S)
                    return;
                URL url = new URL(MainActivity.EC2_URL + "/add-training-data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept","application/json");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                JSONObject jsonParam = new JSONObject();
                jsonParam.put("trial", trialNum);
                jsonParam.put("date", currTime);
                jsonParam.put("index", indexVal);
                jsonParam.put("middle", middleVal);
                jsonParam.put("ring", ringVal);
                jsonParam.put("pinky", pinkyVal);
                jsonParam.put("beat", beatBool);
                jsonParam.put("gesture", getGestureString());
                beatBool = 0;

                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(jsonParam.toString());
                os.flush();
                os.close();

                conn.getResponseCode(); // for some reason data doesn't send unless this is called
//                Log.e(TAG, "JSON: " + jsonParam);
//                Log.e(TAG, "STATUS: " + conn.getResponseCode());
//                Log.e(TAG, "MSG: " + conn.getResponseMessage());
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        });
        thread.start();
    }

    // Send GET request for next trial number
    public void setTrialNum() {
        Thread thread = new Thread(() -> {
            try {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S)
                    return;
                blockListen = true;
                URL url = new URL(MainActivity.EC2_URL + "/get-next-trial");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept-Charset", "UTF-8");
                conn.setDoOutput(false);

                InputStream in = new BufferedInputStream(conn.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
                trialNum = Integer.parseInt(result.toString());
                trialTitle.setText("Trial " + result);
                blockListen = false;
                in.close();
                conn.disconnect();
            } catch (Exception e) {
                Log.e("getData", e.toString());
            }
        });
        thread.start();
    }

    // Get gesture transition string
    public String getGestureString() {
        return gesture1 + "-" + gesture2;
    }

}