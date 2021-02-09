package com.example.ble_pp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    BluetoothGattServer gattServer;
    BluetoothDevice bluetoothDevice;
    BluetoothGatt bluetoothGatt;

    Button startScanningButton;
    Button stopScanningButton;
    Button advertisingButton;
    Button trilateriationButton;

    RecyclerView listRecyclerView;

    FusedLocationProviderClient fusedLocationClient;
    LocationRequest locationRequest;
    LocationManager locationManager;
    Location gpsLoc;
    AlertDialog.Builder builder;

    BluetoothGattCharacteristic location;
    BluetoothGattCharacteristic characteristic;
    BluetoothGattDescriptor descriptor;
    BluetoothGattService service;

    private final List<ScanResult> scanResultList = new ArrayList<>();
    private final List<ScanFilter> scanFilterList = new ArrayList<>();
    private final Set<BluetoothDevice> registeredDevices = new HashSet<>();

    private static final DecimalFormat df = new DecimalFormat("#.#####");                              //7 miejsc //badania!
    private static final DecimalFormat dftemp = new DecimalFormat("#.#####");
    public static final ParcelUuid uuid = ParcelUuid.fromString("0000c0fe-0000-1000-8000-00805f9b34fb");

    public static final ParcelUuid uuid_characteristic = ParcelUuid.fromString("3032faaa-426b-7261-5074-72616d536557b");
    public static final ParcelUuid uuid_descriptor = ParcelUuid.fromString("3032fbbb-426b-7261-5074-72616d536557b");
    public static final ParcelUuid uuid_service = ParcelUuid.fromString("3032fefe-426b-7261-5074-72616d536557b");
    public static final ParcelUuid uuid_location = ParcelUuid.fromString("3032fafa-426b-7261-5074-72616d536557b");

    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_FINE_LOCATION_CODE = 100;
    private static final int PERMISSION_BACKGROUND_LOCATION_CODE = 101;
    private static final int PERMISSION_COARSE_LOCATION_CODE = 102;

    String latitude;
    String longitude;
    double tempLat;
    double tempLon;
    byte[] data;
    double[] centroid;
    boolean gattConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();
        btAdvertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();

        outputStream = new ByteArrayOutputStream();
        listRecyclerView = findViewById(R.id.RecyclerViewList);
        myAdapter = new MyAdapter(this, scanResultList, registeredDevices);
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
                Snackbar mySnackbar = Snackbar.make(findViewById(R.id.RecyclerViewList), "Start advertising...", Snackbar.LENGTH_SHORT);
                mySnackbar.show();
                startAdvertising();
//                new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                    advertisingButton.setVisibility(View.INVISIBLE);
//                }, 2 * 60 * 1000); // minuty * sekundy * milisekundy
            }
        });

        trilateriationButton = (Button) findViewById(R.id.Trilateration);
        trilateriationButton.setVisibility(View.INVISIBLE);
        trilateriationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!scanResultList.isEmpty()) {
                    answer();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        trilateriationButton.setVisibility(View.INVISIBLE);
                    }, 500);
                }
            }
        });

        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, PERMISSION_FINE_LOCATION_CODE);
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
        } else {
            requestLocation();
        }
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
//            super.onScanResult(callbackType, result);
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
    }; //# leScanCallback #

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
    }; //# leAdvertiseCallback #

    private final LocationCallback leLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
