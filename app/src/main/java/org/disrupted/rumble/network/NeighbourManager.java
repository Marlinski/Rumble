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

import android.util.Log;

import org.disrupted.rumble.message.MessageQueue;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.exceptions.ProtocolNotFoundException;
import org.disrupted.rumble.network.exceptions.UnknownNeighbourException;
import org.disrupted.rumble.network.protocols.Protocol;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.command.SendStatusMessageCommand;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.greenrobot.event.EventBus;

/**
 * The NeighbourRecord is a representation of a neighbour on a logical level. We may be connected
 * to a neighbour with multiple interface at the same time, for instance Bluetooth and Wifi. As
 * both neighbour's interfaces have a different mac address, it is however the same mobile device.
 * As soon as we match both interfaces as being one entity (if possible), the two NeighbourDevice
 * instance are being kept in a table within NeighbourRecord.
 *
 * @author Marlinski
 */
public class NeighbourManager {

    private static final String TAG = "NeighbourRecord";

    private static final Object lock = new Object();

    //TODO replace name and id by ContactInfo
    private String name;
    private Map<Neighbour, Boolean>        linkLayerPresences;
    private Map<String, HashSet<Protocol>> protocolIdentifierToLinkSpecificProtocolInstance;
    private Map<String, MessageProcessor>  protocolIdentifierToMessageProcessor;

    public NeighbourManager(Neighbour neighbour){
        this.name = "undefinied";

        linkLayerPresences = new HashMap<Neighbour, Boolean>();
        linkLayerPresences.put(neighbour, new Boolean(true));

        protocolIdentifierToLinkSpecificProtocolInstance = new HashMap<String, HashSet<Protocol>>();
        protocolIdentifierToMessageProcessor = new HashMap<String, MessageProcessor>();
    }

    public String getName() {
        return name;
    }

    /*
     * getPresences return the list of Neighbour
     */
    public List<Neighbour> getPresences() {
        List<Neighbour> list = new LinkedList<Neighbour>();
        for(Map.Entry<Neighbour, Boolean> entry : linkLayerPresences.entrySet()) {
            list.add(entry.getKey());
        }
        return list;
    }

    /*
     * addPresence add a Neighbour to the local presence list.
     * it returns true if the neighbour has been added or updated
     * false if the neighbour presence was already known
     */
    public boolean addPresence(Neighbour presence) {
        for(Map.Entry<Neighbour, Boolean> entry : linkLayerPresences.entrySet()) {
            if(entry.getKey().getMacAddress().equals(presence.getMacAddress())) {
                if(entry.getValue().booleanValue())
                    return false;

                entry.setValue(true);
                return true;
            }
        }
        linkLayerPresences.put(presence, new Boolean(true));
        return true;
    }
    /*
     * delPresence removes a neighbour's presence from the local presence list unless
     * we are being connected to it.
     * returns true if the neighbour's presence was successfully removed
     * returns false if the neighbour's presence was not removed (because we are connected)
     * throws UnknownNeighbourException else
     */
    public boolean delPresence(String mac) throws UnknownNeighbourException{
        for (Map.Entry<Neighbour, Boolean> entry : linkLayerPresences.entrySet()) {
            if (entry.getKey().getMacAddress().equals(mac)) {
                if (!entry.getValue().booleanValue()) {
                    Log.e(TAG, "[!] presence was already false");
                    return false;
                }
                if(isConnectedLinkLayer(entry.getKey().getLinkLayerType())) {
                    Log.e(TAG, "[!] we are still connected to the neighbour "+mac);
                    return false;
                } else {
                    entry.setValue(new Boolean(false));
                    return true;
                }

            }
        }
        throw new UnknownNeighbourException();
    }


    /*
     * This function is called when the user shut down an interface
     * In that case we must remove the eventual entries related to a specific LinkLayerIdentifier
     * We also remove the related protocols entry (they should have or will stop by themselves)
     */
    public void delDeviceType(String linkLayerIdentifier) {
        for (Map.Entry<Neighbour, Boolean> entry : linkLayerPresences.entrySet()) {
            if(entry.getKey().getLinkLayerType().equals(linkLayerIdentifier))
                entry.setValue(new Boolean(false));
        }
        synchronized (lock) {
            for (Map.Entry<String, HashSet<Protocol>> entry : protocolIdentifierToLinkSpecificProtocolInstance.entrySet()) {
                Iterator<Protocol> itproto = entry.getValue().iterator();
                while (itproto.hasNext()) {
                    Protocol protocol = itproto.next();
                    if (protocol.getLinkLayerIdentifier().equals(linkLayerIdentifier)) {
                        itproto.remove();
                    }
                }
            }
        }
    }


