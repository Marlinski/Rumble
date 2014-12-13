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

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.exceptions.RecordNotFoundException;

import java.io.IOException;

/**
 * @author Marlinski
 */
public abstract class BluetoothServerConnection extends BluetoothConnection {

    private static final String TAG = "BluetoothServerConnection";

    public BluetoothServerConnection(BluetoothSocket socket) {
        super(socket.getRemoteDevice().getAddress());
        this.mmConnectedSocket = socket;
    }

    @Override
    public void run() {
        try {
            if (mmConnectedSocket == null)
                throw new Exception("[!] Client Socket is null");

            this.mmBluetoothDevice = mmConnectedSocket.getRemoteDevice();

            if (mmBluetoothDevice == null)
                throw new Exception("[!] no remote bluetooth device");

            this.remoteMacAddress = mmBluetoothDevice.getAddress();

            inputStream = mmConnectedSocket.getInputStream();
            outputStream = mmConnectedSocket.getOutputStream();
        }
        catch(IOException e){
            Log.e(TAG, "[!] Cannot get In/Output stream from Bluetooth Socket");
            return;
        }
        catch (Exception e) {
            Log.e(TAG, e.toString());
            return;
        }

        onBluetoothConnected();

    }

}
