/*
 * Copyright (C) 2014 Disrupted Systems
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

package org.disrupted.rumble.network.protocols.rumble.workers;

import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.Worker;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.wifi.TCP.TCPNeighbour;
import org.disrupted.rumble.network.linklayer.wifi.TCP.TCPServer;
import org.disrupted.rumble.network.linklayer.wifi.TCP.TCPServerConnection;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;

import java.net.Socket;

/**
 * @author Marlinski
 */
public class RumbleTCPServer extends TCPServer {

    private static final String TAG = "RumbleBluetoothServer";

    private final RumbleProtocol protocol;
    private final NetworkCoordinator networkCoordinator;

    public RumbleTCPServer(RumbleProtocol protocol, NetworkCoordinator networkCoordinator) {
        super(RumbleProtocol.RUMBLE_TCP_PORT);
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
    protected void onClientConnected(Socket mmConnectedSocket) {
        LinkLayerNeighbour neighbour = new TCPNeighbour(mmConnectedSocket.getInetAddress().getHostAddress());
        Worker worker = new RumbleOverTCP(protocol, new TCPServerConnection(mmConnectedSocket));
        networkCoordinator.addWorker(worker);
    }
}
