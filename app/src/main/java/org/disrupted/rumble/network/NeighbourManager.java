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
import org.disrupted.rumble.database.events.ContactInterfaceInserted;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Interface;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStopped;
import org.disrupted.rumble.network.linklayer.events.NeighborhoodChanged;
import org.disrupted.rumble.network.linklayer.events.NeighbourReachable;
import org.disrupted.rumble.network.linklayer.events.NeighbourUnreachable;
import org.disrupted.rumble.network.linklayer.wifi.WifiNeighbour;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.events.NeighbourConnected;
import org.disrupted.rumble.network.protocols.events.NeighbourDisconnected;
import org.disrupted.rumble.network.services.events.ContactConnected;
import org.disrupted.rumble.network.services.events.ContactDisconnected;
import org.disrupted.rumble.network.services.events.ContactReachable;
import org.disrupted.rumble.network.services.events.ContactUnreachable;
import org.disrupted.rumble.util.NetUtil;

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
 *
 */
public class NeighbourManager {


    private static final String TAG = "NeighbourManager";

    private final Object managerLock = new Object();

    public static class NeighbourEntry {
        public LinkLayerNeighbour linkLayerNeighbour;
        public boolean reachable;
        public Set<ProtocolChannel> channels;

        public NeighbourEntry(LinkLayerNeighbour linkLayerNeighbour) {
            this.linkLayerNeighbour = linkLayerNeighbour;
            this.reachable = true;
            this.channels = new HashSet<ProtocolChannel>();
        }
    }
    private  Map<String, NeighbourEntry> neighborhood;

    public NeighbourManager() {
        this.neighborhood = new HashMap<String, NeighbourEntry>();
    }

    /*
     * Starting/Stopping the manager
     */
    public void startMonitoring() {
        EventBus.getDefault().register(this);
    }

    public void stopMonitoring() {
        synchronized (managerLock) {
            if (EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);

            for (Map.Entry<String, NeighbourEntry> entry : neighborhood.entrySet()) {
                entry.getValue().channels.clear();
            }
            neighborhood.clear();
        }
    }

    /*
     * Using Events to manage the neighborhood
     */
    public void onEvent(NeighbourReachable event) {
        synchronized (managerLock) {
            if (neighborhood.get(event.neighbour.getLinkLayerAddress()) != null)
                return;
            NeighbourEntry entry = new NeighbourEntry(event.neighbour);
            neighborhood.put(event.neighbour.getLinkLayerAddress(), entry);

            // throw ContactReachable event if any
            Set<Contact> contacts = DatabaseFactory.getContactDatabase(RumbleApplication.getContext())
                    .getContactsUsingMacAddress(event.neighbour.getLinkLayerAddress());
            if(!contacts.isEmpty()) {
                for (Contact contact : contacts)
                    EventBus.getDefault().post(new ContactReachable(contact, event.neighbour));
            }
        }
        EventBus.getDefault().post(new NeighborhoodChanged());
    }

    public void onEvent(NeighbourUnreachable event) {
        synchronized (managerLock) {
            NeighbourEntry entry = neighborhood.get(event.neighbour.getLinkLayerAddress());
            if (entry == null)
                return;
            if (entry.channels.isEmpty()) {
                neighborhood.remove(event.neighbour.getLinkLayerAddress());

                // throw ContactUnreachable event if any
                Set<Contact> contacts = DatabaseFactory.getContactDatabase(RumbleApplication.getContext())
                        .getContactsUsingMacAddress(event.neighbour.getLinkLayerAddress());
                if(!contacts.isEmpty()) {
                    for (Contact contact : contacts)
                        EventBus.getDefault().post(new ContactUnreachable(contact));
                }

            } else
                entry.reachable = false;
        }
        EventBus.getDefault().post(new NeighborhoodChanged());
    }

    public void onEvent(NeighbourConnected event) {
        synchronized (managerLock) {
            NeighbourEntry entry = neighborhood.get(event.neighbour.getLinkLayerAddress());
            if (entry == null) {
                entry = new NeighbourEntry(event.neighbour);
                neighborhood.put(event.neighbour.getLinkLayerAddress(), entry);
            }
            entry.channels.add(event.worker);
        }
        EventBus.getDefault().post(new NeighborhoodChanged());
    }

