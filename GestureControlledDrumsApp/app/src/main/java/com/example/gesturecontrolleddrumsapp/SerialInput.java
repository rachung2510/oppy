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
import java.util.ArrayList;
import java.util.Calendar;
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
    AlertDialog progressDialog;

    // Serial reading/writing
    Thread readThread;
    byte[] readBuffer;
    int readBufferPosition;
    InputStream btInputStream;
    volatile boolean stopReadThread; // boolean for beginListenForData thread
    boolean readingInput = false; // boolean for btnStartStop
    int beatCount = 0; // for keeping track of no. of beats
    volatile boolean blockListen = false; // blocking call to set trial no.

    // Gesture data
    ArrayList<Float> indexArr = new ArrayList<>();
    ArrayList<Float> middleArr = new ArrayList<>();
    ArrayList<Float> ringArr = new ArrayList<>();
    ArrayList<Float> pinkyArr = new ArrayList<>();
    ArrayList<Float> accXArr = new ArrayList<>();
    ArrayList<Float> accYArr = new ArrayList<>();
    ArrayList<Float> accZArr = new ArrayList<>();
    ArrayList<Integer> beatArr = new ArrayList<>();
    ArrayList<String> dateArr = new ArrayList<>();
    ArrayList<Integer> beatIndArr = new ArrayList<>();
    String currTime = "";
    String gesture1, gesture2;
    int trialNum = -1;

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
                beatCount = 0;
                btnBeat.setText("Beat");
                clearDataArrays();
                blockListen = true;
                setTrialNum();
                while (blockListen);
                serialInput.setText("Reading...");
                btnStartStop.setImageResource(R.drawable.ic_baseline_stop_24);
                btnStartStop.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(SerialInput.this, R.color.red)));
                beginListenForData();

            } else { // stop
                readingInput = false;
                stopReadThread = true;
                btnStartStop.setImageResource(R.drawable.ic_baseline_play_arrow_24);
                btnStartStop.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(SerialInput.this, R.color.green)));
                serialInput.setText("");
                btnBeat.setText("Beat");
                for (Integer ind : beatIndArr) beatArr.set(ind, 1);
                sendData();
                try { readThread.join(); }
                catch (Exception e) { Log.e("btnStop", e.toString()); }
            }
        });

        // Disconnect
        btnDisconnect.setOnClickListener(v -> disconnectBT());

        // Click to indicate beat
        btnBeat.setOnClickListener(view -> {
            beatIndArr.add(beatArr.size());
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
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
        spinnerGesture2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long l) {
                gesture2 = Integer.toString(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
    }

    // Connect to / Disconnect from Bluetooth
    void connectBT() {
        // on pre-execute
        handler.post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(SerialInput.this);
            builder.setTitle("Connecting");
            builder.setCancelable(false); // if you want user to wait for some process to finish,
            builder.setView(R.layout.loading_dialog);
            progressDialog = builder.create();
            progressDialog.show();
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
        } catch (Exception e) {
            Log.e("connectBT", e.toString());
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
            progressDialog.dismiss();
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
        final byte delimiter = 10; // ASCII code for a newline character
        stopReadThread = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        readThread = new Thread(() -> {
            // flush all data that was read previously
            try { btInputStream.skip(btInputStream.available()); }
            catch (IOException e) { Log.e("workerThread", e.toString()); }

            // main thread
            while (!Thread.currentThread().isInterrupted() && !stopReadThread) {
                try {
                    int bytesAvailable = btInputStream.available();
                    if (bytesAvailable <= 0)
                        continue; // continue if input stream cannot read any bytes
                    byte[] packetBytes = new byte[bytesAvailable];
                    int bytesRead = btInputStream.read(packetBytes);
                    if (bytesRead <= 0)
                        continue; // continue if no bytes read
                    for (int i = 0; i < bytesAvailable; i++) {
                        byte b = packetBytes[i];
                        if (b == delimiter) {
                            byte[] encodedBytes = new byte[readBufferPosition];
                            System.arraycopy(readBuffer,0, encodedBytes, 0, encodedBytes.length);
                            final String readInput = new String(encodedBytes, StandardCharsets.US_ASCII);
                            readBufferPosition = 0;

                            // parse read input and
                            String[] data = readInput.split(",");
                            if (data.length != 7) data = new String[]{"-1","-1","-1","-1","0","0","0"};

                            // get time of start of data collection
                            currTime = String.valueOf(Calendar.getInstance().getTimeInMillis());

                            // Send POST request
                            beatArr.add(0);
                            indexArr.add(Float.valueOf(data[0]));
                            middleArr.add(Float.valueOf(data[1]));
                            ringArr.add(Float.valueOf(data[2]));
                            pinkyArr.add(Float.valueOf(data[3]));
                            accXArr.add(Float.valueOf(data[4]));
                            accYArr.add(Float.valueOf(data[5]));
                            accZArr.add(Float.valueOf(data[6]));
                            dateArr.add(String.valueOf(Calendar.getInstance().getTimeInMillis()));

                        } else {
                            readBuffer[readBufferPosition++] = b;
                        }
                    }
                } catch (IOException ex) {
                    stopReadThread = true;
                }
            }
        });
        readThread.start();
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
                jsonParam.put("date", dateArr.toString());
                jsonParam.put("index", indexArr.toString());
                jsonParam.put("middle", middleArr.toString());
                jsonParam.put("ring", ringArr.toString());
                jsonParam.put("pinky", pinkyArr.toString());
                jsonParam.put("accX", accXArr.toString());
                jsonParam.put("accY", accYArr.toString());
                jsonParam.put("accZ", accZArr.toString());
                jsonParam.put("beat", beatArr.toString());
                jsonParam.put("gesture", getGestureString());

                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(jsonParam.toString());
                os.flush();
                os.close();
                conn.getResponseCode(); // data doesn't send unless this is called
                Log.e(TAG, "JSON: " + jsonParam);
//                Log.e(TAG, "STATUS: " + conn.getResponseCode());
//                Log.e(TAG, "MSG: " + conn.getResponseMessage());
                conn.disconnect();
            } catch (Exception e) {
                Log.e("sendData", e.toString());
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
                Log.e("setTrialNum", e.toString());
            }
        });
        thread.start();
    }

    // Get gesture transition string
    public String getGestureString() {
        return gesture1 + "-" + gesture2;
    }

    // Clear data arrays for new trial
    public void clearDataArrays() {
        indexArr.clear();
        middleArr.clear();
        ringArr.clear();
        pinkyArr.clear();
        accXArr.clear();
        accYArr.clear();
        accZArr.clear();
        beatArr.clear();
        dateArr.clear();
        beatIndArr.clear();
    }

    // Helper function for Toast
    private void msg(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

}