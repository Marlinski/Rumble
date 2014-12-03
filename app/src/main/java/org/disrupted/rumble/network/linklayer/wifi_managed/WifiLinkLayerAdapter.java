/*
 * Copyright (C) 2014 Disrupted Systems
 *
 * This file is part of Rumble.
 *
 * Rumble is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Rumble is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.network.linklayer.wifi_managed;

import org.disrupted.rumble.network.NeighbourDevice;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;

import java.util.List;

/**
 * @author Marlinski
 */
public class WifiLinkLayerAdapter extends LinkLayerAdapter {

    private static final String ID = "Wifi-Managed";

    public WifiLinkLayerAdapter(NetworkCoordinator networkCoordinator) {
        super(networkCoordinator);
    }

    @Override
    public String getID() {
        return null;
    }

    @Override
    public void onLinkStart() {

    }

    @Override
    public void onLinkStop() {

    }

    @Override
    public boolean isScanning() {
        return false;
    }

    @Override
    public void forceDiscovery() {

    }

    @Override
    public List<NeighbourDevice> getNeighborhood() {
        return null;
    }

    @Override
    public void connectTo(NeighbourDevice neighbourDevice, boolean force) {

    }
}
