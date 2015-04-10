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

package org.disrupted.rumble.network.protocols;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public abstract class ProtocolNeighbour {

    List<LinkLayerNeighbour> linkLayerPresence;

    abstract public String getProtocolIdentifier();

    abstract public String getNeighbourIdentifier();

    public ProtocolNeighbour(LinkLayerNeighbour linkLayerNeighbour) {
        linkLayerPresence = new LinkedList<LinkLayerNeighbour>();
        linkLayerPresence.add(linkLayerNeighbour);
    }

    // todo return a copy
    public List<LinkLayerNeighbour> getLinkLayerPresence() {
        return linkLayerPresence;
    }

    public void clear() {
        linkLayerPresence.clear();
    }

    public boolean reachableOn(String linkLayerIdentifier) {
        Iterator<LinkLayerNeighbour> it = linkLayerPresence.iterator();
        while(it.hasNext()) {
            LinkLayerNeighbour neighbour = it.next();
            if(neighbour != null)
                if(neighbour.getLinkLayerIdentifier().equals(linkLayerIdentifier))
                    return true;
        }
        return false;
    }

}
