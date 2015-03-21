package org.disrupted.rumble.network.events;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;

/**
 * @author Marlinski
 */
public class NewNeighbour extends NetworkEvent {

    public LinkLayerNeighbour neighbour;

    public NewNeighbour(LinkLayerNeighbour neighbour) {
        this.neighbour = neighbour;
    }
}
