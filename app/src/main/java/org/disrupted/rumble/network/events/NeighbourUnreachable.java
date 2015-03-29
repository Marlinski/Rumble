package org.disrupted.rumble.network.events;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;

/**
 * @author Marlinski
 */
public class NeighbourUnreachable extends NetworkEvent {

    public final LinkLayerNeighbour neighbour;

    public NeighbourUnreachable(LinkLayerNeighbour neighbour) {
        this.neighbour = neighbour;
    }
}
