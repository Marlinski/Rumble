package org.disrupted.rumble.network.protocols.rumble;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.protocols.ProtocolNeighbour;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public class RumbleNeighbour extends ProtocolNeighbour {

    String rumbleIdentifier;

    public RumbleNeighbour(String rumbleIdentifier, LinkLayerNeighbour linkLayerNeighbour) {
        super(linkLayerNeighbour);
        this.rumbleIdentifier = rumbleIdentifier;
    }

    @Override
    public String getProtocolIdentifier() {
        return RumbleProtocol.protocolID;
    }

    @Override
    public String getNeighbourIdentifier() {
        return rumbleIdentifier;
    }


}
