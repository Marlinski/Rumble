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

package org.disrupted.rumble.network.linklayer;


import android.util.Log;

import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.protocols.Protocol;

/**
 * @author Marlinski
 */
public abstract class Connection implements Runnable{

    private static final String TAG = "Connection";

    protected String macAddress;
    protected String type;
    protected Protocol protocol;
    protected String connectionID;
    protected ConnectionCallback callback;

    public interface ConnectionCallback{
        public void onConnectionFailed(Connection connection, String reason);
        public void onConnectionSucceeded(Connection connection);
        public void onConnectionEnded(Connection connection);
    }

    public Connection(String macAddress, Protocol protocol, String type, ConnectionCallback callback) {
        this.macAddress = macAddress;
        this.protocol = protocol;
        this.type = type;
        this.connectionID = "Generic Connection";
        this.callback = callback;
    }


    public String getType() {          return this.type;    }
    public String getConnectionID() {  return connectionID; }
    public String getMacAddress() {    return macAddress;   }
    public Protocol getProtocol() {    return protocol;     }


    protected void onConnectionFailed(String reason) {
        Log.d(TAG, "[!] FAILED: "+getConnectionID() + "reason: "+reason);
        if(callback != null)
            this.callback.onConnectionFailed(this, reason);
    }

    protected void onConnectionEstablished(String address) {
        Log.d(TAG, "[+] STARTED: "+getConnectionID());
        NetworkCoordinator networkCoordinator = NetworkCoordinator.getInstance();
        if(networkCoordinator != null) {
            networkCoordinator.addProtocol(address, protocol);
        }
        if(callback != null)
            this.callback.onConnectionSucceeded(this);
    }

    protected void onConnectionEnded(String address) {
        Log.d(TAG, "[-] ENDED: "+getConnectionID());
        NetworkCoordinator networkCoordinator = NetworkCoordinator.getInstance();
        if(networkCoordinator != null) {
            networkCoordinator.delProtocol(address, protocol.getProtocolID());
        }
        if(callback != null)
            this.callback.onConnectionEnded(this);
    }

    abstract public void kill();

}
