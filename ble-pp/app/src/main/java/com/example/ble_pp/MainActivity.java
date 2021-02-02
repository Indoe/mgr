package com.example.ble_pp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity: ";
    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    BluetoothLeScanner btScanner;
    MyAdapter myAdapter;

    BluetoothLeAdvertiser btAdvertiser;
    AdvertiseSettings btAdvertiseSettings;
    AdvertiseData btAdvertiseData;
    ByteArrayOutputStream outputStream;
    ScanFilter scanFilter;
    ScanSettings scanSettings;

    Button startScanningButton;
    Button stopScanningButton;
    Button advertisingButton;
    RecyclerView listRecyclerView;

    FusedLocationProviderClient fusedLocationClient;
    Task<Location> locationTask;
    LocationRequest locationRequest;
    LocationManager locationManager;
    Location gpsLoc;

    private final List<ScanResult> scanResultList = new ArrayList<>();
    private final List<ScanFilter> scanFilterList = new ArrayList<>();

    private static final DecimalFormat df = new DecimalFormat("#.##");                              //badania!
    public static final ParcelUuid uuid = ParcelUuid.fromString("0000feaa-0000-1000-8000-00805f9b34fb");

    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_FINE_LOCATION_CODE = 100;
    private static final int PERMISSION_BACKGROUND_LOCATION_CODE = 101;
    private static final int PERMISSION_COARSE_LOCATION_CODE = 102;

    String latitude;
    String longitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();
        btAdvertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();

        listRecyclerView = findViewById(R.id.RecyclerViewList);
        myAdapter = new MyAdapter(this, scanResultList);
        listRecyclerView.setAdapter(myAdapter);
        listRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        startScanningButton = (Button) findViewById(R.id.StartScanButton);
        startScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startScanning();
            }
        });

        stopScanningButton = (Button) findViewById(R.id.StopScanButton);
        stopScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopScanning();
            }
        });
        stopScanningButton.setVisibility(View.INVISIBLE);

        advertisingButton = (Button) findViewById(R.id.AdvertisingButton);
        advertisingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startAdvertising();

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        fusedLocationClient.removeLocationUpdates(leLocationCallback);
                        Toast.makeText(getApplicationContext(), "Advertising is stopped!", Toast.LENGTH_LONG).show();
                    }
                }, 60000);
            }
        });

        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, PERMISSION_FINE_LOCATION_CODE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION, PERMISSION_BACKGROUND_LOCATION_CODE);
        }
        checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, PERMISSION_COARSE_LOCATION_CODE);
    } // onCreate

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_FINE_LOCATION_CODE:
            case PERMISSION_BACKGROUND_LOCATION_CODE:
            case PERMISSION_COARSE_LOCATION_CODE:
            default:
        }
    }

    public void checkPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission)
                == PackageManager.PERMISSION_DENIED) {
            // Requesting the permission
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{permission},
                    requestCode);
        }
        else
            requestLocation();
    }


    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            if (result == null) {
                return;
            }
            myAdapter.addScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.e(TAG, "onBatchResults");
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(getApplicationContext(), "Scan fail", Toast.LENGTH_SHORT).show();
        }
    };

    private final AdvertiseCallback leAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.e(TAG, "Advertising onStartFailure: " + errorCode);
        }
    };

    private final LocationCallback leLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            if (locationResult == null) {
                return;
            }
            for (Location location : locationResult.getLocations()) {
                if (location != null) {
                    latitude = df.format(location.getLatitude());
                    longitude = df.format(location.getLongitude());
                }
            }
        }

        @Override
        public void onLocationAvailability(LocationAvailability locationAvailability) {
            super.onLocationAvailability(locationAvailability);
        }
    };

    public void startAdvertising() {
        Toast.makeText(getApplicationContext(), "Start advertising for 1 minute", Toast.LENGTH_SHORT).show();

        btAdvertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setTimeout(60000) //1min
                .setConnectable(true)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        getLocation();

        if(latitude.isEmpty() || longitude.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Get Location FAIL!", Toast.LENGTH_SHORT).show();
            return;
        }

        outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(latitude.getBytes());
            outputStream.write(myAdapter.toByteArray("20")); //spacebar
            outputStream.write(longitude.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        btAdvertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(uuid)
                .addServiceData(uuid, outputStream.toByteArray())
                .build();

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btAdvertiser.startAdvertising(btAdvertiseSettings, btAdvertiseData, leAdvertiseCallback);
            }
        });
    }

    public void startScanning() {
        scanFilter = new ScanFilter.Builder()
                .setServiceUuid(uuid)
                .build();
        scanFilterList.add(scanFilter);
        scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();
        Toast.makeText(getApplicationContext(), "Start scanning", Toast.LENGTH_SHORT).show();
        myAdapter.clearScanResult();
        startScanningButton.setVisibility(View.INVISIBLE);
        stopScanningButton.setVisibility(View.VISIBLE);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.startScan(scanFilterList, scanSettings, leScanCallback);
            }
        });
    }

    public void stopScanning() {
        myAdapter.convertDataToStringLatLong();
        Toast.makeText(getApplicationContext(), "Stop scanning", Toast.LENGTH_SHORT).show();
        startScanningButton.setVisibility(View.VISIBLE);
        stopScanningButton.setVisibility(View.INVISIBLE);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.stopScan(leScanCallback);
            }
        });
    }

    public void requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        else {
            locationRequest = LocationRequest.create();
            locationRequest
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setInterval(10 * 1000); //1min

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            fusedLocationClient.requestLocationUpdates(locationRequest, leLocationCallback, Looper.getMainLooper());
        }
    }
    public void getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        else{
            fusedLocationClient.getLocationAvailability()
                    .addOnSuccessListener(this, locationAvailability -> {
                        Log.e(TAG, "Is location available: " + locationAvailability.isLocationAvailable() + "\n");
                    });

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                latitude = df.format(location.getLatitude());
                longitude = df.format(location.getLongitude());
                Log.e(TAG, "fusedLocationClient -> lat: " + latitude + " long: " + longitude + "\n");
            });

            fusedLocationClient.getLastLocation().addOnFailureListener(location -> {
                locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
                gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                latitude = df.format(gpsLoc.getLatitude());
                longitude = df.format(gpsLoc.getLongitude());
                Log.e(TAG, "locationManager -> lat: " + latitude + " long: " + longitude + "\n");
            });
        }

    }
}
