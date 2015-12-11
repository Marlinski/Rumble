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

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.exception.NullSocketException;
import org.disrupted.rumble.network.linklayer.exception.SocketAlreadyClosedException;
import org.disrupted.rumble.network.linklayer.wifi.WifiLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.wifi.WifiNeighbour;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

/**
 * @author Lucien Loiseau
 */
public abstract class TCPConnection implements UnicastConnection {

    private static final String TAG = "TCPConnection";

    protected String        remoteAddress;
    protected InetAddress   remoteInetAddress;
    protected Socket        mmConnectedSocket;
    protected InputStream   inputStream;
    protected OutputStream  outputStream;
    protected boolean       socketConnected;
    protected WifiNeighbour wifiNeighbour;

    public TCPConnection(String remoteAddress) {
        this.remoteAddress = remoteAddress;
        this.remoteInetAddress = null;
        this.mmConnectedSocket = null;
        this.wifiNeighbour     = new WifiNeighbour(remoteAddress);
    }

    @Override
    public String getLinkLayerIdentifier() {
        return WifiLinkLayerAdapter.LinkLayerIdentifier;
    }

    @Override
    public int getLinkLayerPriority() {
        return LINK_LAYER_HIGH_PRIORITY;
    }

    @Override
    public String getRemoteLinkLayerAddress() {
        return remoteAddress;
    }

    @Override
    public LinkLayerNeighbour getLinkLayerNeighbour() {
        return new WifiNeighbour(wifiNeighbour);
    }

    @Override
    public InputStream getInputStream() throws InputOutputStreamException {
        try {
            InputStream input = mmConnectedSocket.getInputStream();
            if (input == null)
                throw new InputOutputStreamException();
            return input;
        } catch (IOException e) {
            throw new InputOutputStreamException();
        }
    }

    @Override
    public OutputStream getOutputStream() throws InputOutputStreamException {
        try {
            OutputStream output = mmConnectedSocket.getOutputStream();
            if (output == null)
                throw new InputOutputStreamException();
            return output;
        } catch (IOException e) {
            throw new InputOutputStreamException();
        }
    }


    @Override
    public void disconnect() throws LinkLayerConnectionException {
        try {
            mmConnectedSocket.close();
        } catch (IOException e) {
            throw new SocketAlreadyClosedException();
        } catch (NullPointerException e) {
            throw new NullSocketException();
        } finally {
        }
    }

}
