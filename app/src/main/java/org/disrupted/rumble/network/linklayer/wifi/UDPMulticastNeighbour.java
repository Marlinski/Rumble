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

package org.disrupted.rumble.network.linklayer.wifi;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;

import java.net.InetAddress;

/**
 * @author Marlinski
 */
public class UDPMulticastNeighbour extends LinkLayerNeighbour {

    int port;
    InetAddress address;

    public UDPMulticastNeighbour(int port, InetAddress address) {
        super(address.getHostAddress());
        this.port = port;
        this.address = address;
    }

    @Override
    public String getLinkLayerType() {
        return null;
    }

    @Override
    public String getLinkLayerName() {
        return null;
    }

    @Override
    public String getLinkLayerAddress() {
        return super.getLinkLayerAddress();
    }
}
