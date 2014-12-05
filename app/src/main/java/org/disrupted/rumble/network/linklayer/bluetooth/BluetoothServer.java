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

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.disrupted.rumble.network.NeighbourDevice;
import org.disrupted.rumble.network.linklayer.Connection;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.ThreadPoolCoordinator;
import org.disrupted.rumble.network.protocols.Protocol;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * @author Marlinski
 */
public class BluetoothServer extends Connection {

    private static final String TAG = "BluetoothServer";

    protected UUID bt_service_uuid;
    protected String bt_service_name;

    protected BluetoothServerSocket mmServerSocket;
    private boolean secureSocket;

    public BluetoothServer(UUID uuid, String name, boolean secure, Protocol protocol, ConnectionCallback callback) {
        super(BluetoothUtil.getBluetoothAdapter(NetworkCoordinator.getInstance()).getAddress(), protocol, BluetoothLinkLayerAdapter.LinkLayerIdentifier, callback);
        this.bt_service_uuid = uuid;
        this.bt_service_name = name;
        this.secureSocket = secure;
        this.connectionID = "BluetoothServer: "+bt_service_uuid.toString();
    }

    public void run() {
        BluetoothServerSocket tmp = null;
        NetworkCoordinator networkCoordinator = NetworkCoordinator.getInstance();

        try {
            if(secureSocket)
                tmp = BluetoothUtil.getBluetoothAdapter(networkCoordinator).listenUsingRfcommWithServiceRecord(this.bt_service_name,this.bt_service_uuid);
            else
                tmp = BluetoothUtil.getBluetoothAdapter(networkCoordinator).listenUsingInsecureRfcommWithServiceRecord(this.bt_service_name,this.bt_service_uuid);
        } catch (IOException e) {
            onConnectionFailed("cannot open Listen Socket on service record "+bt_service_uuid);
            return;
        }

        mmServerSocket = tmp;
        if(tmp == null){
            onConnectionFailed("cannot open Listen Socket on service record "+bt_service_uuid);
            return;
        }

        try {
            while(true) {
                BluetoothSocket mmConnectedSocket = mmServerSocket.accept();
                if (mmConnectedSocket != null) {
                    Log.d(TAG, "[+] Client connected");
                    NeighbourDevice neighbourDevice = new NeighbourDevice(
                            mmConnectedSocket.getRemoteDevice().getAddress(),
                            BluetoothLinkLayerAdapter.LinkLayerIdentifier);
                    neighbourDevice.setDeviceName(mmConnectedSocket.getRemoteDevice().getName());
                    networkCoordinator.newNeighbor(neighbourDevice);
                    BluetoothServerConnection clientThread = new BluetoothServerConnection(mmConnectedSocket, protocol.newInstance(), null);
                    ThreadPoolCoordinator.getInstance().addConnection(clientThread,ThreadPoolCoordinator.PRIORITY_HIGH);
                }
            }
        } catch (Exception e) {
            onConnectionEnded(connectionID);
        }
    }

    @Override
    public void kill() {
        try {
            mmServerSocket.close();
        } catch (Exception e) {
        }
    }
}

