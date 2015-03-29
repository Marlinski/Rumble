package org.disrupted.rumble.network.protocols.firechat;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.protocols.ProtocolNeighbour;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public class FirechatNeighbour implements ProtocolNeighbour {

    private String firechatName;
    private String firechatID;

    private List<LinkLayerNeighbour> linkLayerPresence;

    public FirechatNeighbour(String firechatID, String firechatName, LinkLayerNeighbour presence) {
        this.firechatID = firechatID;
        this.firechatName = firechatName;
        this.linkLayerPresence = new LinkedList<LinkLayerNeighbour>();
        linkLayerPresence.add(presence);
    }

    @Override
    public String getProtocolIdentifier() {
        return FirechatProtocol.protocolID;
    }

    @Override
    public String getNeighbourIdentifier() {
        return firechatID;
    }

    @Override
    public List<LinkLayerNeighbour> getLinkLayerPresence() {
        return linkLayerPresence;
    }

    public String getFirechatName() {
        return firechatName;
    }

    public String getFirechatId() {
        return firechatID;
    }


}
