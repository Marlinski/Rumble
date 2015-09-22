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

package org.disrupted.rumble.network.linklayer.wifi.UDP;

import android.net.wifi.WifiManager;

import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.MulticastConnection;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.exception.UDPMulticastSocketException;
import org.disrupted.rumble.network.linklayer.wifi.WifiManagedLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.wifi.WifiUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

/**
 * An UDP Multicast Connection is not a one-to-one connection but a one-to-many connection
 * This class is responsible for managing its own neighborhood, that is the other devices who
 * also send and receive on this multicast IP Address. This is achieved by using the DNS-SD
 * service (Bonjour/Avahi)
 *
 * @author Marlinski
 */
public class UDPMulticastConnection implements MulticastConnection {


    private static final String TAG = "UDPMulticastConnection";

    private int               udpPort;
    private String            address;
    private InetAddress       multicastAddr;
    private SocketAddress     socketAddress;
    WifiManager.MulticastLock multicastLock;
    MulticastSocket           socket;

    public UDPMulticastConnection(int port, String address) {
        this.udpPort = port;
        this.address = address;
        multicastLock = WifiUtil.getWifiManager().createMulticastLock("org.disruptedsystems.rumble.port."+udpPort);
    }

    @Override
    public String getConnectionID() {
        return "UDPMulticastConnection";
    }

    @Override
    public String getMulticastAddress() {
        return multicastAddr.getHostAddress();
    }

    @Override
    public String getLinkLayerIdentifier() {
        return WifiManagedLinkLayerAdapter.LinkLayerIdentifier;
    }

    @Override
    public int getLinkLayerPriority() {
        return LINK_LAYER_MIDDLE_PRIORITY;
    }

    @Override
    public void connect() throws LinkLayerConnectionException {
        /*
         * we enable multicast packet over WiFi, it is usually disabled to save battery but we
         * need it to send/receive message
         */
        multicastLock.acquire();


        MulticastSocket tmp = null;
        try {
            multicastAddr = InetAddress.getByName(address);
            socketAddress = new InetSocketAddress(multicastAddr, udpPort);
            tmp = new MulticastSocket(udpPort);
        } catch (UnknownHostException uh) {
            throw  new UDPMulticastSocketException();
        } catch( IOException io) {
            throw  new UDPMulticastSocketException();
        }

        if(tmp == null)
            throw  new UDPMulticastSocketException();

        socket = tmp;
        try {
            socket.joinGroup(multicastAddr);
            socket.setReuseAddress(true);
        } catch ( IOException io) {
            throw  new UDPMulticastSocketException();
        }
    }

    public DatagramPacket receive(DatagramPacket packet) throws IOException, UDPMulticastSocketException {
        if(!multicastLock.isHeld())
            throw  new UDPMulticastSocketException();

        socket.receive(packet);
        if(packet == null)
            throw new IOException();
        return packet;
    }

    public void send(byte[] bytes) throws IOException, UDPMulticastSocketException {
        if(!multicastLock.isHeld())
            throw  new UDPMulticastSocketException();

        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, socketAddress);
        socket.send(packet);
    }

    @Override
    public void disconnect() throws LinkLayerConnectionException {
        try {
            if(socket != null) {
                socket.leaveGroup(multicastAddr);
                socket.close();
            }
        } catch(IOException e) {
            throw new UDPMulticastSocketException();
        }

        multicastLock.release();

    }
}
