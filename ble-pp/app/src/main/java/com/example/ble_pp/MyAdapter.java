package com.example.ble_pp;

import android.annotation.SuppressLint;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {

    private static final String TAG = "MyAdapter: ";
    private static DecimalFormat df = new DecimalFormat("0.00");
    public static final ParcelUuid uuid = ParcelUuid.fromString("0000feaa-0000-1000-8000-00805f9b34fb");
    private static Comparator<ScanResult> SORTING_COMPARATOR = (lhs, rhs) ->
            lhs.getDevice().getAddress().compareTo(rhs.getDevice().getAddress());
    List<ScanResult> scanResultList;
    Context context;
    String hexToString;
    byte[] data;
    double distance;
    String[] splited;

    public MyAdapter(Context context, List<ScanResult> scanResultList) {
        super();
        this.context = context;
        this.scanResultList = scanResultList;
    }


    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.row_list, viewGroup, false);
        return new MyViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(MyViewHolder myViewHolder, int position) {

        myViewHolder.deviceName.setText(scanResultList.get(position).getDevice().getName());

        myViewHolder.macAddress.setText(scanResultList.get(position).getDevice().getAddress());

        data = scanResultList.get(position).getScanRecord().getServiceData(uuid);
        hexToString = new String(data, StandardCharsets.UTF_8);
        splited = hexToString.split("\\s+");
        myViewHolder.data_service.setText("lat: " + splited[0] + " long: " + splited[1]);

        distance = calculateDistance(scanResultList.get(position).getRssi(), scanResultList.get(position).getTxPower());
        myViewHolder.rssi.setText(df.format(distance) + " m");
    }

    @Override
    public int getItemCount() {
        return scanResultList.size();
    }

    public void addScanResult(ScanResult result) {
        for (int position = 0; position < scanResultList.size(); position++) {
            if (scanResultList.get(position).getDevice().getAddress().equals(result.getDevice().getAddress())) {
                scanResultList.set(position, result);
                notifyItemChanged(position);
                return;
            }
        }
        scanResultList.add(result);
        Collections.sort(scanResultList, SORTING_COMPARATOR);
        notifyDataSetChanged();

        convertData();
    }

    public void convertData() {
        double[][] positions = new double[scanResultList.size()][2];
        for (int position = 0; position < scanResultList.size(); position++) {

            data = scanResultList.get(position).getScanRecord().getServiceData(uuid);
            hexToString = new String(data, StandardCharsets.UTF_8);
            splited = hexToString.split("\\s+");

            double latitude = Double.parseDouble(splited[0].replace(",","."));
            double longitude = Double.parseDouble(splited[1].replace(",","."));

            positions[position] = new double[]{latitude, longitude};

        }
    }

    public double calculateDistance(int rssi, int txPower) {
        if (rssi == 0)
            return -1.0;

        if (txPower == 127)
            txPower = -59;

        double ratio = rssi * 1.0 / txPower;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        } else {
            double distance = (0.89976) * Math.pow(ratio, 7.7095) + 0.111; //badania
            return distance;
        }
    }

    //correctly converting string to byte
    public byte[] toByteArray(String hexString) {
        int len = hexString.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return bytes;
    }

    //check correctly formatted service data (max 31 bytes)
    public String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length);
        for(byte byteChar : bytes) {
            sb.append(String.format("%02X ", byteChar));
        }
        return sb.toString();
    }

    public void clearScanResult() {
        scanResultList.clear();
        notifyDataSetChanged();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {

        private final TextView deviceName;
        private final TextView macAddress;
        private final TextView rssi;
        private final TextView data_service;

        public MyViewHolder(View itemView) {
            super(itemView);
            deviceName = (TextView) itemView.findViewById(R.id.device_name);
            macAddress = (TextView) itemView.findViewById(R.id.mac_address);
            rssi = (TextView) itemView.findViewById(R.id.signal_strength);
            data_service = (TextView) itemView.findViewById(R.id.data_service);
        }
    }
}
