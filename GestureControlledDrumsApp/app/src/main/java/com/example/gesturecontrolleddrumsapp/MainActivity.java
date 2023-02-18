package com.example.gesturecontrolleddrumsapp;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

@RequiresApi(api = Build.VERSION_CODES.S)
public class MainActivity extends AppCompatActivity {

    String TAG = "MainActivity";

    private BluetoothAdapter myBluetooth = null;
    public static String EXTRA_ADDRESS = "device_address";
    ListView deviceList;

    public static final String[] ANDROID_12_BLE_PERMISSIONS = new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
    };
    public static final String EC2_URL = "http://54.172.65.79:5000";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btnPaired;

        btnPaired = findViewById(R.id.button);
        deviceList = findViewById(R.id.btDeviceList);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, ANDROID_12_BLE_PERMISSIONS, 2);
            return;
        }

        myBluetooth = BluetoothAdapter.getDefaultAdapter();
        if (myBluetooth == null) {
            Toast.makeText(getApplicationContext(), "Bluetooth device not available", Toast.LENGTH_LONG).show();
            finish();
        } else if (!myBluetooth.isEnabled()) {
            Intent turnBTon = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnBTon, 1);
        }

        btnPaired.setOnClickListener(v -> pairedDevicesList());

    }

    private void pairedDevicesList() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, ANDROID_12_BLE_PERMISSIONS, 2);
        }

        Set<BluetoothDevice> pairedDevices = myBluetooth.getBondedDevices();
        ArrayList<String> list = new ArrayList<>();

        if ( pairedDevices.size() > 0 )
            for ( BluetoothDevice bt : pairedDevices) list.add(bt.getName() + "\n" + bt.getAddress());
        else
            Toast.makeText(getApplicationContext(), "No Paired Bluetooth Devices Found.", Toast.LENGTH_LONG).show();

        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
        deviceList.setAdapter(adapter);
        deviceList.setOnItemClickListener(myListClickListener);
    }

    private AdapterView.OnItemClickListener myListClickListener = (parent, view, position, id) -> {
        String info = ((TextView) view).getText().toString();
        String address = info.substring(info.length()-17);

        Intent i = new Intent(MainActivity.this, SerialInput.class);
        i.putExtra(EXTRA_ADDRESS, address);
        startActivity(i);
    };
}