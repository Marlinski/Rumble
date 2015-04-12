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

package org.disrupted.rumble.network.protocols.rumble.workers;

import android.util.Log;

import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.wifi.UDPMulticastConnection;
import org.disrupted.rumble.network.protocols.ProtocolNeighbour;
import org.disrupted.rumble.network.protocols.ProtocolWorker;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;

import java.net.DatagramPacket;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public class RumbleOverUDPMulticast extends ProtocolWorker {

    private static final String TAG = "RumbleOverUDP";

    private static final String NSD_SERVICE_NAME = "Rumble";
    private static final String NSD_SERVICE_TYPE = "_rumble._udp.";
    private static final String MULTICAST_ADDRESS = "239.192.0.0";
    private static final int    MULTICAST_UDP_PORT = 9715;
    private static final int    PACKET_SIZE = 2048;

    private UDPMulticastConnection con;
    private DatagramPacket packet;
    private boolean working;

    public RumbleOverUDPMulticast() {
        this.con = new UDPMulticastConnection(
                MULTICAST_UDP_PORT,
                MULTICAST_ADDRESS, false, null, null);
        byte[] buffer = new byte[PACKET_SIZE];
        this.packet = new DatagramPacket(buffer,  PACKET_SIZE);
        this.working = false;
    }

    // todo : a implementer
    public List<ProtocolNeighbour> getUDPNeighbourList() {
        List<ProtocolNeighbour> ret = new LinkedList<ProtocolNeighbour>();
        return ret;
    }

    @Override
    public boolean isWorking() {
        return working;
    }

    @Override
    public String getProtocolIdentifier() {
        return RumbleProtocol.protocolID;
    }

    @Override
    public String getWorkerIdentifier() {
        return getProtocolIdentifier()+" "+con.getConnectionID();
    }

    @Override
    public LinkLayerConnection getLinkLayerConnection() {
        return con;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return con.getLinkLayerIdentifier();
    }

    @Override
    public void cancelWorker() {
        return;
    }

    @Override
    public void startWorker() {
        if(working)
            return;
        working = true;

        try {
            con.connect();
        } catch (LinkLayerConnectionException exception) {
            Log.d(TAG, "[!] FAILED: " + getWorkerIdentifier() + " " + exception.getMessage());
            return;
        }

        try {
            Log.d(TAG, "[+] CONNECTED: " + getWorkerIdentifier());

            /*
             * this one automatically creates two thread, one for processing the command
             * and one for processing the network
             */
            onWorkerConnected();

        } finally {
            stopWorker();
        }
    }

    @Override
    protected void processingPacketFromNetwork() {
    }

    @Override
    public boolean isCommandSupported(String commandName) {
        return false;
    }

    @Override
    protected boolean onCommandReceived(Command command) {
        return false;
    }


    @Override
    public void stopWorker() {
        if(!working)
            return;

        this.working = false;
        try {
            con.disconnect();
        } catch (LinkLayerConnectionException ignore) {
        }
        Log.d(TAG, "[-] ENDED: " + getWorkerIdentifier());
    }
}
