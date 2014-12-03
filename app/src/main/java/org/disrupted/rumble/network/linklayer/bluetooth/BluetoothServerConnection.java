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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.disrupted.rumble.network.linklayer.Connection;
import org.disrupted.rumble.network.protocols.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Marlinski
 */
public class BluetoothServerConnection extends Connection{

    private static final String TAG = "BluetoothServerConnection";

    private String macAddress;
    private BluetoothDevice mmBluetoothDevice;
    private BluetoothSocket mmConnectedSocket;
    private String connectionID;
    private InputStream inputStream;
    private OutputStream outputStream;
    private boolean isBeingKilled;

    public BluetoothServerConnection(BluetoothSocket socket, Protocol message, ConnectionCallback callback) {
        super(socket.getRemoteDevice().getAddress(), message, BluetoothLinkLayerAdapter.LinkLayerIdentifier, callback);
        this.mmConnectedSocket = socket;
        this.mmBluetoothDevice = socket.getRemoteDevice();
        this.macAddress = mmBluetoothDevice.getAddress();
        this.connectionID = "ConnectFROM: "+macAddress;
        this.inputStream = null;
        this.outputStream = null;
        this.isBeingKilled = false;
    }

    @Override
    public void run() {

        if(mmConnectedSocket == null) {
            onConnectionFailed("Client Socket is null");
            return;
        }

        try {
            inputStream  = mmConnectedSocket.getInputStream();
            outputStream = mmConnectedSocket.getOutputStream();
        } catch (IOException e) {
            onConnectionFailed("Cannot get In/Output stream from Bluetooth Socket");
            return;
        }

        onConnectionEstablished(macAddress);
        protocol.onConnected(macAddress, inputStream,outputStream);

        if(!isBeingKilled)
            kill();
    }

    @Override
    public void kill() {
        this.isBeingKilled = true;
        if(protocol.isRunning()) {
            protocol.stop();

            try {
                mmConnectedSocket.close();
            } catch( Exception ignore){
                Log.e(TAG, "unable to close() socket ",ignore);
            }
            onConnectionEnded(macAddress);
        }
    }

    @Override
    public String getConnectionID() {
        return connectionID;
    }

}
