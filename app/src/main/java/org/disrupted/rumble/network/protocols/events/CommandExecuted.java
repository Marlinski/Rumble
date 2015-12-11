/*
 * Copyright (C) 2014 Lucien Loiseau
 * This file is part of Rumble.
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
 * You should have received a copy of the GNU General Public License along
 * with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.network.protocols.events;

import org.disrupted.rumble.network.events.NetworkEvent;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.command.Command;

/**
 * @author Lucien Loiseau
 */
public class CommandExecuted extends NetworkEvent {

    public ProtocolChannel worker;
    public Command command;
    public boolean success;

    public CommandExecuted(ProtocolChannel worker, Command command, boolean success) {
        this.worker = worker;
        this.command = command;
        this.success = success;
    }

    @Override
    public String shortDescription() {
        if((worker != null) && (command != null))
            return worker.getWorkerIdentifier()+" ("+command.getClass().getSimpleName()+")";
        else
            return "";
    }
}
