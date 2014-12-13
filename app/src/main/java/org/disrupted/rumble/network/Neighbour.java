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

    private String macAddress;


    abstract public String getLinkLayerName();

    abstract public String getLinkLayerType();


    public Neighbour(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getMacAddress() {
        return this.macAddress;
    }

    @Override
    public String toString() {
        return "MAC address: " + macAddress;
    }

    @Override
    public int hashCode() {
        return macAddress.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Neighbour))
            return false;
        if (obj == this)
            return true;

        Neighbour device = (Neighbour) obj;
        return device.getMacAddress().equals(this.macAddress);
    }

}
