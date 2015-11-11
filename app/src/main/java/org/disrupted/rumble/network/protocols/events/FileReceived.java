/*
 * Copyright (C) 2014 Disrupted Systems
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

/**
 * @author Marlinski
 */
public class FileReceived extends NetworkEvent {

    public String filename;
    public String uuid;
    public String sender;
    public String protocolID;
    public String linkLayerIdentifier;
    public long size;
    public long duration;

    public FileReceived(String filename, String uuid, String sender, String protocolID, String linkLayerIdentifier, long size, long duration) {
        this.filename = filename;
        this.uuid = uuid;
        this.sender = sender;
        this.protocolID = protocolID;
        this.linkLayerIdentifier = linkLayerIdentifier;
        this.size = size;
        this.duration = duration;
    }

    @Override
    public String shortDescription() {
        return filename+": size="+size+" bytes received in "+duration+" nanoseconds";
    }
}
