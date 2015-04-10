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

package org.disrupted.rumble.network;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.protocols.ProtocolNeighbour;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author Marlinski
 */
public class NeighbourInfo {

    public LinkLayerNeighbour neighbour;
    public Set<String> connectedProtocols;

    public NeighbourInfo(LinkLayerNeighbour neighbour) {
        this.neighbour = neighbour;
        connectedProtocols = new HashSet<String>();
    }

    public void addProtocol(String protocol) {
        connectedProtocols.add(protocol);
    }

    public boolean isConnected() {
        return (connectedProtocols.size() > 0);
    }
}
