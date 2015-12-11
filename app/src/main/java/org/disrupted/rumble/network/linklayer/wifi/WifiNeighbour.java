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

package org.disrupted.rumble.network.linklayer.wifi;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.util.NetUtil;

/**
 * @author Lucien Loiseau
 */
public class WifiNeighbour implements LinkLayerNeighbour {

    String remoteIPAddress;
    String remoteMacAddress;

    public WifiNeighbour(String remoteIPAddress) {
        this.remoteIPAddress = remoteIPAddress;
        try {
            this.remoteMacAddress = NetUtil.getMacFromArpCache(remoteIPAddress);
        } catch (Exception e) {
            this.remoteMacAddress = null;
        }
    }

    public WifiNeighbour(WifiNeighbour neighbour) {
        this.remoteIPAddress  = neighbour.remoteIPAddress;
        this.remoteMacAddress = neighbour.remoteMacAddress;
    }

    @Override
    public boolean isLocal() {
        if(remoteIPAddress.equals("127.0.0.1"))
            return true;
        if(remoteIPAddress.equals("0.0.0.0"))
            return true;
        if(remoteIPAddress.equals(WifiUtil.getIPAddress()))
            return true;

        return false;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return WifiLinkLayerAdapter.LinkLayerIdentifier;
    }

    @Override
    public String getLinkLayerAddress() {
        return remoteIPAddress;
    }

    @Override
    public String getLinkLayerMacAddress() throws NetUtil.NoMacAddressException {
        try {
            if(this.remoteMacAddress == null)
                return NetUtil.getMacFromArpCache(remoteIPAddress);
            else
                return remoteMacAddress;
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
            return remoteIPAddress.equals(neighbour.remoteIPAddress);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return remoteIPAddress.hashCode();
    }
}
