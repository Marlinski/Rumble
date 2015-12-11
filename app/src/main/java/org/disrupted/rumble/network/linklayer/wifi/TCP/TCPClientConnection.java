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

import org.disrupted.rumble.network.linklayer.exception.ConnectionFailedException;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.exception.NullSocketException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * @author Lucien Loiseau
 */
public class TCPClientConnection extends TCPConnection {

    private static final String TAG = "TCPClient";

    private int remotePort;

    public TCPClientConnection(String remoteAddress, int remotePort) {
        super(remoteAddress);
        this.remotePort = remotePort;
    }

    @Override
    public String getConnectionID() {
        return "TCP ClientConnection: "+remoteAddress+":"+remotePort;
    }



    @Override
    public void connect() throws LinkLayerConnectionException {
        try {
            remoteInetAddress = InetAddress.getByName(remoteAddress);
            mmConnectedSocket = new Socket(remoteAddress, remotePort);

            if (mmConnectedSocket == null)
                throw new NullSocketException();
        } catch (UnknownHostException e) {
            throw new ConnectionFailedException(remoteAddress+":"+remotePort);
        } catch (IOException e) {
            throw new ConnectionFailedException(remoteAddress+":"+remotePort);
        }

        socketConnected = true;

        try {
            inputStream = mmConnectedSocket.getInputStream();
            outputStream = mmConnectedSocket.getOutputStream();
        } catch (IOException e) {
            throw new InputOutputStreamException();
        }
    }

}
