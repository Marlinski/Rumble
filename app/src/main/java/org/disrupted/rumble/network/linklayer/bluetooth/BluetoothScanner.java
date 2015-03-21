/*
 * Copyright (C) 2014 Disrupted Systems
 *
 * This file is part of Rumble.
 *
 * Rumble is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Rumble is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.network.linklayer.bluetooth;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.*;

import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.events.BluetoothScanEnded;
import org.disrupted.rumble.network.events.BluetoothScanStarted;
import org.disrupted.rumble.network.exceptions.RecordNotFoundException;

import java.util.HashSet;
import java.lang.Math;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class BluetoothScanner implements SensorEventListener {

    private static final String TAG = "BluetoothScanner";

    private static final Object lock = new Object();
    private static BluetoothScanner instance;

    private enum ScanningState {
        SCANNING_ON, SCANNING_OFF
    };
    private ScanningState scanningState;

    private HashSet<BluetoothNeighbour>  btNeighborhood;

    /*
     * If another application is using the bluetooth (like Firechat) we have
     * to take extra care not to conflict with our discovery procedures.
     * waitingForDiscovery is used to trigger unsollicited DISCOVERY and in that case
     * will also listen for the result of this procedure.
     * todo: they might be race condition issues whenever a DISCOVERY starts at the same time
     */
    private boolean hasCallback = false;

    /*
     * Scanning consumes a lot of  resources, especially  battery.
     * in order  to save battery, the period  between two  successive
     * scan follow the trickle algorithm defined in the RFC 6206:
     *
     *         http://tools.ietf.org/html/rfc6206
     *
     * The idea is to increase the non-scanning period if the
     * neighborhood stay consistent between two scan.
     *
     * The trickle is increased first in a linear way, then in an exponential fashion
     *    - Slow Start  mode: in which we increase linearly the trickle timer
     *    - Exponential mode: in which we increase the timer exponentially
     */
    private static final double START_TRICKLE_TIMER   = 10000; // 10 seconds
    private static final double LINEAR_STEP           = 5000;  // 5 seconds
    private static final double EXPONENTIAL_THRESHOLD = 60000; // 1 minute
    private static final double IMAX_TRICKLE_TIMER    = 4;     // 2^4 = 16 minutes
    private int                 consistency = 1;
    private double              trickleTimer = START_TRICKLE_TIMER;
    private HashSet<BluetoothNeighbour>  lastTrickleState;
    private Handler             handler;

    /*
     * reset the trickle timer when phone is moving only if the timer is already long enough
     */
    private static final double RESET_TRICKLE_THRESHOLD = 30000;
    private SensorManager       mSensorManager;
    private Sensor              mAccelerometer;
    private long                lastUpdate;
    final float alpha = 0.8f;
    float[] gravity = new float[3];
    float[] linear_acceleration = new float[3];


    /*
     * little hack to prevent multiple unsollicited DISCOVERY_FINISH out of nowhere to
     * increase our trickle timer
     */
    private boolean             lastscan = false;

    private boolean registered;

    public static BluetoothScanner getInstance(NetworkCoordinator networkCoordinator) {
        synchronized (lock) {
            if(instance == null)
                return new BluetoothScanner();

            return instance;
        }
    }

    private BluetoothScanner() {
        btNeighborhood     = new HashSet<BluetoothNeighbour>();
        lastTrickleState = new HashSet<BluetoothNeighbour>();
        trickleTimer     = START_TRICKLE_TIMER;
        handler          = new Handler();
        scanningState    = ScanningState.SCANNING_OFF;
        hasCallback = false;
        registered = false;

        mSensorManager = (SensorManager) RumbleApplication.getContext().getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null){
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        } else {
            mAccelerometer = null;
        }
        for(int i = 0; i < 3; i++) {
            gravity[i] = 0;
            linear_acceleration[i] = 0;
        }
    }

    public void startDiscovery () {
        boolean force = false;
        if(scanningState == ScanningState.SCANNING_OFF) {
            scanningState = ScanningState.SCANNING_ON;
            force = true;
        }
        hasCallback = false;

        if(!registered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothDevice.ACTION_UUID);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
            RumbleApplication.getContext().registerReceiver(mReceiver, filter);

            if(mAccelerometer != null)
                mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);


            registered = true;
        }

        try{
            BluetoothAdapter mBluetoothAdapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());

            if (mBluetoothAdapter == null) {
                Log.d(TAG, "Bluetooth is not supported on this platform");
                return;
            }

            if( mBluetoothAdapter.isEnabled() ){
                if (mBluetoothAdapter.isDiscovering()) {
                    if(force) {
                        Log.d(TAG, "[!] we force our scanning for the first one");
                        mBluetoothAdapter.cancelDiscovery();
                        mBluetoothAdapter.startDiscovery();
                    }else {
                        Log.d(TAG, "[!] Scanning should start but we do nothing instead");
                        EventBus.getDefault().post(new BluetoothScanStarted());
                    }
                } else {
                    mBluetoothAdapter.startDiscovery();
                }
            }
        }
        catch (Exception e) {
            Log.d(TAG, "Exception:"+e.toString());
        }
    }

    public void stopDiscovery () {
        if(scanningState == ScanningState.SCANNING_OFF)
            return;

        Log.d(TAG, "[-] stop scanning procedure");
        handler.removeCallbacksAndMessages(null);
        if(registered) {
            RumbleApplication.getContext().unregisterReceiver(mReceiver);
            if(mAccelerometer != null)
                mSensorManager.unregisterListener(this);
        }
        registered = false;
        btNeighborhood.clear();
        lastTrickleState.clear();
        trickleTimer = START_TRICKLE_TIMER;
        scanningState    = ScanningState.SCANNING_OFF;
        EventBus.getDefault().post(new BluetoothScanEnded());
    }

    /*
     * when discovery is forced we reset the trickle timer and call for a new
     * scan (unless there is already one going on)
     */
    public void forceDiscovery() {
        resetTrickleTimer();
        if(hasCallback)
            startDiscovery();
    }

    public void destroy() {
        stopDiscovery();
    }

    private void resetTrickleTimer() {
        lastTrickleState.clear();
        trickleTimer = START_TRICKLE_TIMER;
    }

    /*
     * The trickle timer increase if the neighborhood is consistent
     * It is resetted if the neighborhood is inconsistant
     */
    private void recomputeTrickleTimer() {
        Log.d(TAG, "[+] recompute Trickle Timer");
        int inconsistency = 0;
        HashSet<BluetoothNeighbour> tmp = new HashSet<BluetoothNeighbour>();

        /*
         * first we detect for new neighbour inconsistency
         */
        for(BluetoothNeighbour neighbor : btNeighborhood) {
            tmp.add(neighbor);
            if(!lastTrickleState.remove(neighbor))
                inconsistency++;
        }

        /*
         * Then we detect for neighbour that disappeared inconsistency
         */
        for(BluetoothNeighbour neighbor : lastTrickleState) {
            try { NetworkCoordinator.getInstance().delNeighbor(neighbor.getLinkLayerAddress()); }
            catch (RecordNotFoundException ignore){  }
            inconsistency++;
        }
        lastTrickleState.clear();

        /*
         * then we save the state for the next trickle recomputation
         */
        lastTrickleState = tmp;

        if(inconsistency < consistency) {
            if(trickleTimer > EXPONENTIAL_THRESHOLD)
                trickleTimer = (((trickleTimer * 2) > (START_TRICKLE_TIMER * (Math.pow(2, IMAX_TRICKLE_TIMER)))) ? Math.pow(2, IMAX_TRICKLE_TIMER) : (trickleTimer * 2));
            else
                trickleTimer += LINEAR_STEP;
        } else {
            trickleTimer = START_TRICKLE_TIMER;
        }

        //todo: should we post a neighborhoodchange event when trickle is reset ?
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(scanningState == ScanningState.SCANNING_OFF)
                return;

            String action = intent.getAction();
            //Log.d(TAG, "[*] "+action);

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                if(scanningState == ScanningState.SCANNING_OFF)
                    return;
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getAddress() == null)
                    return;
                BluetoothNeighbour btPeerDevice = new BluetoothNeighbour(device.getAddress());
                if(btNeighborhood.add(btPeerDevice)){
                    Log.d(TAG, "[+] device "+device.getName()+" ["+device.getAddress()+"] discovered");
                    NetworkCoordinator.getInstance().newNeighbour(btPeerDevice, true);
                }
            }

            if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                if(scanningState == ScanningState.SCANNING_OFF)
                    return;
                if(!lastscan)
                    return;

                recomputeTrickleTimer();
                EventBus.getDefault().post(new BluetoothScanEnded());

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startDiscovery();
                    }
                }, (long) trickleTimer);
                lastscan = false;
                hasCallback = true;
            }

            /*
             * It is possible that another application (like firechat) is also sending
             * discovery intent to bluetooth. In that case we silently use the response
             * of these one
             */
            if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
                if(hasCallback) {
                    Log.d(TAG, "[!] started unsollicited Discovery");
                } else {
                    Log.d(TAG, "[+] started sollicited Discovery");
                }

                scanningState    = ScanningState.SCANNING_ON;
                btNeighborhood.clear();
                handler.removeCallbacksAndMessages(null);
                lastscan = true;
                hasCallback = false;

                EventBus.getDefault().post(new BluetoothScanStarted());
            }

            if(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)){
                int oldState = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, 0);
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE, 0);
                Log.d(TAG, "[+] Scan Mode Changed "+oldState+" -> "+newState);
            }

        }
    };


    @Override
    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();
        if ((curTime - lastUpdate) > 100) {
            lastUpdate = curTime;

            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

            float x =  event.values[0] - gravity[0];
            float y = event.values[1] - gravity[1];
            float z = event.values[2] - gravity[2];

            float speed = Math.abs(x+y+z-linear_acceleration[0]-linear_acceleration[1]-linear_acceleration[2]);

            if(speed > 2) {
                if(trickleTimer > RESET_TRICKLE_THRESHOLD) {
                    Log.d(TAG, "[!] phone moved, reset trickle timer");
                    forceDiscovery();
                }
            }

            linear_acceleration[0] = x;
            linear_acceleration[1] = y;
            linear_acceleration[2] = z;

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
