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
import org.disrupted.rumble.network.events.LinkLayerStarted;
import org.disrupted.rumble.network.events.LinkLayerStopped;
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
        return activated;
    }

    public void linkStart() {
        if(activated)
            return;

        Log.d(TAG, "[+] Starting Bluetooth");
        btScanner.startDiscovery();
        networkCoordinator.addScanner(btScanner);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        RumbleApplication.getContext().registerReceiver(mReceiver, filter);
        register = true;
        activated = true;

        EventBus.getDefault().post(new LinkLayerStarted(getLinkLayerIdentifier()));
    }

    public void linkStop() {
        if(!activated)
            return;

        Log.d(TAG, "[+] Stopping Bluetooth");
        networkCoordinator.delScanner(btScanner);
        btScanner.destroy();

        EventBus.getDefault().post(new LinkLayerStopped(getLinkLayerIdentifier()));
        if(register)
            RumbleApplication.getContext().unregisterReceiver(mReceiver);
        register = false;
        activated = false;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                Log.d(TAG, "[!] BT State Changed");
                switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)){
                    case BluetoothAdapter.STATE_ON:
                        btScanner.startDiscovery();
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        btScanner.stopDiscovery();
                        linkStop();
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