    public void onEvent(NeighbourDisconnected event) {
        synchronized (managerLock) {
            NeighbourEntry entry = neighborhood.get(event.neighbour.getLinkLayerAddress());
            if (entry == null)
                return;
            entry.channels.remove(event.worker);

            try {
                // throw ContactDisconnected event if any
                Set<Contact> contacts = DatabaseFactory.getContactDatabase(RumbleApplication.getContext())
                        .getContactsUsingMacAddress(event.neighbour.getLinkLayerMacAddress());
                if (!contacts.isEmpty()) {
                    for (Contact contact : contacts)
                        EventBus.getDefault().post(new ContactDisconnected(contact));
                }
                if (entry.channels.isEmpty() && !entry.reachable) {
                    if (!contacts.isEmpty()) {
                        for (Contact contact : contacts)
                            EventBus.getDefault().post(new ContactUnreachable(contact));
                    }
                }
            } catch(NetUtil.NoMacAddressException ignore) {
            }

            if (entry.channels.isEmpty() && !entry.reachable)
                neighborhood.remove(entry);
        }
        EventBus.getDefault().post(new NeighborhoodChanged());
    }

    public void onEvent(LinkLayerStopped event) {
        synchronized (managerLock) {
            Iterator<Map.Entry<String, NeighbourEntry>> it = neighborhood.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String,NeighbourEntry> mapEntry = it.next();
                NeighbourEntry neighbourEntry = mapEntry.getValue();
                if (neighbourEntry.linkLayerNeighbour.getLinkLayerIdentifier().equals(event.linkLayerIdentifier))
                    it.remove();
            }
        }
        EventBus.getDefault().post(new NeighborhoodChanged());
    }

    public void onEvent(ContactInterfaceInserted event) {
        EventBus.getDefault().post(new NeighborhoodChanged());
    }

    /*
     * List of neighbour for the UI Adapter
     */
    public static interface Neighbour {
        public String getFirstName();
        public String getSecondName();
        public boolean isReachable(String linkLayerIdentifier);
        public boolean isConnected(String linkLayerIdentifier);
        public boolean is(String linkLayerAddress);
    }

    public static class UnknowNeighbour implements Neighbour {

        private NeighbourEntry neighbour;

        public UnknowNeighbour(NeighbourEntry entry) {
            this.neighbour = entry;
        }

        @Override
        public String getFirstName() {
            if(neighbour.linkLayerNeighbour instanceof BluetoothNeighbour)
                return ((BluetoothNeighbour)neighbour.linkLayerNeighbour).getBluetoothDeviceName();
            else
                return neighbour.linkLayerNeighbour.getLinkLayerAddress();
        }

        @Override
        public String getSecondName() {
            if (neighbour.linkLayerNeighbour instanceof BluetoothNeighbour)
                return ((BluetoothNeighbour) neighbour.linkLayerNeighbour).getLinkLayerAddress();
            try {
                if (neighbour.linkLayerNeighbour instanceof WifiNeighbour)
                    return neighbour.linkLayerNeighbour.getLinkLayerMacAddress();
            } catch(NetUtil.NoMacAddressException ignore) {
            }
            return "";
        }

        @Override
        public boolean isReachable(String linkLayerIdentifier) {
            return neighbour.linkLayerNeighbour.getLinkLayerIdentifier()
                    .equals(linkLayerIdentifier);
        }

        @Override
        public boolean isConnected(String linkLayerIdentifier) {
            for(ProtocolChannel channel : neighbour.channels) {
                if(channel.getLinkLayerConnection().getLinkLayerIdentifier()
                        .equals(linkLayerIdentifier))
                    return true;
            }
            return false;
        }

        @Override
        public boolean is(String linkLayerAddress) {
            return neighbour.linkLayerNeighbour.getLinkLayerAddress()
                    .equals(linkLayerAddress);
        }
    }

    public static class ContactNeighbour implements Neighbour {

        private Contact contact;
        private List<NeighbourEntry> neighbourEntries;

        public ContactNeighbour(Contact contact) {
            this.contact = contact;
            neighbourEntries = new LinkedList<NeighbourEntry>();
        }

        public void addNeighbourEntry(NeighbourEntry entry) {
            this.neighbourEntries.add(entry);
        }

        public List<NeighbourEntry> getNeighbourEntries() {
            return neighbourEntries;
        }

        @Override
        public String getFirstName() {
            return contact.getName();
        }

        @Override
        public String getSecondName() {
            return contact.getUid();
        }

        @Override
        public boolean isReachable(String linkLayerIdentifier) {
            for(NeighbourEntry entry : neighbourEntries) {
                if(entry.linkLayerNeighbour.getLinkLayerIdentifier()
                        .equals(linkLayerIdentifier))
                    return true;
            }
            return false;
        }

        @Override
        public boolean isConnected(String linkLayerIdentifier) {
            for(NeighbourEntry entry : neighbourEntries) {
                if(entry.linkLayerNeighbour.getLinkLayerIdentifier()
                        .equals(linkLayerIdentifier)) {
                    return (!entry.channels.isEmpty());
                }
            }
            return false;
        }

        @Override
        public boolean is(String linkLayerAddress) {
            for(NeighbourEntry neighbourEntry : neighbourEntries) {
                if(neighbourEntry.linkLayerNeighbour.getLinkLayerAddress()
                        .equals(linkLayerAddress))
                    return true;
            }
            return false;
        }
    }

    public Set<Neighbour> getNeighbourList() {
        Set<Neighbour> ret = new HashSet<Neighbour>();

        synchronized (managerLock) {
            for(Map.Entry<String, NeighbourEntry> mapEntry : neighborhood.entrySet()) {
                NeighbourEntry neighbourEntry = mapEntry.getValue();

                // we might know some of the entries already, either because we are already
                // connected to them, or because we met them in the past
                String macAddress = "";
                try {
                    macAddress = neighbourEntry.linkLayerNeighbour.getLinkLayerMacAddress();
                } catch(NetUtil.NoMacAddressException e){
                    continue;
                }

                Set<Contact> contacts = DatabaseFactory.getContactDatabase(RumbleApplication.getContext())
                        .getContactsUsingMacAddress(macAddress);

                if(contacts.isEmpty()) {
                    // this neighbour entry is unknown
                    Neighbour unknowNeighbour = new UnknowNeighbour(neighbourEntry);
                    ret.add(unknowNeighbour);
                } else {
                    // we know one or more contact that match this neighbourEntry.
                    // this is because a certain linkLayerNeighbour can be accessed by multiple
                    // protocols (Rumble, Firechat) each of them being a different contact
                    // every contact will have its own ContactNeighbour entry
                    ContactNeighbour contactNeighbour = null;
                    for (Contact contact : contacts) {
                        // now we might have created this contact already (with another interface)
                        boolean found = false;
                        Iterator<Neighbour> it = ret.iterator();
                        while (it.hasNext() && !found) {
                            Neighbour neighbour = it.next();
                            if (neighbour instanceof ContactNeighbour) {
                                if (((ContactNeighbour) neighbour).contact.equals(contact)) {
                                    // the contact was created already
                                    contactNeighbour = (ContactNeighbour) neighbour;
                                    found = true;
                                }
                            }
                        }
                        if(!found) {
                            // the contact wasn't already created
                            contactNeighbour = new ContactNeighbour(contact);
                            ret.add(contactNeighbour);
                        }

                        // now we add the channels to our ContactNeighbour
                        // but only with the ProtocolChannel relevant to our contact
                        NeighbourEntry newNeighbourEntry = new NeighbourEntry(neighbourEntry.linkLayerNeighbour);
                        for(ProtocolChannel channel : neighbourEntry.channels) {
                            Interface iface = new Interface(
                                    macAddress,
                                    channel.getProtocolIdentifier());
                            if(contact.getInterfaces().contains(iface))
                                newNeighbourEntry.channels.add(channel);
                        }
                        contactNeighbour.addNeighbourEntry(newNeighbourEntry);
                    }
                }
            }
        }

        return ret;
    }

    public ProtocolChannel chooseBestChannel(Contact contact) {
        ProtocolChannel ret = null;
        
        synchronized (managerLock) {
            Set<Neighbour> neighbourList = getNeighbourList();
            Iterator<Neighbour> it = neighbourList.iterator();
            while (it.hasNext()) {
                Neighbour neighbour = it.next();
                if (!(neighbour instanceof ContactNeighbour))
                    continue;

                if (((ContactNeighbour) neighbour).contact.equals(contact)) {
                    for (NeighbourEntry neighbourEntry : ((ContactNeighbour) neighbour).getNeighbourEntries()) {
                        for (ProtocolChannel channel : neighbourEntry.channels) {
                            if (ret == null)
                                ret = channel;
                            else
                                ret = (ret.getChannelPriority() >
                                        channel.getChannelPriority() ? ret : channel);
                        }
                    }
                }
            }
        }
        return ret;
    }
}
