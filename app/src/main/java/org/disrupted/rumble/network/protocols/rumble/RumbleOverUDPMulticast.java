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

package org.disrupted.rumble.network.protocols.rumble;

import android.util.Log;

import org.disrupted.rumble.network.NetworkThread;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.wifi.UDPMulticastConnection;
import org.disrupted.rumble.network.protocols.GenericProtocol;
import org.disrupted.rumble.network.protocols.command.Command;

/**
 * @author Marlinski
 */
public class RumbleOverUDPMulticast extends GenericProtocol implements NetworkThread {

    private static final String TAG = "RumbleOverUDP";

    private UDPMulticastConnection con;
    protected boolean isBeingKilled;

    public RumbleOverUDPMulticast(UDPMulticastConnection con) {
        this.con = con;
    }

    @Override
    public String getProtocolID() {
        return RumbleProtocol.protocolID;
    }
    @Override
    public String getNetworkThreadID() {
        return RumbleProtocol.protocolID+" "+getLinkLayerIdentifier();
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
