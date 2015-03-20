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

package org.disrupted.rumble.network.protocols.rumble;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.NetworkThread;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothServer;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothServerConnection;

/**
 * @author Marlinski
 */
public class RumbleBTServer extends BluetoothServer {

    private static final String TAG = "RumbleBluetoothServer";

    public RumbleBTServer() {
        super(RumbleProtocol.RUMBLE_BT_UUID_128, RumbleProtocol.RUMBLE_BT_STR, false);
    }

    @Override
    public String getNetworkThreadID() {
        return RumbleProtocol.protocolID + super.getNetworkThreadID();
    }

    /*
     * we don't allow two connection with the same client
     * When a client connect to the server, we will accept the connection based on its mac address
     * If ourmacaddress < remotemacaddress we accept the connection
     * else we refuse the connection and will connect to it instead
     */
    @Override
    protected NetworkThread onClientConnected(BluetoothSocket mmConnectedSocket) {
        LinkLayerNeighbour neighbour = new BluetoothNeighbour(mmConnectedSocket.getRemoteDevice().getAddress());
        Log.d(TAG, neighbour.getLinkLayerAddress()+" "+localMacAddress+" : "+neighbour.getLinkLayerAddress().compareTo(localMacAddress) );
        if(neighbour.getLinkLayerAddress().compareTo(localMacAddress) < 0)
            return null;
        return new RumbleOverBluetooth(new BluetoothServerConnection(mmConnectedSocket));
    }

    /*
    @Override
    protected NetworkThread onClientConnected(BluetoothSocket mmConnectedSocket) {
        LinkLayerNeighbour neighbour = new BluetoothNeighbour(mmConnectedSocket.getRemoteDevice().getAddress());
        try {
            if (NetworkCoordinator.getInstance().isNeighbourConnectedWithProtocol(neighbour, RumbleProtocol.protocolID)) {

                 * We are receiving a connection from someone we are already connected to.
                 * This case happen only if both device discover at the same time.
                 * we cannot simply drop the connection because the other end would do the same
                 * and that would result in both side closing the connection
                 * To solve this issue, only the lower mac address drop the connection.

                if(neighbour.getLinkLayerAddress().compareTo(localMacAddress) < 0)
                    return null;
            }
            return new RumbleOverBluetooth(new BluetoothServerConnection(mmConnectedSocket));
        }catch(RecordNotFoundException first) {
            return null;
        }
    }
    */
}
