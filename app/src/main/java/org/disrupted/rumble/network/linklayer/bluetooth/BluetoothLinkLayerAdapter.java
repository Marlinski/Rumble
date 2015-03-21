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
import org.disrupted.rumble.network.NeighbourManager;
import org.disrupted.rumble.network.NetworkThread;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.exceptions.RecordNotFoundException;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.ThreadPoolCoordinator;
import org.disrupted.rumble.network.protocols.firechat.FirechatOverBluetooth;
import org.disrupted.rumble.network.protocols.rumble.RumbleBTState;
import org.disrupted.rumble.network.protocols.rumble.RumbleOverBluetooth;
import org.disrupted.rumble.network.protocols.rumble.RumbleBTServer;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.firechat.FirechatProtocol;

/**
 * @author Marlinski
 */
public class BluetoothLinkLayerAdapter extends LinkLayerAdapter {

    private static final String TAG = "BluetoothLinkLayerAdapter";
    public static final String LinkLayerIdentifier = "BLUETOOTH";

    private BluetoothScanner btScanner;
    private boolean register;


    public BluetoothLinkLayerAdapter(NetworkCoordinator networkCoordinator) {
        super(networkCoordinator);
        this.btScanner = BluetoothScanner.getInstance(networkCoordinator);
        register = false;
    }

    public String getLinkLayerIdentifier() {
        return LinkLayerIdentifier;
    }

    public void onLinkStart() {
        Log.d(TAG, "[+] Starting Bluetooth");
        RumbleBTServer btRumbleServer = new RumbleBTServer();
        ThreadPoolCoordinator.getInstance().addNetworkThread(btRumbleServer);

        btScanner.startDiscovery();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        RumbleApplication.getContext().registerReceiver(mReceiver, filter);
        register = true;
    }

    public void onLinkStop() {
        Log.d(TAG, "[+] Stopping Bluetooth");
        btScanner.destroy();
        ThreadPoolCoordinator.getInstance().killThreadType(LinkLayerIdentifier);
        if(register)
            RumbleApplication.getContext().unregisterReceiver(mReceiver);
        register = false;
        // TODO: this should be useless, should check
        NetworkCoordinator.getInstance().removeNeighborsType(LinkLayerIdentifier);

    }

    @Override
    public boolean isScanning() {
        if(activated)
            return BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext()).isDiscovering();
        else
            return false;
    }

    public void forceDiscovery() {
        if(activated)
            btScanner.forceDiscovery();
    }


    /*
     * todo make this portion of code protocol independant (by registering the protocol when button click)
     */
    public void connectTo(LinkLayerNeighbour neighbour, boolean force) {
        NeighbourManager record;
        try {
            record = NetworkCoordinator.getInstance().getNeighbourRecordFromDeviceAddress(neighbour.getLinkLayerAddress());
        } catch (RecordNotFoundException e) {
            Log.e(TAG, "[!] record not found ! cannot connectTo "+neighbour.getLinkLayerAddress());
            return;
        }

        if (record.getRumbleBTState().getState() == RumbleBTState.RumbleBluetoothState.NOT_CONNECTED) {
            BluetoothConnection con = new BluetoothClientConnection(
                    neighbour.getLinkLayerAddress(),
                    RumbleProtocol.RUMBLE_BT_UUID_128,
                    RumbleProtocol.RUMBLE_BT_STR,
                    false);
            NetworkThread rumble = new RumbleOverBluetooth(con);
            try {
                record.getRumbleBTState().connectionInitiated(rumble.getNetworkThreadID());
                ThreadPoolCoordinator.getInstance().addNetworkThread(rumble);
            } catch (RumbleBTState.StateException e) {
                /*
                 * Should be quiet rare but it is possible that the BluetoothServer thread has
                 * Accepted() a connection before we initiate this one. In that case, we silently
                 * cancel this connection request
                 */
            }
        }

        if (!record.isConnectedWithProtocol(FirechatProtocol.protocolID)) {
            BluetoothConnection con = new BluetoothClientConnection(
                    neighbour.getLinkLayerAddress(),
                    FirechatProtocol.FIRECHAT_BT_UUID_128,
                    FirechatProtocol.FIRECHAT_BT_STR,
                    false);
            FirechatOverBluetooth firechat = new FirechatOverBluetooth(con);
            ThreadPoolCoordinator.getInstance().addNetworkThread(firechat);
        }

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