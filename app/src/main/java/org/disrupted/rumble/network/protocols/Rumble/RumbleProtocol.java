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


import org.disrupted.rumble.network.protocols.Protocol;
import org.disrupted.rumble.network.protocols.command.ProtocolCommand;
import org.disrupted.rumble.network.protocols.command.SendStatusMessageCommand;

/**
 * @author Marlinski
 */
public class RumbleProtocol extends Protocol {

    private static final String TAG = "RumbleMessage";
    public static final String ID  = "Rumble";

    public RumbleProtocol() {
        this.protocolID = ID;
    }

    public Protocol newInstance() {
        return new RumbleProtocol();
    }

    @Override
    public boolean isCommandSupported(String commandName) {
        if(commandName.equals(SendStatusMessageCommand.COMMAND_NAME))
            return true;
        return false;
    }

    @Override
    public boolean onPacketReceived(byte[] bytes, int size) {
        return false;
    }

    @Override
    public boolean onCommandReceived(ProtocolCommand command) {
        return false;
    }
}
