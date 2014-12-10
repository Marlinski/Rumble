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

package org.disrupted.rumble.network.linklayer.wifi;

import android.util.Log;

import org.disrupted.rumble.network.linklayer.Connection;
import org.disrupted.rumble.network.protocols.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * @author Marlinski
 */
public abstract class UDPServer implements Connection, Protocol {

    private static final String TAG = "UDPServer";

    protected int BUFFER_SIZE = 1024;
    protected int udpPort;
    protected DatagramSocket mServerSocket;

    public UDPServer(int port) {
        this.udpPort = port;
    }

    @Override
    public String getType() {
        return WifiManagedLinkLayerAdapter.LinkLayerIdentifier;
    }

    @Override
    public void run() {
        DatagramSocket tmp = null;

        try {
            tmp = new DatagramSocket(udpPort);
        }
        catch(SocketException e){
            Log.e(TAG, "[!] cannot open UDP Socket on port: "+udpPort+" ("+e.toString()+")");
            return;
        }

        mServerSocket = tmp;
        if(tmp == null){
            Log.d(TAG, "[!] cannot open UDP Socket on port: "+udpPort);
            return;
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, BUFFER_SIZE);
        try {
            while(true) {
                mServerSocket.receive(packet);
            }
        }
        catch(IOException ignore) {
        }

        mServerSocket.close();
    }

    @Override
    public void stop() {

    }

    @Override
    public void kill() {
        mServerSocket.close();
    }

}
