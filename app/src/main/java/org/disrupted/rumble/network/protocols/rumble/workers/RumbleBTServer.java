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

package org.disrupted.rumble.network.protocols.rumble.workers;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothServer;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothServerConnection;
import org.disrupted.rumble.network.protocols.Worker;
import org.disrupted.rumble.network.protocols.rumble.RumbleBTState;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;

import java.io.IOException;

/**
 * @author Marlinski
 */
public class RumbleBTServer extends BluetoothServer {

    private static final String TAG = "RumbleBluetoothServer";
    private final RumbleProtocol protocol;
    private final NetworkCoordinator networkCoordinator;

    public RumbleBTServer(RumbleProtocol protocol, NetworkCoordinator networkCoordinator) {
        super(RumbleProtocol.RUMBLE_BT_UUID_128, RumbleProtocol.RUMBLE_BT_STR, false);
        this.protocol = protocol;
        this.networkCoordinator = networkCoordinator;
    }

    @Override
    public String getWorkerIdentifier() {
        return "Rumble"+super.getWorkerIdentifier();
    }

    @Override
    public String getProtocolIdentifier() {
        return RumbleProtocol.protocolID;
    }

    @Override
    protected void onClientConnected(BluetoothSocket mmConnectedSocket) {
        LinkLayerNeighbour neighbour = new BluetoothNeighbour(mmConnectedSocket.getRemoteDevice().getAddress());
        try {
            RumbleBTState connectionState = protocol.getBTState(neighbour.getLinkLayerAddress());
            switch (connectionState.getState()) {
                case CONNECTED:
                case CONNECTION_ACCEPTED:
                    Log.d(TAG, "[-] refusing client connection");
                    mmConnectedSocket.close();
                    return;
                case CONNECTION_INITIATED:
                    if (neighbour.getLinkLayerAddress().compareTo(localMacAddress) < 0) {
                        Log.d(TAG, "[-] refusing client connection");
                        mmConnectedSocket.close();
                    } else {
                        Log.d(TAG, "[-] cancelling worker " + connectionState.getConnectionInitiatedWorkerID());
                        networkCoordinator.stopWorker(
                                BluetoothLinkLayerAdapter.LinkLayerIdentifier,
                                connectionState.getConnectionInitiatedWorkerID());
                    }
                case NOT_CONNECTED:
                    Worker worker = new RumbleOverBluetooth(protocol, new BluetoothServerConnection(mmConnectedSocket));
                    connectionState.connectionAccepted(worker.getWorkerIdentifier());
                    networkCoordinator.addWorker(worker);
                default:
                    return;
            }

        } catch(IOException ignore) {
        } catch (RumbleBTState.StateException e) {
            Log.e(TAG,"[!] Rumble Bluetooth State Exception");
        }
    }

}
