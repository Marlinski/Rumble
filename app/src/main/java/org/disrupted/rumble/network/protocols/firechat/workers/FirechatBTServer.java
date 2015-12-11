/*
 * Copyright (C) 2014 Lucien Loiseau
 * This file is part of Rumble.
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
 * You should have received a copy of the GNU General Public License along
 * with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.network.protocols.firechat.workers;

import android.bluetooth.BluetoothSocket;
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothServer;
import org.disrupted.rumble.network.protocols.firechat.FirechatProtocol;

import java.io.IOException;

/**
 * @author Lucien Loiseau
 */
public class FirechatBTServer extends BluetoothServer {

    private static final String TAG = "FirechatBluetoothServer";

    public FirechatBTServer(FirechatProtocol protocol, NetworkCoordinator networkCoordinator) {
        super(FirechatProtocol.FIRECHAT_BT_UUID_128, FirechatProtocol.FIRECHAT_BT_STR, false);
    }

    @Override
    public String getWorkerIdentifier() {
        return "Firechat"+super.getWorkerIdentifier();
    }

    @Override
    public String getProtocolIdentifier() {
        return FirechatProtocol.protocolID;
    }

    @Override
    protected void onClientConnected(BluetoothSocket mmConnectedSocket) {
        try {
            Log.d(TAG, "[-] refusing firechat connection");
            mmConnectedSocket.close();
        } catch(IOException e) {
        }
    }


}
