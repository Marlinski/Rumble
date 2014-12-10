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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The NeighbourRecord is a representation of a neighbour on a logical level. We may be connected
 * to a neighbour with multiple interface at the same time, for instance Bluetooth and Wifi. As
 * both neighbour's interfaces have a different mac address, it is however the same mobile device.
 * As soon as we match both interfaces as being one entity (if possible), the two NeighbourDevice
 * instance are being kept in a table within NeighbourRecord.
 *
 * @author Marlinski
 */
public class NeighbourRecord {

    private static final String TAG = "NeighbourRecord";

    private int nbConnection;
    //TODO replace name and id by ContactInfo
    private String name;
    private String id;
    private Map<String, NeighbourDevice> macToNeighbourDevice;

    public NeighbourRecord(NeighbourDevice neighbourDevice){
        this.id = neighbourDevice.getMacAddress();
        this.name = neighbourDevice.getDeviceName();
        macToNeighbourDevice = new HashMap<String, NeighbourDevice>();
        addDevice(neighbourDevice.getMacAddress(), neighbourDevice);
        nbConnection = 0;
    }

    public String getName() {
        return name;
    }

    public String getID() {
        return id;
    }

    public NeighbourDevice getDeviceFromAddress(String mac) {
        return macToNeighbourDevice.get(mac);
    }

    public NeighbourDevice getDeviceFromType(String deviceType) {
        Iterator<Map.Entry<String,NeighbourDevice>> it = macToNeighbourDevice.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String,NeighbourDevice> entry = it.next();
            if(entry.getValue().getType().equals(deviceType))
                return entry.getValue();
        }
        return null;
    }

    public boolean hasDeviceType(String deviceType) {
        Iterator<Map.Entry<String,NeighbourDevice>> it = macToNeighbourDevice.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String,NeighbourDevice> entry = it.next();
            if(entry.getValue().getType().equals(deviceType))
                return true;
        }
        return false;
    }

    public boolean addDevice(String mac, NeighbourDevice neighbourDevice) {
        if(isDevice(mac))
            return false;
        else {
            macToNeighbourDevice.put(mac, neighbourDevice);
            return true;
        }
    }

    public void delDevice(String mac) {
        Iterator<Map.Entry<String,NeighbourDevice>> it = macToNeighbourDevice.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String,NeighbourDevice> entry = it.next();
            if(entry.getValue().getMacAddress().equals(mac)) {
                it.remove();
                return;
            }
        }
    }

    public void delDeviceType(String linkLayerIdentifier) {
        Iterator<Map.Entry<String,NeighbourDevice>> it = macToNeighbourDevice.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String,NeighbourDevice> entry = it.next();
            if(entry.getValue().getType().equals(linkLayerIdentifier))
                it.remove();
        }
    }

    public boolean isDevice(String mac) {
        return macToNeighbourDevice.containsKey(mac);
    }

    public int getNbConnection() {
        return nbConnection;
    }

    public void incNbConnection(){
        nbConnection++;
    }

    public boolean isInRange() {
        if(macToNeighbourDevice.size() > 0)
                return true;
        return false;
    }

    public boolean isInRangeType(String deviceType) {
        for(Map.Entry<String, NeighbourDevice> entry : macToNeighbourDevice.entrySet()) {
            if(entry.getValue().getType().equals(deviceType))
                return true;
        }
        return false;
    }

    public boolean isAlreadyConnected() {
        for(Map.Entry<String, NeighbourDevice> entry : macToNeighbourDevice.entrySet()) {
            if(entry.getValue().isConnected())
                return true;
        }
        return false;
    }


    public boolean isConnectedFromLinkLayer(String deviceType) {
        for(Map.Entry<String, NeighbourDevice> entry : macToNeighbourDevice.entrySet()) {
            if(entry.getValue().isConnected() && entry.getValue().getType().equals(deviceType))
                return true;
        }
        return false;
    }


    public NeighbourDevice getBestDevice() {
        return macToNeighbourDevice.get(id);
    }

}
