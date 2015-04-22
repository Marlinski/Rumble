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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;


import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStarted;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStopped;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class BluetoothLinkLayerAdapter implements LinkLayerAdapter {

    private static final String TAG = "BluetoothLinkLayerAdapter";
    public static final String LinkLayerIdentifier = "BLUETOOTH";

    private NetworkCoordinator networkCoordinator;
    private BluetoothScanner btScanner;
    private boolean register;
    private boolean activated;


    public BluetoothLinkLayerAdapter(NetworkCoordinator networkCoordinator) {
        this.networkCoordinator = networkCoordinator;
        this.btScanner = BluetoothScanner.getInstance();
        register = false;
        activated = false;
    }

    public String getLinkLayerIdentifier() {
        return LinkLayerIdentifier;
    }

    @Override
    public boolean isActivated() {
        return register;
    }

    public void linkStart() {
        if(register)
            return;

        Log.d(TAG, "[+] Starting Bluetooth");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        RumbleApplication.getContext().registerReceiver(mReceiver, filter);
        register = true;

        if(BluetoothUtil.isEnabled())
            linkStarted();
    }

    public void linkStop() {
        if(!register)
            return;
        register = false;

        Log.d(TAG, "[-] Stopping Bluetooth");
        RumbleApplication.getContext().unregisterReceiver(mReceiver);
        linkStopped();
    }

    private void linkStarted() {
        if(activated)
            return;
        activated = true;
        Log.d(TAG, "[+] Bluetooth Activated");
        btScanner.startScanner();
        networkCoordinator.addScanner(btScanner);
        EventBus.getDefault().post(new LinkLayerStarted(getLinkLayerIdentifier()));
    }
    private void linkStopped() {
        if(!activated)
            return;
        activated = false;
        Log.d(TAG, "[-] Bluetooth De-activated");
        EventBus.getDefault().post(new LinkLayerStopped(getLinkLayerIdentifier()));
        btScanner.stopScanner();
        networkCoordinator.delScanner(btScanner);
    }


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                Log.d(TAG, "[!] BT State Changed");
                switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)){
                    case BluetoothAdapter.STATE_ON:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                // to avoid doing networking on the main thread
                                linkStarted();
                            }
                        }).start();
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                // to avoid doing networking on the main thread
                                linkStopped();
                            }
                        }).start();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }

            if(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED.equals(action)){
            }
        }
    };
}