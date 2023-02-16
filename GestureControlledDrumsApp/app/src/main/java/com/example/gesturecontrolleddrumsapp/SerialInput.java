package com.example.gesturecontrolleddrumsapp;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class SerialInput extends AppCompatActivity {

    String TAG = "LedControl";

    Button btnStartStop, btnDisconnect;
    String address = null;
    TextView serialInput;
    ScrollView scrollView;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    InputStream btInputStream;
    volatile boolean stopWorker;
    boolean readingInput;

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
//        serialInput.setMovementMethod(new ScrollingMovementMethod());

        new SerialInput.ConnectBT().execute();

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
        btnDisconnect.setOnClickListener(v -> Disconnect());
    }

//    private void sendSignal(String number) {
//        if (btSocket != null) {
//            try {
//                btSocket.getOutputStream().write(number.toString().getBytes());
//            } catch (IOException e) {
//                msg("Error");
//            }
//        }
//    }

    // Helper functions
    private void msg(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    // Connect & Disconnect
    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;

        @Override
        protected void onPreExecute() {
            progress = ProgressDialog.show(SerialInput.this, "Connecting...", "Please wait");
        }

        @RequiresApi(api = Build.VERSION_CODES.S)
        @Override
        protected Void doInBackground(Void... devices) {
            try {
                if (btSocket == null || !isBtConnected) {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);
                    if (ActivityCompat.checkSelfPermission(SerialInput.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(SerialInput.this, MainActivity.ANDROID_12_BLE_PERMISSIONS, 2);
                        return null;
                    }
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();
                    btInputStream = btSocket.getInputStream();
                }
            } catch (IOException e) {
                ConnectSuccess = false;
            }
            return null;
        }

        @Override
        protected void onPostExecute (Void result) {
            super.onPostExecute(result);

            if (!ConnectSuccess) {
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            } else {
                msg("Connected");
                isBtConnected = true;
            }

            progress.dismiss();
        }
    }
    private void Disconnect() {
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

    void beginListenForData() {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(() -> {
            while(!Thread.currentThread().isInterrupted() && !stopWorker) {
                try {
                    int bytesAvailable = btInputStream.available();
                    if(bytesAvailable > 0) {
                        byte[] packetBytes = new byte[bytesAvailable];
                        btInputStream.read(packetBytes);
                        for(int i=0;i<bytesAvailable;i++) {
                            byte b = packetBytes[i];
                            if(b == delimiter) {
                                byte[] encodedBytes = new byte[readBufferPosition];
                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                final String data = new String(encodedBytes, "US-ASCII");
                                readBufferPosition = 0;
                                handler.post(() -> {
                                    String currText = serialInput.getText().toString();
                                    String newText = (currText.isEmpty()) ? data : currText + "\n" + data;
                                    serialInput.setText(newText);
                                    scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
                                });
                            } else {
                                readBuffer[readBufferPosition++] = b;
                            }
                        }
                    }
                }
                catch (IOException ex) {
                    stopWorker = true;
                }
            }
        });

        workerThread.start();
    }
}