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

import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.Connection;
import org.disrupted.rumble.network.protocols.GenericProtocol;
import org.disrupted.rumble.network.protocols.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Marlinski
 */
public abstract class BluetoothServerConnection extends GenericProtocol implements Connection {

    private static final String TAG = "BluetoothServerConnection";

    protected String macAddress;
    protected BluetoothDevice mmBluetoothDevice;
    protected BluetoothSocket mmConnectedSocket;
    protected String connectionID;
    protected InputStream inputStream;
    protected OutputStream outputStream;
    protected boolean isBeingKilled;

    public BluetoothServerConnection(BluetoothSocket socket) {
        this.macAddress = socket.getRemoteDevice().getAddress();
        this.mmConnectedSocket = socket;
        this.mmBluetoothDevice = socket.getRemoteDevice();
        this.macAddress = mmBluetoothDevice.getAddress();
        this.inputStream = null;
        this.outputStream = null;
        this.isBeingKilled = false;
    }

    @Override
    public String getType() {
        return BluetoothLinkLayerAdapter.LinkLayerIdentifier;
    }


    @Override
    public void run() {

        if(mmConnectedSocket == null) {
            Log.e(TAG, "[!] Client Socket is null");
            return;
        }

        try {
            inputStream  = mmConnectedSocket.getInputStream();
            outputStream = mmConnectedSocket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "[!] Cannot get In/Output stream from Bluetooth Socket");
            return;
        }

        Log.d(TAG, "[+] ESTABLISHED: "+getConnectionID());
        NetworkCoordinator networkCoordinator = NetworkCoordinator.getInstance();
        if(networkCoordinator != null)
            networkCoordinator.addProtocol(macAddress, this);

        onConnected();

        if(!isBeingKilled)
            kill();
    }

    @Override
    public void kill() {
        this.isBeingKilled = true;
        if(isRunning()) {
            stop();
            try {
                mmConnectedSocket.close();
            } catch( Exception ignore){
                Log.e(TAG, "[!] unable to close() socket ",ignore);
            }
            Log.d(TAG, "[+] ENDED: "+getConnectionID());
        }
    }
}
