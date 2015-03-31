package org.disrupted.rumble.network.protocols;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public abstract class ProtocolNeighbour {

    List<LinkLayerNeighbour> linkLayerPresence;

    abstract public String getProtocolIdentifier();

    abstract public String getNeighbourIdentifier();

    public ProtocolNeighbour(LinkLayerNeighbour linkLayerNeighbour) {
        linkLayerPresence = new LinkedList<LinkLayerNeighbour>();
        linkLayerPresence.add(linkLayerNeighbour);
    }

    // todo return a copy
    public List<LinkLayerNeighbour> getLinkLayerPresence() {
        return linkLayerPresence;
    }

    public void clear() {
        linkLayerPresence.clear();
    }

    public boolean reachableOn(String linkLayerIdentifier) {
        Iterator<LinkLayerNeighbour> it = linkLayerPresence.iterator();
        while(it.hasNext()) {
            LinkLayerNeighbour neighbour = it.next();
            if(neighbour != null)
                if(neighbour.getLinkLayerIdentifier().equals(linkLayerIdentifier))
                    return true;
        }
        return false;
    }

}
