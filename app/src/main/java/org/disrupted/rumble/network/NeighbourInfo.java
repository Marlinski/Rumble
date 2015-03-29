package org.disrupted.rumble.network;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.protocols.ProtocolNeighbour;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public class NeighbourInfo {

    public LinkLayerNeighbour first;
    public List<ProtocolNeighbour> protocolPresence;

    public NeighbourInfo(LinkLayerNeighbour neighbour) {
        this.first = neighbour;
        protocolPresence = new LinkedList<ProtocolNeighbour>();
    }

    public NeighbourInfo(ProtocolNeighbour protocolNeighbour) {
        protocolPresence = new LinkedList<ProtocolNeighbour>();
        protocolPresence.add(protocolNeighbour);
    }

    public void addProtocolNeighbour(ProtocolNeighbour presence) {
        protocolPresence.add(presence);
    }


    // todo on peut surement optimiser cela
    public boolean isReachable(String linkLayerIdentifier) {
        if(linkLayerIdentifier.equals(first.getLinkLayerIdentifier()))
            return true;

        boolean ret = false;
        Iterator<ProtocolNeighbour> itl = protocolPresence.iterator();
        while(itl.hasNext() && !ret) {
            ProtocolNeighbour protocolNeighbour = itl.next();
            Iterator<LinkLayerNeighbour> itn = protocolNeighbour.getLinkLayerPresence().iterator();
            while(itn.hasNext() && !ret) {
                LinkLayerNeighbour neighbour = itn.next();
                if(neighbour.getLinkLayerIdentifier().equals(linkLayerIdentifier))
                    return true;
            }
        }
        return false;
    }

    public boolean isConnected(String linkLayerIdentifier) {
        boolean ret = false;
        Iterator<ProtocolNeighbour> itl = protocolPresence.iterator();
        while(itl.hasNext() && !ret) {
            ProtocolNeighbour protocolNeighbour = itl.next();
            Iterator<LinkLayerNeighbour> itn = protocolNeighbour.getLinkLayerPresence().iterator();
            while(itn.hasNext() && !ret) {
                LinkLayerNeighbour neighbour = itn.next();
                if(neighbour.getLinkLayerIdentifier().equals(linkLayerIdentifier))
                    return true;
            }
        }
        return false;
    }


}
