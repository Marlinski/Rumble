/*
 * Copyright (C) 2014 Lucien Loiseau
 * This file is part of Rumble.
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
 * You should have received a copy of the GNU General Public License along
 * with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.network.linklayer.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.Scanner;
import org.disrupted.rumble.network.linklayer.events.AccessPointReachable;
import org.disrupted.rumble.network.linklayer.events.AccessPointUnreachable;
import org.disrupted.rumble.network.linklayer.events.WifiScanEnded;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class WifiScanner extends HandlerThread implements Scanner {

    private static final String TAG = "WifiScanner";

    private static final ReentrantLock lock = new ReentrantLock();

    private enum ScanningState {
        SCANNING_OFF, SCANNING_IDLE, SCANNING_SCHEDULED, SCANNING_IN_PROGRESS
    }
    private ScanningState scanningState;
    private Map<String, ScanResult> accessPoints;

    private static final double PERIODIC_SCAN = 15000;
    private Handler handler;

    private boolean registered;

    public WifiScanner() {
        super(TAG);
        scanningState = ScanningState.SCANNING_OFF;
        accessPoints  = new HashMap<>();
        registered = false;
    }

    @Override
    public void startScanner() {
        lock.lock();
        try {
            if (!scanningState.equals(ScanningState.SCANNING_OFF))
                return;
            scanningState = ScanningState.SCANNING_IDLE;

            Log.d(TAG, "((( Wifi Scanner started )))");

            if (!registered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

                handler = new Handler(getLooper());
                RumbleApplication.getContext().registerReceiver(mReceiver, filter, null, handler);
                registered = true;
            }
        } finally {
            lock.unlock();
        }
        performScan(true);
    }


    @Override
    public void stopScanner() {
        lock.lock();
        try {
            if (scanningState.equals(ScanningState.SCANNING_OFF))
                return;

            switch (scanningState) {
                case SCANNING_IDLE:
                    break;
                case SCANNING_SCHEDULED:
                    handler.removeCallbacks(scanScheduleFires);
                    break;
                case SCANNING_IN_PROGRESS:
                    EventBus.getDefault().post(new WifiScanEnded());
                    break;
            }
            scanningState = ScanningState.SCANNING_OFF;

            Log.d(TAG, "))) Wifi Scanner stopped (((");

            if (EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);

            if (registered) {
                RumbleApplication.getContext().unregisterReceiver(mReceiver);
                registered = false;
            }

            accessPoints.clear();
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

            if( WifiUtil.getWifiManager().isWifiEnabled()){
                WifiUtil.getWifiManager().startScan();
            }
        }
        catch (Exception e) {
            Log.d(TAG, "Exception:"+e.toString());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void forceDiscovery() {
    }

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

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (scanningState.equals(ScanningState.SCANNING_OFF))
                return;

            String action = intent.getAction();

            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                lock.lock();
                try {
                    List<ScanResult> results = WifiUtil.getWifiManager().getScanResults();

                    // we remove entries that disappeared
                    for(Map.Entry<String, ScanResult> entry : accessPoints.entrySet()) {
                        boolean found = false;
                        outer:
                        for(ScanResult result : results) {
                            if(entry.getKey().equals(result.BSSID)) {
                                found = true;
                                break outer;
                            }
                        }
                        if(!found) {
                            EventBus.getDefault().post(new AccessPointUnreachable(entry.getValue()));
                            accessPoints.remove(entry.getKey());
                        }
                    }

                    // we update / add the entries
                    for (ScanResult result : results) {
                        if (!accessPoints.containsKey(result.BSSID)) {
                            accessPoints.put(result.BSSID, result);
                            EventBus.getDefault().post(new AccessPointReachable(result));
                        } else {
                            accessPoints.put(result.BSSID, result);
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    };



    @Override
    public boolean isScanning() {
        return scanningState.equals(ScanningState.SCANNING_IN_PROGRESS);
    }


    @Override
    public HashSet<LinkLayerNeighbour> getNeighbourList() {
        HashSet<LinkLayerNeighbour> ret = new HashSet<>();
        for(Map.Entry<String, ScanResult> entry : accessPoints.entrySet()) {
            ret.add(new WifiAPNeighbour(entry.getValue()));
        }
        return ret;
    }
}