    /*
     * isInRange returns true if this neighbour is within range of any interface
     * it returns false otherwise
     */
    public boolean isInRange() {
        for (Map.Entry<Neighbour, Boolean> entry : linkLayerPresences.entrySet()) {
            if(entry.getValue())
                return true;
        }
        return false;
    }

    /*
     * isInRange returns true if this neighbour is within range of a specific interface
     * it returns false otherwise
     */
    public boolean isInRange(String linkLayerIdentifier) throws UnknownNeighbourException{
        for (Map.Entry<Neighbour, Boolean> entry : linkLayerPresences.entrySet()) {
            if(entry.getKey().getLinkLayerType().equals(linkLayerIdentifier))
                return entry.getValue().booleanValue();
        }
        throw new UnknownNeighbourException();
    }
    /*
     * isConnected return true if we are connected to the neighbour
     * on a specific link layer.
     * it returns false otherwise
     */
    public boolean isConnectedLinkLayer(String linkLayerIdentifier) {
        for(Map.Entry<String, HashSet<Protocol>> entry : protocolIdentifierToLinkSpecificProtocolInstance.entrySet()) {
            Iterator<Protocol> it = entry.getValue().iterator();
            while(it.hasNext()) {
                Protocol protocol = it.next();
                if(protocol.getLinkLayerIdentifier().equals(linkLayerIdentifier)) {
                    return true;
                }
            }
        }
        return false;
    }
    /*
     * isConnectedWithprotocol return true if we are connected to the neighbour with a specific
     * protocol
     * it returns false otherwise
     */
    public boolean isConnectedWithProtocol(String protocolIdentifier) {
        return (protocolIdentifierToLinkSpecificProtocolInstance.get(protocolIdentifier) != null);
    }
    /*
     * isConnectedWithprotocol return true if we are connected to the neighbour with a specific
     * protocol on a specific link layer.
     * it returns false otherwise
     */
    public boolean isConnectedWithProtocol(String protocolIdentifier, String linkLayerIdentifier) {
        HashSet<Protocol> protocols = protocolIdentifierToLinkSpecificProtocolInstance.get(protocolIdentifier);
        if(protocols == null)
            return false;
        Iterator<Protocol> it = protocols.iterator();
        while(it.hasNext()) {
            if(it.next().getLinkLayerIdentifier().equals(linkLayerIdentifier))
                return true;
        }
        return false;
    }
    /*
     * is returns true if macAddress is one of the neighbour's presence
     * it returns false otherwise
     */
    public boolean is(String macAddress) {
        for (Map.Entry<Neighbour, Boolean> entry : linkLayerPresences.entrySet()) {
            if(entry.getKey().getMacAddress().equals(macAddress))
                return true;
        }
        return false;
    }

    /*
     * addProtocol adds a link layer specific instance of a protocol to the local protocol instance
     * list unless such an instance already exists. If this is the first instance of this protocol,
     * we also starts the MessageProcessor.
     *
     * Warning: the protocol must be ready to accept executeCommand() !
     *
     * return true if the protocol has been successfully added
     * return false otherwise
     */
    public boolean addProtocol(Protocol protocol) {
        synchronized (lock) {
            HashSet<Protocol> protocols = protocolIdentifierToLinkSpecificProtocolInstance.get(protocol.getProtocolID());

            if (protocols == null) {
                protocols = new HashSet<Protocol>();
                protocolIdentifierToLinkSpecificProtocolInstance.put(protocol.getProtocolID(), protocols);
            }

            if (protocols.size() == 0) {
                protocols.add(protocol);
                MessageProcessor processor = new MessageProcessor(protocol.getProtocolID());
                protocolIdentifierToMessageProcessor.put(protocol.getProtocolID(), processor);
                processor.start();
                return true;
            }

            Iterator<Protocol> it = protocols.iterator();
            while (it.hasNext()) {
                Protocol entry = it.next();
                if (entry.getLinkLayerIdentifier().equals(protocol.getLinkLayerIdentifier())) {
                    Log.e(TAG, "[!] we are already connected with " + protocol.getProtocolID() + " on link layer " + protocol.getLinkLayerIdentifier());
                    return false;
                }
            }
            Log.d(TAG, "[+] protocol " + protocol.getProtocolID() + " added on link layer " + protocol.getLinkLayerIdentifier());
            protocols.add(protocol);
            return true;
        }
    }


