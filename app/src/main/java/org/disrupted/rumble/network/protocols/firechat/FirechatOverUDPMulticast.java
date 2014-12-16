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

package org.disrupted.rumble.network.protocols.firechat;

import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.NetworkThread;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.wifi.UDPMulticastConnection;
import org.disrupted.rumble.network.protocols.GenericProtocol;
import org.disrupted.rumble.network.protocols.command.Command;
import org.json.JSONException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * @author Marlinski
 */
public class FirechatOverUDPMulticast extends GenericProtocol implements NetworkThread {


    private static final String TAG = "RumbleOverUDP";

    private static final int PACKET_SIZE = 2048;

    private UDPMulticastConnection con;
    private DatagramPacket         packet;
    private boolean                isBeingKilled;
    private static final FirechatMessageParser parser = new FirechatMessageParser();

    public FirechatOverUDPMulticast() {
        this.con = new UDPMulticastConnection(7576, "239.192.0.0", false, null, null);
        byte[] buffer = new byte[PACKET_SIZE];
        this.packet = new DatagramPacket(buffer,  PACKET_SIZE);
        isBeingKilled = false;
    }

    @Override
    public String getProtocolID() {
        return FirechatProtocol.protocolID;
    }
    @Override
    public String getNetworkThreadID() {
        return FirechatProtocol.protocolID+" "+getLinkLayerIdentifier();
    }
    @Override
    public String getType() {
        return con.getLinkLayerIdentifier();
    }
    @Override
    public String getLinkLayerIdentifier() {
        return con.getLinkLayerIdentifier();
    }

    @Override
    public void run() {
        try {
            con.connect();
        } catch (LinkLayerConnectionException exception) {
            Log.d(TAG, "[!] FAILED: " + getNetworkThreadID() + " " + exception.getMessage());
            return;
        }

        try {

            Log.d(TAG, "[+] CONNECTED: " + getNetworkThreadID());

            /*
             * this one automatically creates two thread, one for processing the command
             * and one for processing the network
             */
            onGenericProcotolConnected();

        } finally {
            if (!isBeingKilled)
                kill();
        }
    }

    @Override
    protected void processingPacketFromNetwork() {
        try {
            while(true) {
                con.receive(packet);
                StatusMessage status;
                try {
                    String jsonString = new String(packet.getData(), 0, packet.getLength());
                    status = parser.networkToStatus(jsonString);
                    if(status.getFileSize() > 0) {
                        Log.d(TAG, "we do not accept attached file yet");
                        continue;
                    }
                } catch (JSONException ignore) {
                    Log.d(TAG, "malformed JSON");
                    continue;
                }

                /*
                 * since we cannot have the mac address of the remote device, we use the IP address
                 * instead.
                 */
                status.addForwarder(packet.getAddress().getHostAddress(), FirechatProtocol.protocolID);
                DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).insertStatus(status, null);
            }
        } catch (IOException e) {
        }
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
    public void stop() {

    }
    @Override
    public void kill() {
        this.isBeingKilled = true;
        try {
            con.disconnect();
        } catch (LinkLayerConnectionException ignore) {
        }
        Log.d(TAG, "[-] ENDED: " + getNetworkThreadID());
    }

}
