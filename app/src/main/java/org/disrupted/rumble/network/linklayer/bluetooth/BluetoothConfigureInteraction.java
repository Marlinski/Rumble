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

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.util.Log;

/**
 * @author Marlinski
 */
public class BluetoothConfigureInteraction {

    private static final String TAG = "BluetoothConfigure";

    public static final int REQUEST_ENABLE_BT = 1;
    public static final int REQUEST_ENABLE_DISCOVERABLE = 2;

    public static boolean isEnabled(Activity activity) {
        if(BluetoothUtil.getBluetoothAdapter(activity) == null)
            return false;
        return BluetoothUtil.getBluetoothAdapter(activity).isEnabled();
    }

    public static void enableBT(Activity activity) {
        if (BluetoothUtil.getBluetoothAdapter(activity) == null) {
            Log.d(TAG, "mBluetoothAdapter is null");
            return;
        }

        if (!isEnabled(activity)) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
        }
    }

    public static boolean isDiscoverable(Activity activity) {
        if(BluetoothUtil.getBluetoothAdapter(activity) == null)
            return false;
        return (BluetoothUtil.getBluetoothAdapter(activity).getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
    }

    public static void discoverableBT(Activity activity) {
        if (BluetoothUtil.getBluetoothAdapter(activity) == null)
            return;

        if (!BluetoothUtil.getBluetoothAdapter(activity).isEnabled()) {
            Log.d(TAG, "cannot request discoverable: enable Bluetooth first");
            return;
        }

        if (BluetoothUtil.getBluetoothAdapter(activity).getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableBTIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
            activity.startActivityForResult(discoverableBTIntent, REQUEST_ENABLE_DISCOVERABLE);
        }
    }

}
