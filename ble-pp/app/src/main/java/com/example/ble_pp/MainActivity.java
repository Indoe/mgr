package com.example.ble_pp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
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

import com.google.android.gms.common.api.GoogleApiClient;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

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
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    advertisingButton.setVisibility(View.INVISIBLE);
                }, 2 * 60 * 1000); // minuty * sekundy * milisekundy
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
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setTimeout(2 * 60 * 1000)
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

    public void sendLocation() {
        location = bluetoothGatt
                .getService(uuid_service.getUuid())
                .getCharacteristic(uuid_location.getUuid());

        outputStream = getLocation();

        if (outputStream.size() <= 0) {
            Log.e(TAG, "outputStream.size() in sendLocation i 0");
            return;
        }

        location.setValue(outputStream.toByteArray());
        bluetoothGatt.writeCharacteristic(location);
    }


    ///////////////////////////////////////


    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.e(TAG, "Connected to GATT client. Attempting to start service discovery");

                if (status == BluetoothGatt.STATE_CONNECTED) {
                    Log.e(TAG, "bluetoothGattCallback onConnectionStateChange STATE_CONNECTED");
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        gatt.discoverServices();
                        Log.e(TAG, "Discover Services started ");
                    }
                });

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e(TAG, "Disconnected from GATT client");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.e(TAG, "onServicesDiscovered received: " + status);
            gattConnected = true;
