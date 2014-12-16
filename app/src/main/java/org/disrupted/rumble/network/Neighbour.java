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

package org.disrupted.rumble.network;


/**
 * @author Marlinski
 */
public abstract class Neighbour {

    private String linkLayerAddress;


    abstract public String getLinkLayerName();

    abstract public String getLinkLayerType();


    public Neighbour(String linkLayerAddress) {
        this.linkLayerAddress = linkLayerAddress;
    }

    public String getLinkLayerAddress() {
        return this.linkLayerAddress;
    }

    @Override
    public String toString() {
        return "MAC address: " + linkLayerAddress;
    }

    @Override
    public int hashCode() {
        return linkLayerAddress.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Neighbour))
            return false;
        if (obj == this)
            return true;

        Neighbour device = (Neighbour) obj;
        return device.getLinkLayerAddress().equals(this.linkLayerAddress);
    }

}
