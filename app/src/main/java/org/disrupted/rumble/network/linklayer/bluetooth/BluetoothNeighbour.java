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

package org.disrupted.rumble.network.linklayer.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.Neighbour;

/**
 * @author Marlinski
 */
public class BluetoothNeighbour extends Neighbour {

    private String bluetoothDeviceName;

    public BluetoothNeighbour(BluetoothNeighbour neighbour){
        super(neighbour.getMacAddress());
        this.bluetoothDeviceName = neighbour.bluetoothDeviceName;
    }

    public BluetoothNeighbour(String macAddress){
        super(macAddress);
        BluetoothAdapter adapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
        if(adapter != null) {
            BluetoothDevice remote = adapter.getRemoteDevice(macAddress);
            if(remote != null)
                bluetoothDeviceName = remote.getName();
            else
                bluetoothDeviceName = "#no name#";
        } else {
            bluetoothDeviceName = "#no name#";
        }
    }

    @Override
    public String getLinkLayerName() {
        return bluetoothDeviceName;
    }

    @Override
    public String getLinkLayerType() {
        return BluetoothLinkLayerAdapter.LinkLayerIdentifier;
    }

}
