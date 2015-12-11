/*
 * Copyright (C) 2014 Lucien Loiseau
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
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.MulticastConnection;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.exception.UDPMulticastSocketException;
import org.disrupted.rumble.network.linklayer.wifi.WifiLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.wifi.WifiUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.UnknownHostException;

/**
 * An UDP Multicast Connection is not a one-to-one connection but a one-to-many connection
 * This class is responsible for managing its own neighborhood constituted of the other devices who
 * also send and receive on this multicast IP Address.
 * @author Lucien Loiseau
 */
public class UDPMulticastConnection implements MulticastConnection {


    private static final String TAG = "UDPMulticastConnection";

    private int               udpPort;
    private String            address;
    private InetAddress       multicastAddr;
    private SocketAddress     socketAddress;
    private NetworkInterface  wlan0;
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
        return WifiLinkLayerAdapter.LinkLayerIdentifier;
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

        /*
         * sometimes, when switching from AP mode to Managed (or the way around), the interface
         * may take some time to be fully operational. In this case, joinGroup will throw the
         * ENODEV exception. We thus tests up to 10 times with 500ms sleep to let some time
         * for the interface to be set up.
         */
        socket = tmp;
        int nbretries;
        boolean success = false;
        for(nbretries = 0; nbretries < 10; nbretries++) {
            try {
                wlan0 = WifiUtil.getWlanEth();
                socket.setReuseAddress(true);
                socket.setNetworkInterface(wlan0);
                socket.joinGroup(socketAddress, wlan0);
                success = true;
                break;
            } catch (IOException io) {
                Log.d(TAG, "fail, try again", io);
                try { Thread.sleep(500); } catch (InterruptedException ie) {}
            }
        }
        if(!success)
            throw new UDPMulticastSocketException();
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
                socket.leaveGroup(socketAddress, wlan0);
                socket.close();
            }
        } catch(IOException io) {
            Log.d(TAG, io.getMessage());
            throw new UDPMulticastSocketException();
        } finally {
            multicastLock.release();
        }
    }

    @Override
    public LinkLayerNeighbour getLinkLayerNeighbour() {
        return new UDPMulticastNeighbour(getLinkLayerIdentifier(), address, udpPort);
    }

}
