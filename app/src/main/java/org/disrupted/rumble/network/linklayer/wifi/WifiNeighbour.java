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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;

/**
 * @author Marlinski
 */
public class WifiNeighbour implements LinkLayerNeighbour {

    InetAddress address;

    public WifiNeighbour(InetAddress address) {
        this.address = address;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return WifiManagedLinkLayerAdapter.LinkLayerIdentifier;
    }

    @Override
    public String getLinkLayerAddress() {
        return address.getHostAddress();
    }

    public String getMacAddressFromARP() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/net/arp"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] splitted = line.split(" +");
                if (splitted != null && splitted.length >= 4 && address.getHostAddress().equals(splitted[0])) {
                    // Basic sanity check
                    String mac = splitted[3];
                    if (mac.matches("..:..:..:..:..:..")) {
                        return mac;
                    } else {
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
            } catch (IOException ignore) {
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if(o == null)
            return false;

        if(o instanceof  WifiNeighbour) {
            WifiNeighbour neighbour = (WifiNeighbour) o;
            return address.getHostAddress().equals(neighbour.address.getHostAddress());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return address.getHostAddress().hashCode();
    }
}
