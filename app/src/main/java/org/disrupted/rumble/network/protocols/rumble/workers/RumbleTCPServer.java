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

package org.disrupted.rumble.network.protocols.rumble.workers;

import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.Worker;
import org.disrupted.rumble.network.events.ScannerNeighbourSensed;
import org.disrupted.rumble.network.linklayer.wifi.TCP.TCPServer;
import org.disrupted.rumble.network.linklayer.wifi.TCP.TCPServerConnection;
import org.disrupted.rumble.network.linklayer.wifi.WifiLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.wifi.WifiNeighbour;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.RumbleStateMachine;
import org.disrupted.rumble.util.NetUtil;

import java.io.IOException;
import java.net.Socket;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class RumbleTCPServer extends TCPServer {

    private static final String TAG = "RumbleTCPServer";

    public static final int   RUMBLE_TCP_PORT = 7430;

    private final RumbleProtocol protocol;
    private final NetworkCoordinator networkCoordinator;

    public RumbleTCPServer(RumbleProtocol protocol, NetworkCoordinator networkCoordinator) {
        super(RUMBLE_TCP_PORT);
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
        WifiNeighbour neighbour = new WifiNeighbour(mmConnectedSocket.getInetAddress().getHostAddress());
        RumbleStateMachine connectionState = protocol.getState(neighbour.getLinkLayerAddress());
        Worker worker = null;
        try {
            connectionState.lock.lock();
            switch (connectionState.getState()) {
                case CONNECTED:
                case CONNECTION_ACCEPTED:
                    Log.d(TAG, "[-] refusing client connection");
                    mmConnectedSocket.close();
                    return;
                case CONNECTION_SCHEDULED:
                    if (NetUtil.getLocalIpAddress().compareTo(neighbour.getLinkLayerAddress()) < 0) {
                        Log.d(TAG, "[-] refusing client connection");
                        mmConnectedSocket.close();
                        return;
                    } else {
                        Log.d(TAG, "[-] cancelling connection " + connectionState.getWorkerID());
                        networkCoordinator.stopWorker(
                                WifiLinkLayerAdapter.LinkLayerIdentifier,
                                connectionState.getWorkerID());
                        break;
                    }
                case NOT_CONNECTED:
                default:
                    break;
            }

            worker = new RumbleUnicastChannel(protocol, new TCPServerConnection(mmConnectedSocket));
            connectionState.connectionAccepted(worker.getWorkerIdentifier());
        } catch(IOException ignore) {
            Log.e(TAG, "[!] Client CON: " + ignore.getMessage());
        } catch (RumbleStateMachine.StateException e) {
            Log.e(TAG,"[!] Rumble TCP State Exception");
        } finally {
            connectionState.lock.unlock();
        }
        EventBus.getDefault().post(new ScannerNeighbourSensed(neighbour));
        if(worker != null)
            networkCoordinator.addWorker(worker);
    }
}
