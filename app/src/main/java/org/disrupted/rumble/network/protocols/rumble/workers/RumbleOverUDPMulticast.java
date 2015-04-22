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
import org.disrupted.rumble.network.linklayer.exception.UDPMulticastSocketException;
import org.disrupted.rumble.network.linklayer.wifi.UDPMulticastConnection;
import org.disrupted.rumble.network.protocols.ProtocolNeighbour;
import org.disrupted.rumble.network.protocols.ProtocolWorker;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.events.CommandExecuted;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.LinkedList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class RumbleOverUDPMulticast extends ProtocolWorker {

    private static final String TAG = "RumbleOverUDP";

    public static final int    PACKET_SIZE = 2048;

    private boolean working;

    public RumbleOverUDPMulticast(RumbleProtocol protocol, UDPMulticastConnection con) {
        super(protocol, con);
        this.working = false;
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
            onWorkerConnected();

        } finally {
            stopWorker();
        }
    }

    @Override
    protected void processingPacketFromNetwork() {
        try {
            while(true) {
                byte[] buffer = new byte[PACKET_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer,  PACKET_SIZE);
                ((UDPMulticastConnection)con).receive(packet);
            }
        } catch (IOException e) {
        } catch (UDPMulticastSocketException e) {
        }
    }

    @Override
    protected boolean onCommandReceived(Command command) {
        EventBus.getDefault().post(new CommandExecuted(this, command, false));
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
