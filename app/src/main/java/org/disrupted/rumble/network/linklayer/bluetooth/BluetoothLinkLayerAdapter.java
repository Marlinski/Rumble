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
import org.disrupted.rumble.network.Neighbour;
import org.disrupted.rumble.network.exceptions.RecordNotFoundException;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.ThreadPoolCoordinator;
import org.disrupted.rumble.network.protocols.Rumble.RumbleBTClient;
import org.disrupted.rumble.network.protocols.Rumble.RumbleBTServer;
import org.disrupted.rumble.network.protocols.Rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.firechat.FirechatBTClient;
import org.disrupted.rumble.network.protocols.firechat.FirechatProtocol;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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

    public String getID() {
        return LinkLayerIdentifier;
    }

    public void onLinkStart() {
        org.disrupted.rumble.network.linklayer.bluetooth.BluetoothServer btRumbleServer = new RumbleBTServer();
        ThreadPoolCoordinator.getInstance().addConnection(btRumbleServer);

        btScanner.startDiscovery();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        RumbleApplication.getContext().registerReceiver(mReceiver, filter);
        register = true;
    }

    public void onLinkStop() {
        btScanner.destroy();
        ThreadPoolCoordinator.getInstance().killThreadType(LinkLayerIdentifier);
        if(register)
            RumbleApplication.getContext().unregisterReceiver(mReceiver);
        register = false;
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

    public void connectTo(Neighbour neighbour, boolean force) {
        //todo make this portion of code protocol independant (by registering the protocol when button click)
        try {
            if (!NetworkCoordinator.getInstance().isNeighbourConnectedWithProtocol(neighbour, RumbleProtocol.protocolID)) {
                org.disrupted.rumble.network.linklayer.bluetooth.BluetoothClient rumbleConnection = new RumbleBTClient(neighbour.getMacAddress());
                ThreadPoolCoordinator.getInstance().addConnection(rumbleConnection);
            } else {
                Log.d(TAG, "already connected to "+neighbour.getMacAddress()+" with Rumble");
            }
        } catch (RecordNotFoundException ignore){
            Log.e(TAG, "[!] cannot connect to neighbour "+neighbour.getMacAddress()+" record not found !");
        }

        try {
            if (!NetworkCoordinator.getInstance().isNeighbourConnectedWithProtocol(neighbour, FirechatProtocol.protocolID)) {
                org.disrupted.rumble.network.linklayer.bluetooth.BluetoothClient rumbleConnection = new FirechatBTClient(neighbour.getMacAddress());
                ThreadPoolCoordinator.getInstance().addConnection(rumbleConnection);
            } else {
                Log.d(TAG, "already connected "+neighbour.getMacAddress()+" with Firechat");
            }
        }catch (RecordNotFoundException ignore){
            Log.e(TAG, "[!] cannot connect to neighbour "+neighbour.getMacAddress()+" record not found !");
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