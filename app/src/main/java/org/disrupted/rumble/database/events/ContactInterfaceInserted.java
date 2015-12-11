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

package org.disrupted.rumble.database.events;

import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Interface;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.protocols.ProtocolChannel;

/**
 * @author Lucien Loiseau
 */
public class ContactInterfaceInserted extends DatabaseEvent {

    public final Contact            contact;
    public final LinkLayerNeighbour neighbour;
    public final ProtocolChannel    channel;

    public ContactInterfaceInserted(Contact contact, LinkLayerNeighbour neighbour, ProtocolChannel channel){
        this.contact   = contact;
        this.neighbour = neighbour;
        this.channel   = channel;
    }

    @Override
    public String shortDescription() {
        if((contact != null) && (neighbour != null))
            return contact.getName() + " ("+contact.getUid()+") -> " +neighbour.getLinkLayerAddress() + "("+channel.getLinkLayerIdentifier()+")" ;
        else
            return "";
    }
}
