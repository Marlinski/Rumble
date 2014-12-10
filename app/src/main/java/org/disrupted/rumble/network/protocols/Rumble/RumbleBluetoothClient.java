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

package org.disrupted.rumble.network.protocols.Rumble;

import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothClient;
import org.disrupted.rumble.network.protocols.command.Command;

import java.io.IOException;
import java.util.UUID;

/**
 * @author Marlinski
 */
public class RumbleBluetoothClient extends BluetoothClient {

    private static final String TAG = "RumbleBluetoothClient";

    public RumbleBluetoothClient(String remoteMacAddress){
        super(remoteMacAddress, RumbleBTConfiguration.RUMBLE_BT_UUID_128, RumbleBTConfiguration.RUMBLE_BT_STR, false);
    }

    @Override
    public String getConnectionID() {
        return "ConnectTO Rumble: "+this.macAddress+":"+bt_service_uuid.toString();
    }

    @Override
    public String getProtocolID() {
        return "Rumble";
    }

    @Override
    protected void initializeProtocol() {
    }

    @Override
    protected void destroyProtocol() {
    }

    @Override
    public boolean isCommandSupported(String commandName) {
        return false;
    }

    @Override
    protected void processingPacketFromNetwork() throws IOException {
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
    }
}
