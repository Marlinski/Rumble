package org.disrupted.rumble.network.events;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;

/**
 * @author Marlinski
 */
public class NeighbourReachable extends NetworkEvent {

    public final LinkLayerNeighbour neighbour;

    public NeighbourReachable(LinkLayerNeighbour neighbour) {
        this.neighbour = neighbour;
    }
}
