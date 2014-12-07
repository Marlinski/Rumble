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

package org.disrupted.rumble.network.linklayer;

import org.disrupted.rumble.network.NeighbourDevice;
import org.disrupted.rumble.network.NetworkCoordinator;

import java.util.List;

/**
 * @author Marlinski
 */
public abstract class LinkLayerAdapter {

    protected NetworkCoordinator networkCoordinator;
    protected boolean activated;

    public LinkLayerAdapter(NetworkCoordinator networkCoordinator) {
        this.networkCoordinator = networkCoordinator;
        this.activated = false;
    }

    public boolean isActivated() {
        return activated;
    }
    abstract public String getID();

    public void linkStart() {
        activated = true;
        onLinkStart();
    }

    public void linkStop() {
        onLinkStop();
        activated = false;
    }

    abstract public void onLinkStart();

    abstract public void onLinkStop();

    abstract public boolean isScanning();

    abstract public void forceDiscovery();

    abstract public List<NeighbourDevice> getNeighborhood();

    abstract public void connectTo(NeighbourDevice neighbourDevice, boolean force);

}
