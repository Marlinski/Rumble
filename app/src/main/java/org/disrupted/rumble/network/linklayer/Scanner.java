package org.disrupted.rumble.network.linklayer;

import java.util.HashSet;

/**
 * @author Marlinski
 */
public interface Scanner {

    public HashSet<LinkLayerNeighbour> getNeighbourList();

    public void forceDiscovery();

    public boolean isScanning();

}
