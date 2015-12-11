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

import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.exception.NullSocketException;

import java.io.IOException;
import java.net.Socket;

/**
 * @author Lucien Loiseau
 */
public class TCPServerConnection extends TCPConnection {

    private static final String TAG = "TCPServerConnection";

    public TCPServerConnection(Socket socket) {
        super(socket.getInetAddress().getHostAddress());
        this.mmConnectedSocket = socket;
    }

    @Override
    public String getConnectionID() {
        return "TCP ServerConnection: "+remoteAddress;
    }


    @Override
    public void connect() throws LinkLayerConnectionException {
        if (mmConnectedSocket == null)
            throw new NullSocketException();

        try {
            inputStream = mmConnectedSocket.getInputStream();
            outputStream = mmConnectedSocket.getOutputStream();
        } catch (IOException e) {
            throw new InputOutputStreamException();
        }

        socketConnected = true;
    }
}
