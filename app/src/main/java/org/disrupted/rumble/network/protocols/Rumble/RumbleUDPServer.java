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

import org.disrupted.rumble.network.linklayer.wifi.UDPServer;
import org.disrupted.rumble.network.protocols.command.Command;

/**
 * @author Marlinski
 */
public class RumbleUDPServer extends UDPServer {

    public RumbleUDPServer() {
        super(RumbleWifiConfiguration.SERVER_PORT);
    }

    @Override
    public String getConnectionID() {
        return "RumbleUDPServer";
    }

    @Override
    public String getProtocolID() {
        return "Rumble";
    }

    @Override
    public boolean isCommandSupported(String commandName) {
        return false;
    }

    @Override
    public boolean executeCommand(Command command) {
        return false;
    }

    @Override
    public void onConnected() {

    }
}
