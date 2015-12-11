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

package org.disrupted.rumble.network.linklayer;

import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;


/**
 * LinkLayerConnection is an interface for a class to implement connect() and disconnect()
 * method that are link-layer specifics.
 *
 * @author Lucien Loiseau
 */
public interface LinkLayerConnection {

    /*
     * priority is used to determine which channel is the best when multiple channel
     * are available to a certain neighbour
     */
    public static final int LINK_LAYER_HIGH_PRIORITY = 10;
    public static final int LINK_LAYER_MIDDLE_PRIORITY = 5;
    public static final int LINK_LAYER_LOW_PRIORITY = 0;

    public int getLinkLayerPriority();

    public String getLinkLayerIdentifier();

    public String getConnectionID();

    public void connect() throws LinkLayerConnectionException;

    public void disconnect() throws LinkLayerConnectionException;

    public LinkLayerNeighbour getLinkLayerNeighbour();
}
