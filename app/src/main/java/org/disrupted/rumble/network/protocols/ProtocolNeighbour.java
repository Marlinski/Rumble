package org.disrupted.rumble.network.protocols;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;

import java.util.List;

/**
 * @author Marlinski
 */
public interface ProtocolNeighbour {

    public String getProtocolIdentifier();

    public String getNeighbourIdentifier();

    public List<LinkLayerNeighbour> getLinkLayerPresence();

}
