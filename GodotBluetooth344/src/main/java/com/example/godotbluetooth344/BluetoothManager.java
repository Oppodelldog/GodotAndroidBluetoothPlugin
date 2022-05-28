package com.example.godotbluetooth344;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BluetoothManager extends GodotPlugin {

    // General
    private Context context;
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    private LocationManager locationManager;
    private Handler handler = new Handler();
    private static final long SCAN_PERIOD = 10000;

    // Specific
    private boolean scanning = false;
    private Map<String, ScanResult> devices = new HashMap<String, ScanResult>();

    // Permissions related functions
    public boolean hasLocationPermissions() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            return false;
        } else {
            return true;
        }
    }

    public BluetoothManager(Godot godot) {

        super(godot);

        // Get the context
        this.context = getActivity().getApplicationContext();

        // Get the location manager
        this.locationManager = (LocationManager)this.context.getSystemService(Context.LOCATION_SERVICE);

        // Register the listener to the Bluetooth Status
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(mReceiver, filter);

        // Register the listener to the Location Status
        filter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        context.registerReceiver(mGpsSwitchStateReceiver, filter);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotBluetooth344";
    }

    @SuppressWarnings("deprecation")
    @NonNull
    @Override
    public List<String> getPluginMethods() {
        return Arrays.asList("sendDebugSignal", "bluetoothStatus", "scan", "hasLocationPermissions", "locationStatus");
    }

    public void sendDebugSignal(String s) {

        emitSignal("_on_debug_message", s);
    }

    public void sendNewDevice(ScanResult newDevice) {

        org.godotengine.godot.Dictionary deviceData = new org.godotengine.godot.Dictionary();

        deviceData.put("name", newDevice.getScanRecord().getDeviceName());
        deviceData.put("address", newDevice.getDevice().getAddress());
        deviceData.put("rssi", newDevice.getRssi());

        emitSignal("_on_device_found", deviceData);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo>  signals = new ArraySet<>();

        signals.add(new SignalInfo("_on_debug_message", String.class));
        signals.add(new SignalInfo("_on_device_found", org.godotengine.godot.Dictionary.class));
        signals.add(new SignalInfo("_on_bluetooth_status_change", String.class));
        signals.add(new SignalInfo("_on_location_status_change", String.class));

        return signals;
    }

    @SuppressLint("MissingPermission")
    public void scan() {

        if (hasLocationPermissions()) {

            if (!scanning) {

                // Stops scanning after a predefined scan period.
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        sendDebugSignal("Stopping scan");

                        bluetoothLeScanner.stopScan(leScanCallback);
                    }
                }, SCAN_PERIOD);

                sendDebugSignal("Start scan");

                scanning = true;
                bluetoothLeScanner.startScan(leScanCallback);
            } else {
                scanning = false;
                bluetoothLeScanner.stopScan(leScanCallback);
            }
        } else {
            sendDebugSignal("Cannot start a scan because you do not have location permissions");
        }
    }

    private ScanCallback leScanCallback =
            new ScanCallback() {

                @Override
                public void onScanResult(int callbackType, ScanResult result) {

                    // We are only interested in devices with name
                    if (result != null && result.getDevice() != null && result.getDevice().getAddress() != null && result.getScanRecord().getDeviceName() != null) {

                        if (!devices.containsKey(result.getDevice().getAddress())) {

                            devices.put(result.getDevice().getAddress(), result);
                            sendNewDevice(result);
                        }
                    }
                }
            };

    // Status functions
    public boolean bluetoothStatus() {

        return mBluetoothAdapter.isEnabled();
    }

    public boolean locationStatus() {

        Boolean gps_enabled = false;
        Boolean network_enabled = false;

        try {
            gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
        }

        try {
            network_enabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
        }

        if (!gps_enabled && !network_enabled) {
            return false;
        }
        return true;
    }

    // This monitors the bluetooth status
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        emitSignal("_on_bluetooth_status_change", "off");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        emitSignal("_on_bluetooth_status_change", "turning_off");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        emitSignal("_on_bluetooth_status_change", "on");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        emitSignal("_on_bluetooth_status_change", "turning_on");
                        break;
                }
            }
        }
    };

    // This monitors the location status
    private BroadcastReceiver mGpsSwitchStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {

                boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                if (isGpsEnabled || isNetworkEnabled) {
                    emitSignal("_on_location_status_change", "on");
                } else {
                    emitSignal("_on_location_status_change", "off");
                }
            }
        }
    };

}

/*


Received F3:60:26:8A:AD:78
Received Take

 */