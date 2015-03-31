package org.disrupted.rumble.network.protocols.firechat;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.protocols.ProtocolNeighbour;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public class FirechatNeighbour extends ProtocolNeighbour {

    private String firechatName;
    private String firechatID;

    public FirechatNeighbour(String firechatID, String firechatName, LinkLayerNeighbour linkLayerNeighbour) {
        super(linkLayerNeighbour);
        this.firechatID = firechatID;
        this.firechatName = firechatName;
    }

    @Override
    public String getProtocolIdentifier() {
        return FirechatProtocol.protocolID;
    }

    @Override
    public String getNeighbourIdentifier() {
        return firechatID;
    }

}