//            super.onLocationResult(locationResult);
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
    }; //# leLocationCallback #

    @SuppressLint("HardwareIds")
    public void startAdvertising() {
        btAdvertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
//                .setTimeout(2 * 60 * 1000)
                .setConnectable(true)
                .build();

        outputStream = getLocation();

        btAdvertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(uuid)
                .addServiceData(uuid, outputStream.toByteArray())
                .build();

        Log.e(TAG, "btAdvertiseData " + btAdvertiseData.getServiceData().toString());

        runOnUiThread(() -> {
            btAdvertiser.startAdvertising(btAdvertiseSettings, btAdvertiseData, leAdvertiseCallback);
        });
    }

    public void stopAdvertising() {
        btAdvertiser.stopAdvertising(leAdvertiseCallback);
    }

    public void startScanning() {
        scanFilter = new ScanFilter.Builder()
                .setServiceUuid(uuid)
                .build();
        scanFilterList.add(scanFilter);
        scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();

        Snackbar mySnackbar = Snackbar.make(findViewById(R.id.RecyclerViewList), "Start scanning...", Snackbar.LENGTH_SHORT);
        mySnackbar.show();

        myAdapter.clearScanResult();
        startScanningButton.setVisibility(View.INVISIBLE);
        stopScanningButton.setVisibility(View.VISIBLE);
        trilateriationButton.setVisibility(View.INVISIBLE);

        runOnUiThread(() -> {
            btScanner.startScan(scanFilterList, scanSettings, leScanCallback);
        });
    }

    public void stopScanning() {
        fusedLocationClient.removeLocationUpdates(leLocationCallback);

        startScanningButton.setVisibility(View.VISIBLE);
        stopScanningButton.setVisibility(View.INVISIBLE);
        trilateriationButton.setVisibility(View.VISIBLE);

        Snackbar mySnackbar = Snackbar.make(findViewById(R.id.RecyclerViewList), "Stop scanning...", Snackbar.LENGTH_SHORT);
        mySnackbar.show();

        runOnUiThread(() -> {
            btScanner.stopScan(leScanCallback);
        });

    }

    public void answer() {
        myAdapter.convertDataToStringLatLong();

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            latitude = df.format(location.getLatitude());
            longitude = df.format(location.getLongitude());
            Log.e(TAG, "location" + location.toString());
            Log.e(TAG, "fusedLocationClient -> lat: " + location.getLatitude() + " long: " + location.getLongitude() + "\n");
        });

        if (myAdapter.locationWithDistanceList.size() >= 2) {
            centroid = myAdapter.trilateration(myAdapter.locationWithDistanceList);

            //convert answer from meters to decimal degree
            centroid[0] = myAdapter.meters2decimalDegree_latitude(centroid[0]);
            centroid[1] = myAdapter.meters2decimalDegree_longitude(centroid[1]);

            tempLat = Double.parseDouble(latitude.replace(",", "."));
            tempLon = Double.parseDouble(longitude.replace(",", "."));

            String text = df.format(centroid[0]) + "    " + df.format(centroid[1]) + "\n" + "\n"
                    + "Real location:\n" + latitude + "    " + longitude + "\n" + "\n"
                    + "measurement error: " + "\n"
                    + "lat " + dftemp.format(Math.abs(centroid[0] - tempLat) / tempLat * 100) + "%" + "\n"
                    + "lon " + dftemp.format(Math.abs(centroid[1] - tempLon) / tempLon * 100) + "%";

            builder = new AlertDialog.Builder(this);
            builder.setTitle("Trilateration answer")
                    .setMessage(text);
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }

    public void requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "permission not granted");
        } else {
            locationRequest = LocationRequest.create();
            locationRequest
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setInterval(1500);

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            fusedLocationClient.requestLocationUpdates(locationRequest, leLocationCallback, Looper.getMainLooper());
        }
    }

    public ByteArrayOutputStream getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "permission not granted");
        } else {
            fusedLocationClient.getLocationAvailability().
                    addOnSuccessListener(this, locationAvailability -> {
//                        Log.e(TAG, "If true fusedLocationClient, if false locationManager: " + locationAvailability.isLocationAvailable() + "\n");
                        if (locationAvailability.isLocationAvailable()) {
                            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                                if (location != null) {
                                    latitude = df.format(location.getLatitude());
                                    longitude = df.format(location.getLongitude());
                                    Log.e(TAG, "location" + location.toString());
                                    Log.e(TAG, "fusedLocationClient -> lat: " + location.getLatitude() + " long: " + location.getLongitude() + "\n");
                                }
                            });
                        }
                    });
        }

        if (latitude == null || longitude == null) {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            latitude = df.format(gpsLoc.getLatitude());
            longitude = df.format(gpsLoc.getLongitude());
            Log.e(TAG, "locationManager -> lat: " + latitude + " long: " + longitude + "\n");
        }

        try {
            outputStream.write(latitude.getBytes());
            outputStream.write(myAdapter.toByteArray("20")); //spacebar
            outputStream.write(longitude.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return outputStream;
    }
}