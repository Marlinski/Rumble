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
import org.disrupted.rumble.util.NetUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;

/**
 * @author Marlinski
 */
public class WifiNeighbour implements LinkLayerNeighbour {

    String remoteAddress;

    public WifiNeighbour(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return WifiManagedLinkLayerAdapter.LinkLayerIdentifier;
    }

    @Override
    public String getLinkLayerAddress() {
        return remoteAddress;
    }

    @Override
    public String getLinkLayerMacAddress() throws NetUtil.NoMacAddressException {
        try {
            return NetUtil.getMacFromArpCache(remoteAddress);
        } catch (Exception e) {
            throw new NetUtil.NoMacAddressException();
        }
    }

    @Override
    public boolean equals(Object o) {
        if(o == null)
            return false;

        if(o instanceof  WifiNeighbour) {
            WifiNeighbour neighbour = (WifiNeighbour) o;
            return remoteAddress.equals(neighbour.remoteAddress);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return remoteAddress.hashCode();
    }
}
