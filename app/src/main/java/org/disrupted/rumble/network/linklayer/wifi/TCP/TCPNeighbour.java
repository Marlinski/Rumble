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

package org.disrupted.rumble.network.linklayer.wifi.TCP;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.util.NetUtil;

import java.net.InetAddress;

/**
 * @author Marlinski
 */
public class TCPNeighbour implements LinkLayerNeighbour {

    private String macAddress;
    private String ipAddress;

    public TCPNeighbour(String address) {
        this.ipAddress = address;
        try {
            this.macAddress = NetUtil.getMacFromArpCache(address);
        } catch (Exception e) {
            this.macAddress = null;
        }
    }

    @Override
    public String getLinkLayerIdentifier() {
        return "TCP";
    }

    @Override
    public String getLinkLayerAddress() {
        return ipAddress;
    }

}
