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

import org.disrupted.rumble.network.Neighbour;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.events.LinkLayerStarted;
import org.disrupted.rumble.network.events.LinkLayerStopped;

import de.greenrobot.event.EventBus;

/**
 * LinkLayerAdapter is a class that is responsible of managing a LinkLayer interface such
 * as Bluetooth or Wifi. It is directly under the responsibility of NetworkCoordinator and
 * all the LinkLayerAdapter methods are called from it.
 *
 * @author Marlinski
 */
public abstract class LinkLayerAdapter {

    protected boolean activated;

    public LinkLayerAdapter(NetworkCoordinator networkCoordinator) {
        this.activated = false;
    }

    public boolean isActivated() {
        return activated;
    }

    public void linkStart() {
        if(activated)
            return;

        activated = true;
        onLinkStart();
        EventBus.getDefault().post(new LinkLayerStarted(getLinkLayerIdentifier()));
    }

    public void linkStop() {
        if(!activated)
            return;

        onLinkStop();
        activated = false;
        EventBus.getDefault().post(new LinkLayerStopped(getLinkLayerIdentifier()));
    }

    abstract public String getLinkLayerIdentifier();

    abstract public void onLinkStart();

    abstract public void onLinkStop();

    abstract public boolean isScanning();

    abstract public void forceDiscovery();

    abstract public void connectTo(Neighbour neighbour, boolean force);

}
