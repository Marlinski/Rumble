/*
 * Copyright (C) 2014 Lucien Loiseau
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

package org.disrupted.rumble.network.linklayer.wifi;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;

/**
 * @author Lucien Loiseau
 */
public class WifiAdHocLayerAdapter implements LinkLayerAdapter {

    private static final String ID = "Wifi-AdHoc";

    private NetworkCoordinator networkCoordinator;
    private boolean activated;

    public WifiAdHocLayerAdapter(NetworkCoordinator networkCoordinator) {
        this.networkCoordinator = networkCoordinator;
    }

    @Override
    public boolean isActivated() {
        return activated;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return null;
    }

    @Override
    public void linkStart() {
        if(activated)
            return;
        activated = true;
    }

    @Override
    public void linkStop() {
        if(!activated)
            return;
        activated = false;
    }

}


