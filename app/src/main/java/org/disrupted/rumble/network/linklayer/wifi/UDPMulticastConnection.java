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

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.exception.UDPMulticastSocketException;

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
 * This class is responsible of managing its own neighborhood, that is the other device who
 * also send and receive on this multicast IP Address. This is achieved by using the DNS-SD
 * service (Bonjour/Avahi)
 *
 * @author Marlinski
 */
public class UDPMulticastConnection implements LinkLayerConnection {


    private static final String TAG = "UDPMulticastConnection";

    private int               udpPort;
    private String            address;
    private InetAddress       multicastAddr;
    private SocketAddress     socketAddress;
    WifiManager.MulticastLock multicastLock;
    MulticastSocket           socket;

    /*
     * only compatible with API 16 !
     */
    private NsdServiceInfo serviceInfo;
    private NsdManager mNsdManager;
    private NsdManager.RegistrationListener mRegistrationListener;
    private String nsdServiceName;
    private String nsdServiceType;
    private boolean registerService;
    private boolean registered;

    @Override
    public String getConnectionID() {
        return "UDPMulticastConnection";
    }

    @Override
    public InputStream getInputStream() throws IOException, InputOutputStreamException {
        return null;
    }

    @Override
    public OutputStream getOutputStream() throws IOException, InputOutputStreamException {
        return null;
    }

    @Override
    public LinkLayerNeighbour getLinkLayerNeighbour() {
        return new UDPMulticastNeighbour("Multicast", udpPort, address);
    }

    @Override
    public String getRemoteLinkLayerAddress() {
        return null;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return WifiManagedLinkLayerAdapter.LinkLayerIdentifier;
    }

    public UDPMulticastConnection(int port, String address, boolean registerService, String nsdServiceName, String nsdServiceType) {
        this.udpPort = port;
        this.address = address;
        this.registerService = registerService;
        this.nsdServiceName = nsdServiceName;
        this.nsdServiceType = nsdServiceType;
        multicastLock = WifiUtil.getWifiManager().createMulticastLock("org.disruptedsystems.rumble.port."+udpPort);
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

        if(registerService && (nsdServiceName!=null) && (nsdServiceType != null)) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
                registerService();
                registered = true;
            }
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

        if(!registered)
            return;

        if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
            //mNsdManager.unregisterService(mRegistrationListener);
        }
    }


    /*override
     * The following code only deals with DNS-SD registration / discovery
     * and requires API level 16 to works.
     *
     * Since service discovery is an heavy tasks (and thus drain the battery),
     * we may consider using our own strategy instead, trickle based like Bluetooth
     * todo: do testings
     */
    public void registerService() {
        if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
            initializeRegistrationListener();

            serviceInfo  = new NsdServiceInfo();
            serviceInfo.setServiceName(nsdServiceName);
            serviceInfo.setServiceType(nsdServiceType);
            serviceInfo.setPort(udpPort);

            mNsdManager = (NsdManager) RumbleApplication.getContext().getSystemService(Context.NSD_SERVICE);
            mNsdManager.registerService(
                    serviceInfo,
                    NsdManager.PROTOCOL_DNS_SD,
                    mRegistrationListener);
        }
    }

    public void initializeRegistrationListener() {
        if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
            mRegistrationListener = new NsdManager.RegistrationListener() {

                @Override
                public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
                        Log.d(TAG, "[+] Service registered");
                    }
                }

                @Override
                public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                }

                @Override
                public void onServiceUnregistered(NsdServiceInfo arg0) {
                    Log.d(TAG, "[-] Service unregistered");
                }

                @Override
                public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                }
            };
        }
    }


}
