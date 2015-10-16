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

package org.disrupted.rumble.network.events;

import org.disrupted.rumble.network.events.NetworkEvent;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;

/**
 * @author Marlinski
 *
 * This event is sent by NeighbourManager whenever a neighbour has timeout. It is not sent
 * if a channel is still open to this neighbour,
 */
public class NeighbourUnreachable extends NetworkEvent {

    public final LinkLayerNeighbour neighbour;

    public NeighbourUnreachable(LinkLayerNeighbour neighbour) {
        this.neighbour = neighbour;
    }

    @Override
    public String shortDescription() {
        if(neighbour != null)
            return neighbour.getLinkLayerAddress();
        else
            return "";
    }
}
