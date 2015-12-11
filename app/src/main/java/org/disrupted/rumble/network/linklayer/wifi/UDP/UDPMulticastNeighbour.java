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

package org.disrupted.rumble.network.linklayer.wifi.UDP;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.wifi.WifiUtil;
import org.disrupted.rumble.util.NetUtil;

/**
 * @author Lucien Loiseau
 */
public class UDPMulticastNeighbour implements LinkLayerNeighbour {


    private String linkLayerIdentifier;
    private String multicastAddress;
    private int port;

    public UDPMulticastNeighbour(String linkLayerIdentifier, String multicastAddress, int port) {
        this.linkLayerIdentifier = linkLayerIdentifier;
        this.multicastAddress = multicastAddress;
        this.port = port;
    }

    @Override
    public boolean isLocal() {
        if(multicastAddress.equals("127.0.0.1"))
            return true;
        if(multicastAddress.equals("0.0.0.0"))
            return true;
        if(multicastAddress.equals(WifiUtil.getIPAddress()))
            return true;

        return false;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return linkLayerIdentifier;
    }

    @Override
    public String getLinkLayerAddress() {
        return multicastAddress+":"+port;
    }

    @Override
    public String getLinkLayerMacAddress() throws NetUtil.NoMacAddressException {
        throw new NetUtil.NoMacAddressException();
    }

    @Override
    public boolean equals(Object o) {
        if(o == null)
            return false;

        if(o instanceof  UDPMulticastNeighbour) {
            UDPMulticastNeighbour neighbour = (UDPMulticastNeighbour) o;
            return multicastAddress.equals(neighbour.multicastAddress);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return multicastAddress.hashCode();
    }

}
