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
import android.util.Log;


import org.disrupted.rumble.network.NeighbourDevice;
import org.disrupted.rumble.network.linklayer.Connection;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.ThreadPoolCoordinator;
import org.disrupted.rumble.network.protocols.Rumble.RumbleBTConfiguration;
import org.disrupted.rumble.network.protocols.Rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.firechat.FireChatProtocol;
import org.disrupted.rumble.network.protocols.firechat.FirechatBTConfiguration;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public class BluetoothLinkLayerAdapter extends LinkLayerAdapter implements Connection.ConnectionCallback{

    private static final String TAG = "BluetoothLinkLayerAdapter";
    public static final String LinkLayerIdentifier = "BLUETOOTH";

    private BluetoothAdapter btAdapter;
    private BluetoothScanner btScanner;
    private boolean register;
    private boolean activated;

    private List<Connection> connectionToPerform;

    public BluetoothLinkLayerAdapter(NetworkCoordinator networkCoordinator) {
        super(networkCoordinator);
        this.btAdapter = BluetoothUtil.getBluetoothAdapter(networkCoordinator);
        this.btScanner = BluetoothScanner.getInstance(networkCoordinator);
        register = false;
        activated = false;
        connectionToPerform = new LinkedList<Connection>();
    }

    public String getID() {
        return LinkLayerIdentifier;
    }

    public void onLinkStart() {
        BluetoothServer btRumbleServer = new BluetoothServer(
                RumbleBTConfiguration.RUMBLE_BT_UUID_128,
                RumbleBTConfiguration.RUMBLE_BT_STR,
                false,
                new RumbleProtocol(),
                null
        );
        ThreadPoolCoordinator.getInstance().addConnection(btRumbleServer);

        btScanner.startDiscovery();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        networkCoordinator.registerReceiver(mReceiver, filter);
        register = true;
        activated = true;
    }

    public void onLinkStop() {
        btScanner.destroy();
        ThreadPoolCoordinator.getInstance().killThreadType(LinkLayerIdentifier);
        if(register)
            networkCoordinator.unregisterReceiver(mReceiver);
        register = false;
        activated = false;
        networkCoordinator.removeNeighborsType(LinkLayerIdentifier);
    }

    @Override
    public boolean isScanning() {
        return BluetoothUtil.getBluetoothAdapter(networkCoordinator).isDiscovering();
    }

    public void forceDiscovery() {
        if(activated)
            btScanner.forceDiscovery();
    }

    public List<NeighbourDevice> getNeighborhood() {
        List<NeighbourDevice> neighborhood = new LinkedList<NeighbourDevice>();
        HashSet<NeighbourDevice> btNeighborhood = btScanner.getNeighborhood();
        Iterator<NeighbourDevice> it = btNeighborhood.iterator();
        while(it.hasNext()) {
            NeighbourDevice element = it.next();
            if(element.isRumbler() || element.isFirechatter())
                neighborhood.add(new NeighbourDevice(element));
        }
        return neighborhood;
    }

    public void connectTo(NeighbourDevice neighbourDevice, boolean force) {

        //todo make this portion of code protocol independant (by registering the protocol)
        if( (neighbourDevice.isRumbler() || force) && !neighbourDevice.isConnected(RumbleProtocol.ID) ){
            BluetoothClient rumbleConnection = new BluetoothClient(
                    neighbourDevice.getMacAddress(),
                    RumbleBTConfiguration.RUMBLE_BT_UUID_128,
                    RumbleBTConfiguration.RUMBLE_BT_STR,
                    false,
                    new RumbleProtocol(),
                    this
            );
            ThreadPoolCoordinator.getInstance().addConnection(rumbleConnection);
        }

        if( (neighbourDevice.isFirechatter() || force) && !neighbourDevice.isConnected(FireChatProtocol.ID) ) {
            BluetoothClient firechatConnection = new BluetoothClient(
                    neighbourDevice.getMacAddress(),
                    FirechatBTConfiguration.FIRECHAT_BT_UUID_128,
                    FirechatBTConfiguration.FIRECHAT_BT_STR,
                    false,
                    new FireChatProtocol(),
                    this
            );
            ThreadPoolCoordinator.getInstance().addConnection(firechatConnection);
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

    @Override
    public void onConnectionFailed(Connection connection, String reason) {
    }

    @Override
    public void onConnectionSucceeded(Connection connection) {
    }

    @Override
    public void onConnectionEnded(Connection connection) {

    }
}