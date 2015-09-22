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

import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;
import org.disrupted.rumble.network.linklayer.wifi.UDP.UDPMulticastNeighbour;
import org.disrupted.rumble.network.linklayer.wifi.WifiNeighbour;

import java.util.Iterator;
import java.util.Set;

/**
 * This class summarize a contact or neighbour information to be displayed by the UI
 * @author Marlinski
 */
public class NeighbourInfo {

    public Contact contact;
    public Set<LinkLayerNeighbour> reachable;
    public Set<LinkLayerNeighbour> connected;

    public NeighbourInfo(Contact contact, Set<LinkLayerNeighbour> channels) {
        this.contact  = contact;
        this.connected = channels;
    }

    public String getFirstName() {
        if(contact == null) {
            if(reachable == null)
                return "???";
            else {
                Iterator<LinkLayerNeighbour> it = reachable.iterator();
                if(it.hasNext()) {
                    LinkLayerNeighbour neighbour = it.next();
                    if(neighbour instanceof BluetoothNeighbour)
                        return ((BluetoothNeighbour)neighbour).getBluetoothDeviceName();
                    if(neighbour instanceof WifiNeighbour)
                        return ((WifiNeighbour)neighbour).getLinkLayerAddress();
                    if(neighbour instanceof UDPMulticastNeighbour)
                        return ((UDPMulticastNeighbour)neighbour).getLinkLayerAddress();
                    else
                        return neighbour.getLinkLayerAddress();
                }
                return "";
            }
        } else {
            return contact.getName();
        }
    }

    public String getSecondName() {
        if(contact == null) {
            if(reachable == null)
                return "???";
            else {
                Iterator<LinkLayerNeighbour> it = reachable.iterator();
                if(it.hasNext()) {
                    LinkLayerNeighbour neighbour = it.next();
                    if (neighbour instanceof BluetoothNeighbour)
                        return ((BluetoothNeighbour) neighbour).getLinkLayerAddress();
                    if (neighbour instanceof WifiNeighbour)
                        return ((WifiNeighbour) neighbour).getMacAddressFromARP();
                }
            }
        } else {
            return contact.getUid();
        }
        return "";
    }

    public boolean isReachable(String linkLayerIdentifier) {
        for(LinkLayerNeighbour channel : reachable) {
            if(channel.getLinkLayerIdentifier().equals(linkLayerIdentifier))
                return true;
        }
        return false;
    }

    public boolean isConnected(String linkLayerIdentifier) {
        for(LinkLayerNeighbour channel : connected) {
            if(channel.getLinkLayerIdentifier().equals(linkLayerIdentifier))
                return true;
        }
        return false;
    }

}
