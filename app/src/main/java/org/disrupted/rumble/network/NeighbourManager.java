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

package org.disrupted.rumble.network;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStopped;
import org.disrupted.rumble.network.linklayer.events.NeighborhoodChanged;
import org.disrupted.rumble.network.linklayer.events.NeighbourReachable;
import org.disrupted.rumble.network.linklayer.events.NeighbourUnreachable;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.events.ContactInformationReceived;
import org.disrupted.rumble.network.protocols.events.NeighbourConnected;
import org.disrupted.rumble.network.protocols.events.NeighbourDisconnected;
import org.disrupted.rumble.network.services.events.ContactConnected;
import org.disrupted.rumble.network.services.events.ContactDisconnected;
import org.disrupted.rumble.network.services.events.ContactReachable;
import org.disrupted.rumble.network.services.events.ContactUnreachable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.greenrobot.event.EventBus;

/*
 * This class keeps an up-do-date view of the neighborhood
 * - the physical neighborhood comprised of all the nodes discovered by the scanners
 * - the connected neighborhood comprised of all the nodes connected with the protocols
 * - the contact neighborhood which is a rearrangement of the physical and connected neighborhood
 *   relevant to a certain contact
 */
public class NeighbourManager {

    private static final String TAG = "NeighbourManager";

    private final Object managerLock = new Object();
    private HashMap<String, LinkLayerNeighbour>    physicalNeighbours; // linkLayerAddress to LinkLayerNeighbour Object
    private HashMap<String, ProtocolChannel>       connectedProtocols; // workerID to the corresponding ProtocolWorker

    private HashMap<Contact, Set<LinkLayerNeighbour>> contactReachable;
    private HashMap<Contact, Set<ProtocolChannel>>    contactConnected;

    public NeighbourManager() {
        physicalNeighbours = new HashMap<String, LinkLayerNeighbour>();
        connectedProtocols = new HashMap<String, ProtocolChannel>();
        contactReachable   = new HashMap<Contact, Set<LinkLayerNeighbour>>();
        contactConnected   = new HashMap<Contact, Set<ProtocolChannel>>();
    }

    public void startMonitoring() {
        EventBus.getDefault().register(this);
    }

    public void stopMonitoring() {
        synchronized (managerLock) {
            if (EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);

            physicalNeighbours.clear();
            connectedProtocols.clear();
            for (Map.Entry<Contact, Set<LinkLayerNeighbour>> entry : contactReachable.entrySet()) {
                if (entry.getValue() != null)
                    entry.getValue().clear();
            }
            contactReachable.clear();
            for (Map.Entry<Contact, Set<ProtocolChannel>> entry : contactConnected.entrySet()) {
                if (entry.getValue() != null)
                    entry.getValue().clear();
            }
            contactConnected.clear();
        }
    }


    /*
     * managing the physical neighborhood
     */
    public void onEvent(NeighbourReachable event) {
        synchronized (managerLock) {
            if (physicalNeighbours.get(event.neighbour.getLinkLayerAddress()) != null)
                return;

            physicalNeighbours.put(event.neighbour.getLinkLayerAddress(), event.neighbour);

            Set<Contact> contacts = DatabaseFactory.getContactDatabase(RumbleApplication.getContext())
                    .getContactsUsingMacAddress(event.neighbour.getLinkLayerAddress());
            if(!contacts.isEmpty()) {
                for(Contact contact : contacts) {
                    Set<LinkLayerNeighbour> potentialChannels = contactReachable.get(contact);
                    if(potentialChannels == null) {
                        potentialChannels = new HashSet<LinkLayerNeighbour>();
                        potentialChannels.add(event.neighbour);
                        contactReachable.put(contact, potentialChannels);
                        EventBus.getDefault().post(new ContactReachable(contact, event.neighbour));
                    } else {
                        potentialChannels.add(event.neighbour);
                    }
                }
            }
        }
        EventBus.getDefault().post(new NeighborhoodChanged());
    }

    public void onEvent(NeighbourUnreachable event) {
        synchronized (managerLock) {
            physicalNeighbours.remove(event.neighbour.getLinkLayerAddress());

            // we also remove this worker from the contactReachable if any
            for (Iterator<Map.Entry<Contact, Set<LinkLayerNeighbour>>> itmap = contactReachable.entrySet().iterator(); itmap.hasNext(); ) {
                Map.Entry<Contact, Set<LinkLayerNeighbour>> entry = itmap.next();

                Set<LinkLayerNeighbour> channels = entry.getValue();
                channels.remove(event.neighbour);

                if(channels.isEmpty()) {
                    EventBus.getDefault().post(new ContactUnreachable(entry.getKey()));
                    itmap.remove();
                }
            }
        }
        EventBus.getDefault().post(new NeighborhoodChanged());
    }