    /*
     * delProtocol removes a given protocol instance from the instance list. If this instance
     * is also the last one for this neighbour, we also stop the MessageProcessor.
     *
     * returns true if the protocol has been removed from the local instance list
     * throws ProtocolNotFoundException if the protocol was not found on the local instance list
     */
    public boolean delProtocol(Protocol protocol) throws ProtocolNotFoundException{
        synchronized (lock) {
            HashSet<Protocol> protocols = protocolIdentifierToLinkSpecificProtocolInstance.get(protocol.getProtocolID());
            if (protocols == null)
                throw new ProtocolNotFoundException();
            Iterator<Protocol> it = protocols.iterator();
            while (it.hasNext()) {
                Protocol entry = it.next();
                if (entry.getLinkLayerIdentifier().equals(protocol.getLinkLayerIdentifier())) {
                    Log.d(TAG, "[-] removing protocol " + protocol.getProtocolID() + " from link layer " + protocol.getLinkLayerIdentifier());
                    it.remove();
                    if (protocols.size() == 0) {
                        protocolIdentifierToMessageProcessor.get(protocol.getProtocolID()).interrupt();
                        protocolIdentifierToLinkSpecificProtocolInstance.remove(protocol.getProtocolID());
                    }
                    return true;
                }
            }
            throw new ProtocolNotFoundException();
        }
    }

    /*
     * MessageProcessor is created whenever the first instance of a protocol is open with
     * a Neighbour. MessageProcessor will push messages from the database to the neighbour
     * if it hasn't been sent already (using the up to date forwarder list)
     *
     * It uses a MessageQueue.PriorityBlockingMessageQueue which is automatically maintained
     * and feed by the MessageQueue instance. the call to take() will automatically retrieve
     * the best scored (i.e. more urgent) message to forward.
     */
    private class MessageProcessor extends Thread {

        private final String TAG;

        private MessageQueue.PriorityBlockingMessageQueue queue;
        private String protocolID;

        public MessageProcessor(String protocolID) {
            this.TAG = "MessageProcessor";
            this.queue = MessageQueue.getInstance().getMessageListener(100);
            this.protocolID = protocolID;
        }

        @Override
        public synchronized void run() {
            try {
                Log.d(TAG, "[+] Starting ("+protocolID+")");
                while(true) {
                    StatusMessage message = queue.take();
                    Command command = new SendStatusMessageCommand(message);
                    Protocol protocol = getBestProtocol(protocolID);
                    if(protocol == null)
                        break;
                    Log.d(TAG, "[+] new command ("+protocolID+")");
                    protocol.executeCommand(command);
                }
            }
            catch(InterruptedException e) {
                Log.d(TAG, "[+] Stopping ("+protocolID+")");
                queue.clear();
            }
        }
    }


    /*
     * getBestProcotolInstance returns the best available Link Layer specific instance
     * of a given protocol. If we are connected to a neighbour with multiple interface
     * (say bluetooth and wifi), getBestProtocolInstance will perform a score based on
     * history data (throughput, delay) and returns the best one based on this score.
     *
     * It returns null if there is no available instance of this protocol
     */
    private Protocol getBestProtocol(String protocolID) {
        synchronized (lock) {
            HashSet<Protocol> protocols = protocolIdentifierToLinkSpecificProtocolInstance.get(protocolID);
            if (protocols == null)
                return null;
            Iterator<Protocol> it = protocols.iterator();
            Protocol bestProtocol = null;
            //todo compute score for each link layer
            while (it.hasNext()) {
                Protocol protocol = it.next();
                bestProtocol = protocol;
            }
            return bestProtocol;
        }
    }

}
