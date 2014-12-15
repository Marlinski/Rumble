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
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.Neighbour;
import org.disrupted.rumble.network.NetworkThread;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.ThreadPoolCoordinator;

import java.io.IOException;
import java.util.UUID;

/**
 * @author Marlinski
 */
public abstract class BluetoothServer implements NetworkThread {

    private static final String TAG = "BluetoothServer";

    protected String localMacAddress;
    protected BluetoothServerSocket mmServerSocket;
    protected UUID bt_service_uuid;
    protected String bt_service_name;
    protected boolean secureSocket;

    public BluetoothServer(UUID uuid, String name, boolean secure) {
        this.bt_service_uuid = uuid;
        this.bt_service_name = name;
        this.secureSocket = secure;
    }

    @Override
    public String getType() {
        return BluetoothLinkLayerAdapter.LinkLayerIdentifier;
    }

    @Override
    public String getNetworkThreadID() {
        return "Bluetooth Server";
    }


    public void run() {
        BluetoothAdapter adapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
        if(adapter == null)
            return;

        localMacAddress = adapter.getAddress();

        BluetoothServerSocket tmp = null;

        try {
            if(secureSocket)
                tmp = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext()).listenUsingRfcommWithServiceRecord(this.bt_service_name,this.bt_service_uuid);
            else
                tmp = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext()).listenUsingInsecureRfcommWithServiceRecord(this.bt_service_name,this.bt_service_uuid);
        } catch (IOException e) {
            Log.d(TAG, "cannot open Listen Socket on service record "+bt_service_uuid);
            return;
        }

        mmServerSocket = tmp;
        if(tmp == null){
            Log.d(TAG, "cannot open Listen Socket on service record "+bt_service_uuid);
            return;
        }

        try {
            while(true) {
                BluetoothSocket mmConnectedSocket = mmServerSocket.accept();
                if (mmConnectedSocket != null) {
                    Log.d(TAG, "[+] Client connected");

                    Neighbour neighbour = new BluetoothNeighbour(mmConnectedSocket.getRemoteDevice().getAddress());
                    NetworkCoordinator.getInstance().newNeighbour(neighbour);

                    NetworkThread clientThread = onClientConnected(mmConnectedSocket);

                    if(clientThread != null) {
                        if (!ThreadPoolCoordinator.getInstance().addNetworkThread(clientThread, ThreadPoolCoordinator.PRIORITY_HIGH)) {
                            try {
                                mmConnectedSocket.close();
                            } catch (IOException ignore) {
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "[-] ENDED "+getNetworkThreadID());
        }
    }

    abstract protected NetworkThread onClientConnected(BluetoothSocket mmConnectedSocket);

    @Override
    public void kill() {
        try {
            mmServerSocket.close();
        } catch (Exception e) {
        }
    }
}