//            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                service = gatt.getService(uuid_service.getUuid());
                if (service != null) {
                    characteristic = service.getCharacteristic(uuid_characteristic.getUuid());
                    if (characteristic != null) {
                        gatt.setCharacteristicNotification(characteristic, true);
                        descriptor = characteristic.getDescriptor(uuid_descriptor.getUuid());
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                    }
                }

            } else
                Log.e(TAG, "onServicesDiscovered received: " + status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.e(TAG, "onDescriptorWrite received: " + status);
//            super.onDescriptorWrite(gatt, descriptor, status);
            if (uuid_descriptor.getUuid().equals(descriptor.getUuid())) {
                characteristic = gatt.getService(uuid_service.getUuid()).getCharacteristic(uuid_characteristic.getUuid());
                gatt.readCharacteristic(characteristic);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.e(TAG, "onCharacteristicRead received: " + status);
//            super.onCharacteristicRead(gatt, characteristic, status);
            readCounterCharacteristic(characteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.e(TAG, "onCharacteristicChanged: " + Arrays.toString(characteristic.getValue()));
//            super.onCharacteristicChanged(gatt, characteristic);
            readCounterCharacteristic(characteristic);
        }

        //tu odczytujemy  i akutalizujemy lokalizację!
        private void readCounterCharacteristic(BluetoothGattCharacteristic characteristic) {
            Log.e(TAG, "readCounterCharacteristic: " + Arrays.toString(characteristic.getValue()));
            if (uuid_characteristic.getUuid().equals(characteristic.getUuid())) {

                data = characteristic.getValue(); //pobranie lokalizacji

//                int value = Ints.fromByteArray(data);     //aktualizajca lokalizacji
//                mListener.onCounterRead(value);
            }
        }

    }; //# bluetoothGattCallback #

    private void discoverServices() {

        if (!gattConnected) { //just a boolean
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    bluetoothGatt.discoverServices();
                }
            });
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    discoverServices();
                }
            }, 5000);
        }
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
//            super.onConnectionStateChange(device, status, newState);
            Log.e(TAG, "onConnectionStateChange status: " + status + ", newState: " + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.e(TAG, "BluetoothDevice [server] CONNECTED: " + device);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e(TAG, "BluetoothDevice [server] DISCONNECTED: " + device);
                // Remove device from any active subscriptions
                registeredDevices.remove(device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
//            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (uuid_characteristic.getUuid().equals(characteristic.getUuid())) {

//                Log.e(TAG, "characteristic " + myAdapter.byteArrayToHexString(characteristic.getValue()));
//                Log.e(TAG, "descriptor " + Arrays.toString(characteristic.getDescriptor(uuid_descriptor.getUuid()).getValue()));

                //zwrocic liczbe ktora jest  zapisana
//                byte[] data = outputStream.toByteArray();

//                data = getLocation().toByteArray();

                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, characteristic.getValue());
            } else {
                Log.e(TAG, "invalid characteristic");
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
//            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            if (uuid_location.getUuid().equals(characteristic.getUuid())) {

                //dostaje lokalizacje i coś z nią robię
                myAdapter.addLocation(device, value);

                //aktualizujemy tu lokalizacje i wysylamy wszystkim info o jej zmianie
//                notifyRegisteredDevices(value);                                       //gdy client wysle swoja lokalizacje
            } else {
                Log.e(TAG, "Invalid Characteristic Write: " + characteristic.getUuid());
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
//            super.onDescriptorReadRequest(device, requestId, offset, descriptor);

            if (uuid_descriptor.getUuid().equals(descriptor.getUuid())) {
                Log.e(TAG, "Config descriptor read request");
                byte[] returnValue;
                if (registeredDevices.contains(device)) {
                    returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                } else {
                    returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                }
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, returnValue);
            }
            /*else if (DESCRIPTOR_USER_DESC.equals(descriptor.getUuid())) {
                Log.d(TAG, "User description descriptor read request");
                byte[] returnValue = AwesomenessProfile.getUserDescription(descriptor.getCharacteristic().getUuid());
                returnValue = Arrays.copyOfRange(returnValue, offset, returnValue.length);
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, returnValue);
            }*/
            else {
                Log.e(TAG, "Unknown descriptor read request");
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
//            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);

            if (uuid_descriptor.getUuid().equals(descriptor.getUuid())) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    //nie wykonuje sie ;C
                    registeredDevices.add(device);
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    registeredDevices.remove(device);
                }

                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                } else {
                    Log.e(TAG, "Unknown descriptor write request");
                    if (responseNeeded) {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                    }
                }
            }
        }


    }; //# gattServerCallback #

    private BluetoothGattService createService() {
        service = new BluetoothGattService(uuid_service.getUuid(), BluetoothGattService.SERVICE_TYPE_PRIMARY);

        characteristic = new BluetoothGattCharacteristic(
                uuid_characteristic.getUuid(),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        descriptor = new BluetoothGattDescriptor(
                uuid_descriptor.getUuid(),
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE
        );
        characteristic.addDescriptor(descriptor);

        location = new BluetoothGattCharacteristic(
                uuid_location.getUuid(),
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ);

        service.addCharacteristic(location);
        service.addCharacteristic(characteristic);

        return service;
    }

    public void startServer() {
        gattServer = btManager.openGattServer(this, gattServerCallback);
        gattServer.addService(createService());
        Log.e(TAG, "starting server");
    }

    public void stopServer() {
        gattServer.close();
    }

    public void startClient(ScanResult result) {
        bluetoothDevice = btAdapter.getRemoteDevice(result.getDevice().getAddress()); //get address
        bluetoothGatt = bluetoothDevice.connectGatt(this, false, bluetoothGattCallback);
        if (bluetoothGatt == null) {
            Log.e(TAG, "Unable to create GATT client");
            return;
        }
//        discoverServices();
        bluetoothGatt.getServices();
//        sendLocation(); //is wrong
    }

    public void stopClient() {
        bluetoothGatt.close();
    }

    public void notifyRegisteredDevices(byte[] data) {
        //wykonuje sie gdy klient wysle lokalizacje
        if (registeredDevices.isEmpty()) {
            Log.e(TAG, "No subscribers registered");
            return;
        }

        Log.e(TAG, "Sending update to " + registeredDevices.size() + " subscribers");
        for (BluetoothDevice device : registeredDevices) {
            BluetoothGattCharacteristic counterCharacteristic = gattServer
                    .getService(uuid_service.getUuid())
                    .getCharacteristic(uuid_characteristic.getUuid());

            sendLocation();

            //get location from device
            data = outputStream.toByteArray();
            counterCharacteristic.setValue(data);

            gattServer.notifyCharacteristicChanged(device, counterCharacteristic, false);
        }
    }

}
