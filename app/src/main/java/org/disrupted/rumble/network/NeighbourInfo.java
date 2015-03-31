package org.disrupted.rumble.network;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.protocols.ProtocolNeighbour;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author Marlinski
 */
public class NeighbourInfo {

    public LinkLayerNeighbour neighbour;
    public Set<String> connectedProtocols;

    public NeighbourInfo(LinkLayerNeighbour neighbour) {
        this.neighbour = neighbour;
        connectedProtocols = new HashSet<String>();
    }

    public void addProtocol(String protocol) {
        connectedProtocols.add(protocol);
    }

    public boolean isConnected() {
        return (connectedProtocols.size() > 0);
    }
}
