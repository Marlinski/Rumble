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

package org.disrupted.rumble.network.linklayer.wifi.TCP;

import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.network.Worker;
import org.disrupted.rumble.network.events.ScannerNeighbourSensed;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.wifi.WifiLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.wifi.WifiNeighbour;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import de.greenrobot.event.EventBus;

/**
 * This class implements a simple TCP Server. Whenever a client connects, the abstract method
 * onClientConnected is called with the socket of the connected client as a parameter.
 *
 * To use this class, simply extends it and implements the onClientConnected method. It is
 * intented to be used by a ProtocolWorker such as RumbleOverTCP or something alike.
 *
 * @author Lucien Loiseau
 */
public abstract class TCPServer implements Worker {

    private static final String TAG = "TCPServer";

    protected ServerSocket mmServerSocket;
    protected int mmServerPort;
    private boolean working;

    public TCPServer(int port) {
        this.mmServerPort = port;
        this.working = false;
    }

    @Override
    public String getLinkLayerIdentifier() {
        // todo how to make it independant ?
        return WifiLinkLayerAdapter.LinkLayerIdentifier;
    }

    @Override
    public String getWorkerIdentifier() {
        return "TCPServer";
    }

    @Override
    public boolean isWorking() {
        return working;
    }

    @Override
    public void cancelWorker() {
        if(working) {
            Log.d(TAG, "[!] should not call cancelWorker() on a working Worker, call stopWorker() instead !");
            stopWorker();
        }
    }

    @Override
    public void startWorker() {
        if(working)
            return;
        working = true;

        ServerSocket tmp = null;
        try {
            tmp = new ServerSocket(mmServerPort);
        } catch (IOException e) {
            Log.d(TAG, "cannot open ServerSocket on port " + mmServerPort);
            return;
        }

        mmServerSocket = tmp;
        if(tmp == null){
            Log.d(TAG, "cannot open ServerSocket on port " + mmServerPort);
            return;
        }

        try {
            while(true) {
                Socket mmConnectedSocket = mmServerSocket.accept();
                if (mmConnectedSocket != null) {
                    Log.d(TAG, "[+] Client connected");

                    LinkLayerNeighbour neighbour = new WifiNeighbour(mmConnectedSocket.getInetAddress().getHostAddress());

                    onClientConnected(mmConnectedSocket);

                    EventBus.getDefault().post(new ScannerNeighbourSensed(neighbour));
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "[-] ENDED "+getWorkerIdentifier());
        } finally {
            stopWorker();
        }
    }

    abstract protected void onClientConnected(Socket mmConnectedSocket);

    @Override
    public void stopWorker() {
        if(!working)
            return;
        working = false;

        try {
            mmServerSocket.close();
        } catch (Exception ignore) {
        }
    }
}