    public void onEvent(LinkLayerStopped event) {
        synchronized (managerLock) {
            Iterator<Map.Entry<String, LinkLayerNeighbour>> it = physicalNeighbours.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String,LinkLayerNeighbour> entry = it.next();
                LinkLayerNeighbour neighbour = entry.getValue();
                if (neighbour != null) {
                    if (neighbour.getLinkLayerIdentifier().equals(event.linkLayerIdentifier)) {
                        it.remove();
                    }
                } else {
                    it.remove();
                }
            }
        }
        EventBus.getDefault().post(new NeighborhoodChanged());
    }

    /*
     * managing the connected and rumble neighborhood
     */
    public void onEvent(NeighbourConnected event) {
        synchronized (managerLock) {
            connectedProtocols.put(event.worker.getWorkerIdentifier(), event.worker);
        }
        EventBus.getDefault().post(new NeighborhoodChanged());
    }

    public void onEvent(NeighbourDisconnected event) {
        synchronized (managerLock) {
            connectedProtocols.remove(event.worker.getWorkerIdentifier());

            // we also remove this worker from the contactConnected if any
            for (Iterator<Map.Entry<Contact, Set<ProtocolChannel>>> itmap = contactConnected.entrySet().iterator(); itmap.hasNext(); ) {
                Map.Entry<Contact, Set<ProtocolChannel>> entry = itmap.next();

                Set<ProtocolChannel> channels = entry.getValue();
                channels.remove(event.worker.getWorkerIdentifier());

                if(channels.isEmpty()) {
                    EventBus.getDefault().post(new ContactDisconnected(entry.getKey()));
                    itmap.remove();
                }
            }
        }
        EventBus.getDefault().post(new NeighborhoodChanged());
    }

    public void onEvent(ContactInformationReceived event) {
        Set<ProtocolChannel> channels = contactConnected.get(event.contact);
        if(channels == null) {
            channels = new HashSet<ProtocolChannel>();
            channels.add(event.channel);
            contactConnected.put(event.contact, channels);
            EventBus.getDefault().post(new ContactConnected(event.contact, event.channel));
        } else {
            channels.add(event.channel);
        }

    }

    /*
     * public method
     */
    public List<NeighbourInfo> getNeighbourList() {
        synchronized (managerLock) {
            Collection<LinkLayerNeighbour> neighborhood = physicalNeighbours.values();
            List<NeighbourInfo> ret = new LinkedList<NeighbourInfo>();

            for(Map.Entry<Contact, Set<ProtocolChannel>> entry : contactConnected.entrySet()) {
                Contact contact = entry.getKey();
                Set<ProtocolChannel>    channels = entry.getValue();
                Set<LinkLayerNeighbour> neighboorList = new HashSet<LinkLayerNeighbour>();
                for(ProtocolChannel channel : channels) {
                    LinkLayerConnection con = channel.getLinkLayerConnection();
                    neighboorList.add(con.getLinkLayerNeighbour());
                    neighborhood.remove(con.getLinkLayerNeighbour());
                }
                ret.add(new NeighbourInfo(contact, neighboorList));
            }

            // if some neighbour left, it means we haven't receive their contact yet
            for(LinkLayerNeighbour neighbour : neighborhood) {
                Set<LinkLayerNeighbour> single = new HashSet<LinkLayerNeighbour>();
                single.add(neighbour);
                ret.add(new NeighbourInfo(null, single));
            }

            return ret;
        }
    }

    public ProtocolChannel chooseBestChannel(Contact contact) {
        ProtocolChannel ret = null;
        for(ProtocolChannel channel : contactConnected.get(contact)) {
            if(ret == null)
                ret = channel;
            else
                ret = (ret.getLinkLayerConnection().getLinkLayerPriority() >
                        channel.getLinkLayerConnection().getLinkLayerPriority() ? ret : channel );
        }
        return ret;
    }

    public Set<Contact> getRecipientList(ProtocolChannel channel) {
        Set<Contact> ret = new HashSet<Contact>();
        for(Map.Entry<Contact, Set<ProtocolChannel>> entry : contactConnected.entrySet()) {
            Contact contact = entry.getKey();
            Set<ProtocolChannel> channels = entry.getValue();
            if(channels.contains(channel))
                ret.add(contact);
        }
        return ret;
    }
}
