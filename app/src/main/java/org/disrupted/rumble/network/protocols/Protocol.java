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

package org.disrupted.rumble.network.protocols;


import org.disrupted.rumble.network.linklayer.events.LinkLayerStarted;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStopped;
import org.disrupted.rumble.network.linklayer.events.NeighbourReachable;
import org.disrupted.rumble.network.linklayer.events.NeighbourUnreachable;

/**
 * @author Marlinski
 */
public interface Protocol {

    /*
     * Protocol identification
     */
    public String getProtocolIdentifier();

    /*
     * Protocol management
     */
    public void protocolStart();

    public void protocolStop();

    /*
     * Protocol must catch those event to deal with clients
     */
    public void onEvent(LinkLayerStarted event);

    public void onEvent(LinkLayerStopped event);

    public void onEvent(NeighbourReachable event);

    public void onEvent(NeighbourUnreachable event);

}
