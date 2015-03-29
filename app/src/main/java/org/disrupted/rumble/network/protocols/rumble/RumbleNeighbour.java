package org.disrupted.rumble.network.protocols.rumble;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.protocols.ProtocolNeighbour;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public class RumbleNeighbour implements ProtocolNeighbour {

    String rumbleIdentifier;

    List<LinkLayerNeighbour> linkLayerPresence;

    public RumbleNeighbour(String rumbleIdentifier, LinkLayerNeighbour presence) {
        this.rumbleIdentifier = rumbleIdentifier;
        this.linkLayerPresence = new LinkedList<LinkLayerNeighbour>();
        linkLayerPresence.add(presence);
    }

    @Override
    public String getProtocolIdentifier() {
        return RumbleProtocol.protocolID;
    }

    @Override
    public String getNeighbourIdentifier() {
        return rumbleIdentifier;
    }

    @Override
    public List<LinkLayerNeighbour> getLinkLayerPresence() {
        return linkLayerPresence;
    }
}
