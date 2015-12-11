/*
 * Copyright (C) 2014 Lucien Loiseau
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
 * @author Lucien Loiseau
 *
 * This event is sent by NeighbourManager whenever a neighbour has timeout. It is not sent
 * if a channel is still open to this neighbour,
 */
public class NeighbourUnreachable extends NetworkEvent {

    public final LinkLayerNeighbour neighbour;
    public final long reachable_time_nano;
    public final long unreachable_time_nano;

    public NeighbourUnreachable(LinkLayerNeighbour neighbour, long in, long out) {
        this.neighbour = neighbour;
        this.reachable_time_nano = in;
        this.unreachable_time_nano = out;
    }

    @Override
    public String shortDescription() {
        if(neighbour != null)
            return neighbour.getLinkLayerAddress();
        else
            return "";
    }
}
