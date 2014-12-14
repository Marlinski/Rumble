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
import org.disrupted.rumble.network.exceptions.ProtocolNotFoundException;
import org.disrupted.rumble.network.exceptions.RecordNotFoundException;
import org.disrupted.rumble.network.linklayer.Connection;
import org.disrupted.rumble.network.protocols.GenericProtocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * BluetoothConnection is a generic implementation of a Bluetooth Connection wether it is to manage
 * a client or to connect to a server. It is abstract as the connection part is specific and should
 * call onBluetoothConnected when done.
 *
 * @author Marlinski
 */
public abstract class BluetoothConnection extends GenericProtocol implements Connection {

    private static final String TAG = "BluetoothConnection";

    protected String remoteMacAddress;
    protected boolean secureSocket;
    protected BluetoothDevice mmBluetoothDevice;
    protected BluetoothSocket mmConnectedSocket;
    protected InputStream inputStream;
    protected OutputStream outputStream;
    protected boolean isBeingKilled;

    public BluetoothConnection(String remoteMacAddress) {
        this.remoteMacAddress = remoteMacAddress;
        this.mmConnectedSocket = null;
        this.secureSocket = false;
        this.inputStream = null;
        this.outputStream = null;
        this.isBeingKilled = false;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return BluetoothLinkLayerAdapter.LinkLayerIdentifier;
    }

    @Override
    public String getType() {
        return BluetoothLinkLayerAdapter.LinkLayerIdentifier;
    }

    public void onBluetoothConnected() {

        try {

            NetworkCoordinator.getInstance().addProtocol(remoteMacAddress, this);

            Log.d(TAG, "[+] ESTABLISHED: " + getConnectionID());

            onGenericProcotolConnected();

        } catch (RecordNotFoundException ignoredCauseImpossible) {
            Log.e(TAG, "[+] FAILED: cannot find the record for " + getRemoteMacAddress());
        } catch (IOException ignore) {
            Log.d(TAG, "[+] FAILED: "+ignore.getMessage());
        } finally {
            try {
                NetworkCoordinator.getInstance().delProtocol(remoteMacAddress, this);
            } catch (RecordNotFoundException ignoredCauseImpossible) {
            } catch (ProtocolNotFoundException ignoredCauseImpossible) {
            }

            if (!isBeingKilled)
                kill();
        }
    }

    @Override
    public void kill() {
        this.isBeingKilled = true;
        try {
            mmConnectedSocket.close();
        } catch (Exception ignore) {
            Log.e(TAG, "[!] unable to close() socket ", ignore);
        }
        Log.d(TAG, "[-] ENDED: " + getConnectionID());
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public String getRemoteMacAddress() {
        return remoteMacAddress;
    }
}
