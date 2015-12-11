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

package org.disrupted.rumble.network.linklayer.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.util.NetUtil;

/**
 * @author Lucien Loiseau
 */
public class BluetoothNeighbour implements LinkLayerNeighbour {

    private String bluetoothMacAddress;

    public BluetoothNeighbour(String macAddress){
        this.bluetoothMacAddress = macAddress;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return BluetoothLinkLayerAdapter.LinkLayerIdentifier;
    }


    @Override
    public String getLinkLayerAddress() {
        return bluetoothMacAddress;
    }

    @Override
    public String getLinkLayerMacAddress() throws NetUtil.NoMacAddressException {
        return bluetoothMacAddress;
    }

    @Override
    public boolean isLocal() {
        if(bluetoothMacAddress.equals(BluetoothUtil.getBluetoothMacAddress()))
            return true;

        return false;
    }

    public String getBluetoothDeviceName() {
        BluetoothAdapter adapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
        if(adapter != null) {
            BluetoothDevice remote = adapter.getRemoteDevice(this.bluetoothMacAddress);
            if(remote != null)
                return remote.getName();
            else
                return bluetoothMacAddress;
        } else {
            return bluetoothMacAddress;
        }
    }

    @Override
    public boolean equals(Object o) {
        if(o == null)
            return false;

        if(o instanceof  BluetoothNeighbour) {
            BluetoothNeighbour neighbour = (BluetoothNeighbour) o;
            return bluetoothMacAddress.equals(neighbour.bluetoothMacAddress);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return bluetoothMacAddress.hashCode();
    }
}
