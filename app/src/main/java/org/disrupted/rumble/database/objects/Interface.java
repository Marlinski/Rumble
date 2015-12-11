/*
 * Copyright (C) 2014 Lucien Loiseau
 * This file is part of Rumble.
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
 * You should have received a copy of the GNU General Public License along
 * with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.database.objects;

import org.disrupted.rumble.util.HashUtil;

/**
 * @author Lucien Loiseau
 */
public class Interface {

    public static final String TAG = "Interface";

    private long   interfaceDBID;
    private String hash;
    private String macAddress;

    public Interface(long interfaceDBID, String hash, String macAddress) {
        this.interfaceDBID = interfaceDBID;
        this.hash = hash;
        this.macAddress = macAddress;
    }

    public Interface(String macAddress, String protocolID) {
        this.interfaceDBID = -1;
        this.hash = HashUtil.computeInterfaceID(macAddress,protocolID);
        this.macAddress = macAddress;
    }

    public String getMacAddress() {
        return macAddress;
    }

    @Override
    public boolean equals(Object o) {
        if(o == null)
            return false;
        if(o instanceof Interface) {
            Interface anInterface = (Interface)o;
            return this.hash.equals(anInterface.hash);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.hash.hashCode();
    }

    @Override
    public String toString() {
        return macAddress;
    }


}
