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
import android.os.*;

import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.events.BluetoothScanEnded;
import org.disrupted.rumble.network.events.BluetoothScanStarted;
import org.disrupted.rumble.network.exceptions.RecordNotFoundException;

import java.util.HashSet;
import java.lang.Math;
import java.util.Iterator;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class BluetoothScanner {

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
     * todo: they might be race condition issues whenever a DISCOVERY starts at the same
     *
     */
    private boolean hasCallback = false;

    /*
     * Scanning consumes a lot of  resources, especially  battery.
     * in order  to save battery, period  between two  successive
     * scan follow the trickle algorithm defined in the RFC 6206:
     *
     *         http://tools.ietf.org/html/rfc6206
     *
     * The idea is to increase the non-scanning period if the
     * neighborhood stay consistent between two scan.
     */
    private static final double START_TRICKLE_TIMER = 10000;
    private static final double IMIN_TRICKLE_TIMER  = 5000;
    private static final double IMAX_TRICKLE_TIMER  = 16;
    private int                 consistency = 1;
    private double              trickleTimer = START_TRICKLE_TIMER;
    private HashSet<BluetoothNeighbour>  lastTrickleState;
    private Handler             handler;

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
    }

    public BluetoothNeighbour getNeighbor(String address) {
        Iterator<BluetoothNeighbour> it = btNeighborhood.iterator();
        while(it.hasNext()) {
            BluetoothNeighbour element = it.next();
            if(element.getMacAddress().equals(address))
                return element;
        }
        return null;
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
        if(registered)
            RumbleApplication.getContext().unregisterReceiver(mReceiver);
        registered = false;
        BluetoothAdapter mBluetoothAdapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
        if (mBluetoothAdapter == null)
            return;
        btNeighborhood.clear();
        resetTrickleTimer();
        scanningState    = ScanningState.SCANNING_OFF;
        EventBus.getDefault().post(new BluetoothScanEnded());
    }

    public void forceDiscovery() {
        resetTrickleTimer();
        startDiscovery();
    }

    public void destroy() {
        stopDiscovery();
    }

    private void resetTrickleTimer() {
        lastTrickleState.clear();
        trickleTimer = IMIN_TRICKLE_TIMER;
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
            try { NetworkCoordinator.getInstance().delNeighbor(neighbor.getMacAddress()); }
            catch (RecordNotFoundException ignore){  }
            inconsistency++;
        }
        lastTrickleState.clear();

        /*
         * we save the state for the next trickle recomputation
         */
        lastTrickleState = tmp;

        if(inconsistency < consistency)
            trickleTimer = (((trickleTimer * 2) > (IMIN_TRICKLE_TIMER*(Math.pow(2,IMAX_TRICKLE_TIMER)))) ? Math.pow(2,IMAX_TRICKLE_TIMER) : (trickleTimer*2));
        else
            trickleTimer = IMIN_TRICKLE_TIMER;

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
                    NetworkCoordinator.getInstance().newNeighbour(btPeerDevice);
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
             * discovery intent to bluetooth. In that case we silently use this as an
             *
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

                EventBus.getDefault().post(new BluetoothScanStarted());
            }

            if(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)){
                int oldState = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, 0);
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE, 0);
                Log.d(TAG, "[+] Scan Mode Changed "+oldState+" -> "+newState);
            }

        }
    };

}
