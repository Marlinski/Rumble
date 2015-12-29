/*
 * Copyright (C) 2014 Lucien Loiseau
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

import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.events.ScannerNeighbourSensed;
import org.disrupted.rumble.network.events.ScannerNeighbourTimeout;
import org.disrupted.rumble.network.linklayer.events.BluetoothScanEnded;
import org.disrupted.rumble.network.linklayer.events.BluetoothScanStarted;
import org.disrupted.rumble.network.events.ChannelConnected;
import org.disrupted.rumble.network.events.ChannelDisconnected;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.Scanner;

import java.util.HashSet;
import java.lang.Math;
import java.util.concurrent.locks.ReentrantLock;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class BluetoothScanner extends HandlerThread implements SensorEventListener, Scanner {

    private static final String TAG = "BluetoothScanner";

    private static final ReentrantLock lock = new ReentrantLock();
    private static BluetoothScanner instance;
    private static int openedSocket;

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
     *    - Beta Mode: in which we only scan rarely when connected to a device
     *
     *  Whenever the neighborhood change between to scan, a change is the detection of a new
     *  neighbour or a previous neighbour being unreachable. If the number of such inconsistency
     *  is more than a certain threshold, we reset the trickle timer.
     */
    private enum ScanningState {
        SCANNING_OFF, SCANNING_IDLE, SCANNING_SCHEDULED, SCANNING_IN_PROGRESS
    }
    private ScanningState scanningState;
    private HashSet<BluetoothNeighbour>  btNeighborhood;

    private static final double SCANNING_TIMEOUT        = 15000; // max scanning time 15 seconds
    private static final double START_TRICKLE_TIMER     = 10000; // 10 seconds
    private static final double BETA_TRICKLE_TIMER      = 60000; // 1 minute
    private static final double LINEAR_STEP             = 5000;  // 5 seconds
    private static final double EXPONENTIAL_THRESHOLD   = 60000; // 1 minute
    private static final double IMAX_TRICKLE_TIMER      = 4;     // 2^4 = 16 minutes
    private static final int    INCONSISTENCY_THRESHOLD = 2;

    private double              trickleTimer = START_TRICKLE_TIMER;
    private HashSet<BluetoothNeighbour>  lastTrickleState;
    private boolean             betamode;

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

    private boolean registered;
    private boolean sensorregistered;

    public static BluetoothScanner getInstance() {
        try {
            lock.lock();
            if(instance == null)
                instance = new BluetoothScanner();

            return instance;
        } finally {
            lock.unlock();
        }
    }

    private BluetoothScanner() {
        super(TAG);
        super.start();
        btNeighborhood     = new HashSet<BluetoothNeighbour>();
        lastTrickleState = new HashSet<BluetoothNeighbour>();
        trickleTimer     = START_TRICKLE_TIMER;
        scanningState    = ScanningState.SCANNING_OFF;
        registered   = false;
        betamode     = false;
        openedSocket = 0;

        /*
         * using the sensor on google nexus s has supposedly broken the bluetooth stack, stucking it
         * in a while loop at boot, had to factory reset
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
        */
    }

    @Override
    protected void finalize() throws Throwable {
        super.quit();
        super.finalize();
    }

    @Override
    public void startScanner() {
        try {
            lock.lock();
            if (!scanningState.equals(ScanningState.SCANNING_OFF))
                return;
            scanningState = ScanningState.SCANNING_IDLE;

            Log.d(TAG, "--- Bluetooth Scanner started ---");

            if (!registered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothDevice.ACTION_FOUND);
                filter.addAction(BluetoothDevice.ACTION_UUID);
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);

                handler = new Handler(getLooper());
                RumbleApplication.getContext().registerReceiver(mReceiver, filter, null, handler);
                registered = true;
            }
            EventBus.getDefault().register(this);
        } finally {
            lock.unlock();
        }
        performScan(true);
    }

    public void stopScanner() {
        try {
            lock.lock();
            if (scanningState.equals(ScanningState.SCANNING_OFF))
                return;

            BluetoothAdapter mBluetoothAdapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
            if (mBluetoothAdapter != null) {
                if (mBluetoothAdapter.isEnabled() && mBluetoothAdapter.isDiscovering())
                    mBluetoothAdapter.cancelDiscovery();
            }

            switch (scanningState) {
                case SCANNING_IDLE:
                    break;
                case SCANNING_SCHEDULED:
                    handler.removeCallbacks(scanScheduleFires);
                    break;
                case SCANNING_IN_PROGRESS:
                    handler.removeCallbacks(scanTimeoutFires);
                    EventBus.getDefault().post(new BluetoothScanEnded());
                    break;
            }
            scanningState = ScanningState.SCANNING_OFF;

            Log.d(TAG, "--- Bluetooth Scanner stopped ---");

            if (EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);

            if (registered) {
                RumbleApplication.getContext().unregisterReceiver(mReceiver);
                registered = false;
            }

            btNeighborhood.clear();
            resetTrickleTimer();
            /*
            if((mAccelerometer != null) && sensorregistered) {
                mSensorManager.unregisterListener(this);
                sensorregistered = false;
            }
            */
        } finally {
            lock.unlock();
        }
    }

    private void performScan(boolean force) {
        try {
            lock.lock();

            switch (scanningState) {
                case SCANNING_OFF:
                    return;
                case SCANNING_IN_PROGRESS:
                    return;
                case SCANNING_SCHEDULED:
                    if(!force)
                        return;
                    else {
                        handler.removeCallbacks(scanScheduleFires);
                        scanningState = ScanningState.SCANNING_IDLE;
                    }
                case SCANNING_IDLE:
                    break;
            }

            /*
            if((mAccelerometer != null) && !sensorregistered) {
                mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                sensorregistered = true;
            }
            */
            BluetoothAdapter mBluetoothAdapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());

            if (mBluetoothAdapter == null) {
                Log.d(TAG, "Bluetooth is not supported on this platform");
                return;
            }

            if( mBluetoothAdapter.isEnabled() ){
                btNeighborhood.clear();

                /*
                 * it is possible that the device is already discovering if another app
                 * ran a discovery procedure
                 */
                if (!mBluetoothAdapter.isDiscovering())
                    mBluetoothAdapter.startDiscovery();
                scanningState = ScanningState.SCANNING_IN_PROGRESS;
                EventBus.getDefault().post(new BluetoothScanStarted());

                /*
                 * we set a timeout in case the scanning discovery procedure doesn't stop by itself
                 * (yes. it does happen too.)
                 */
                handler.postDelayed(scanTimeoutFires, (long)SCANNING_TIMEOUT);
            }
        }
        catch (Exception e) {
            Log.d(TAG, "Exception:"+e.toString());
        } finally {
            lock.unlock();
        }
    }


    private Runnable scanTimeoutFires = new Runnable() {
        @Override
        public void run() {
            try {
                lock.lock();

                switch (scanningState) {
                    case SCANNING_OFF:
                    case SCANNING_IDLE:
                    case SCANNING_SCHEDULED:
                        return;
                    case SCANNING_IN_PROGRESS:
                        break;
                }

                Log.d(TAG, "[-] timeout expires: force scan procedure to stop");
                BluetoothAdapter mBluetoothAdapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
                if (mBluetoothAdapter.isDiscovering())
                    mBluetoothAdapter.cancelDiscovery();

                EventBus.getDefault().post(new BluetoothScanEnded());

                recomputeTrickleTimer();

                handler.postDelayed(scanScheduleFires, (long) trickleTimer);
                scanningState = ScanningState.SCANNING_SCHEDULED;

            } finally {
                lock.unlock();
            }
        }
    };

    private Runnable scanScheduleFires = new Runnable() {
        @Override
        public void run() {
            try {
                lock.lock();
                switch (scanningState) {
                    case SCANNING_OFF:
                    case SCANNING_IDLE:
                    case SCANNING_IN_PROGRESS:
                        return;
                    case SCANNING_SCHEDULED:
                        scanningState = ScanningState.SCANNING_IDLE;
                }
                /*
                 * just in case a neighbour connect while we were in this critical section
                 * in which case the state would be SCANNING_SCHEDULE with the callback attached
                 */
                handler.removeCallbacks(scanScheduleFires);
            } finally {
                lock.unlock();
            }
            performScan(false);
        }
    };

    @Override
    public void forceDiscovery() {
        resetTrickleTimer();
        performScan(true);
    }

    /*
     * The trickle timer increase if the neighborhood is consistent
     * It is resetted if the neighborhood is inconsistant
     */
    private void recomputeTrickleTimer() {
        int inconsistency = 0;
        HashSet<BluetoothNeighbour> tmp = new HashSet<BluetoothNeighbour>();

        /*
         * first we detect for new neighbour inconsistency
         */
        for (BluetoothNeighbour neighbor : btNeighborhood) {
            tmp.add(neighbor);
            // the neighbour reachable event has already been sent ACTION_DEVICE_FOUND
            if (!lastTrickleState.remove(neighbor))
                inconsistency++;
        }

        /*
         * Then we detect for neighbour that disappeared inconsistency
         */
        for (BluetoothNeighbour neighbor : lastTrickleState) {
            EventBus.getDefault().post(new ScannerNeighbourTimeout(neighbor));
            inconsistency++;
        }

        /*
         * then we save the state for the next trickle recomputation
         */
        lastTrickleState.clear();
        lastTrickleState = tmp;

        if(betamode) {
            trickleTimer = BETA_TRICKLE_TIMER;
        } else {
            if (inconsistency < INCONSISTENCY_THRESHOLD) {
                if (trickleTimer > EXPONENTIAL_THRESHOLD)
                    trickleTimer = (((trickleTimer * 2) > (START_TRICKLE_TIMER * (Math.pow(2, IMAX_TRICKLE_TIMER)))) ? Math.pow(2, IMAX_TRICKLE_TIMER) : (trickleTimer * 2));
                else
                    trickleTimer += LINEAR_STEP;
            } else {
                trickleTimer = START_TRICKLE_TIMER;
            }
        }
    }
    private void resetTrickleTimer() {
        lastTrickleState.clear();
        trickleTimer = START_TRICKLE_TIMER;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(scanningState.equals(ScanningState.SCANNING_OFF))
                return;

            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getAddress() == null)
                    return;
                BluetoothNeighbour btPeerDevice = new BluetoothNeighbour(device.getAddress());
                lock.lock();
                try {
                    if(btNeighborhood.add(btPeerDevice))
                        EventBus.getDefault().post(new ScannerNeighbourSensed(btPeerDevice));
                } finally {
                    lock.unlock();
                }
            }

            /*
             * It is possible that another application (like firechat) is also sending
             * discovery intent to bluetooth. In that case we silently use the response
             * of these one
             */
            if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
                SystemClock.sleep(2000);
                try {
                    lock.lock();
                    switch (scanningState) {
                        case SCANNING_OFF:
                            return;
                        case SCANNING_IDLE:
                            Log.d(TAG, "another app has triggered a scan, ignoring");
                            return;
                        case SCANNING_SCHEDULED:
                            Log.d(TAG, "another app has triggered a scan before our scheduled one");
                            handler.removeCallbacks(scanScheduleFires);
                            scanningState = ScanningState.SCANNING_IDLE;
                            break;
                        case SCANNING_IN_PROGRESS:
                            return;

                    }
                } finally {
                    lock.unlock();
                }
                performScan(true);
            }


            if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                try {
                    lock.lock();

                    switch (scanningState) {
                        case SCANNING_OFF:
                        case SCANNING_SCHEDULED:
                        case SCANNING_IDLE:
                            return;
                        case SCANNING_IN_PROGRESS:
                            break;
                    }

                    handler.removeCallbacks(scanTimeoutFires);
                    EventBus.getDefault().post(new BluetoothScanEnded());

                    recomputeTrickleTimer();
                    handler.postDelayed(scanScheduleFires, (long) trickleTimer);
                    scanningState = ScanningState.SCANNING_SCHEDULED;
                    Log.d(TAG, "[->] next scan in: "+trickleTimer/1000L+" seconds");
                } finally {
                    lock.unlock();
                }
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

    @Override
    public boolean isScanning() {
        return scanningState.equals(ScanningState.SCANNING_IN_PROGRESS);
    }

    @Override
    public HashSet<LinkLayerNeighbour> getNeighbourList() {
        try {
            lock.lock();
            HashSet<LinkLayerNeighbour> neighbourSet = new HashSet<LinkLayerNeighbour>();
            for (BluetoothNeighbour bn : btNeighborhood) {
                neighbourSet.add(bn);
            }
            return neighbourSet;
        } finally {
            lock.unlock();
        }
    }

    /*
     * discovering node disrupt connections...
     * when a neighbour is connected,  we scan much less
     */
    public void onEvent(ChannelConnected event) {
        if (!event.neighbour.getLinkLayerIdentifier().equals(BluetoothLinkLayerAdapter.LinkLayerIdentifier))
            return;
        try {
            lock.lock();
            openedSocket++;

            if(openedSocket == 1) {
                Log.d(TAG, "[+] entering slow scan mode ");
                betamode = true;

                switch (scanningState) {
                    case SCANNING_OFF:
                        return;
                    case SCANNING_IDLE:
                        /*
                         * most probably we are in between a call to performScan(false)
                         * out from scanScheduleFires(). or for some reason the scanner stopped scanning
                         */
                        break;
                    case SCANNING_IN_PROGRESS:
                        Log.d(TAG, "[-] cancelling current scan");
                        handler.removeCallbacks(scanTimeoutFires);
                        BluetoothAdapter mBluetoothAdapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
                        if (mBluetoothAdapter.isDiscovering())
                            mBluetoothAdapter.cancelDiscovery();
                        EventBus.getDefault().post(new BluetoothScanEnded());
                        break;
                    case SCANNING_SCHEDULED:
                        Log.d(TAG, "[-] cancelling previous scan scheduling");
                        handler.removeCallbacks(scanScheduleFires);
                        break;
                }

                handler.postDelayed(scanScheduleFires, (long) BETA_TRICKLE_TIMER);
                Log.d(TAG, "[->] next scan in: "+BETA_TRICKLE_TIMER/1000L+" seconds");
                scanningState = ScanningState.SCANNING_SCHEDULED;
            }
        } finally {
            lock.unlock();
        }
    }


    public void onEvent(ChannelDisconnected event) {
        if (!event.neighbour.getLinkLayerIdentifier().equals(BluetoothLinkLayerAdapter.LinkLayerIdentifier))
            return;
        try {
            lock.lock();
            openedSocket--;
            if(openedSocket < 0) {
                Log.e(TAG, "[!] opened socket cannot be below zero: "+openedSocket);
                openedSocket = 0;
            }

            if (openedSocket == 0) {
                Log.d(TAG, "[+] leaving slow scan mode, entering trickle strategy ");
                betamode = false;

                switch (scanningState) {
                    case SCANNING_OFF:         // we do nothing
                    case SCANNING_IDLE:        // most probably a scan will start shortly
                    case SCANNING_IN_PROGRESS: // it will reschedule a new scan by itself
                        return;
                    case SCANNING_SCHEDULED:   // that is the slow scan mode scheduled
                        handler.removeCallbacks(scanScheduleFires);
                        Log.d(TAG, "[-] cancelling previous scan scheduling");
                        break;
                }

                resetTrickleTimer();
                handler.postDelayed(scanScheduleFires, (long) trickleTimer);
                scanningState = ScanningState.SCANNING_SCHEDULED;
                Log.d(TAG, "[->] next scan in: "+trickleTimer/1000L+" seconds");
                /*
                    if((mAccelerometer != null) && !sensorregistered) {
                        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                        sensorregistered = true;
                    }
                */
            }
        } finally {
            lock.unlock();
        }
    }
}
