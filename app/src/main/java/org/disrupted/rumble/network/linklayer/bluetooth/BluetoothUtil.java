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

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.app.RumbleApplication;

import java.util.UUID;

/**
 * @author Lucien Loiseau
 */
public class BluetoothUtil {

    private static final String TAG = "BluetoothUtil";


    public static final int REQUEST_ENABLE_BT = 1;
    public static final int REQUEST_ENABLE_DISCOVERABLE = 2;

    public static BluetoothAdapter getBluetoothAdapter(Context context) {
        BluetoothAdapter adapter = null;
        if( Build.VERSION.SDK_INT  <= Build.VERSION_CODES.JELLY_BEAN_MR1)
            adapter = BluetoothAdapter.getDefaultAdapter();
        else {
            BluetoothManager btManager = (BluetoothManager) (context.getSystemService(Context.BLUETOOTH_SERVICE));
            adapter = btManager.getAdapter();
        }
        if(adapter == null)
            Log.d(TAG, "mBluetoothAdapter is null");
        return adapter;
    }

    public static String getBluetoothMacAddress() {
        BluetoothAdapter mBluetoothAdapter = getBluetoothAdapter(RumbleApplication.getContext());

        if(mBluetoothAdapter==null)
            return null;

        return mBluetoothAdapter.getAddress();
    }

    public static boolean isWorking() {
        BluetoothAdapter adapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
        if ( adapter == null)
            return false;
        return ((adapter.getScanMode() == BluetoothAdapter.STATE_CONNECTING) ||
                (adapter.getScanMode() == BluetoothAdapter.STATE_DISCONNECTING) );
    }

    public static boolean isEnabled() {
        BluetoothAdapter adapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
        if(adapter == null)
            return false;
        return adapter.isEnabled();
    }

    public static void disableBT() {
        BluetoothAdapter adapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
        if ( adapter == null)
            return;
        if (isEnabled())
            adapter.disable();
    }

    public static boolean isDiscoverable() {
        BluetoothAdapter adapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
        if ( adapter == null)
            return false;
        return (adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
    }

    /*
     * Interaction that requires an activity
     */
    public static void enableBT(Activity activity) {
        if (!isEnabled()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
        }
    }

    public static void discoverableBT(Activity activity, int duration) {
        if (!isEnabled()) {
            Log.d(TAG, "cannot request discoverable: enable Bluetooth first");
            return;
        }
        Intent discoverableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableBTIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
        activity.startActivityForResult(discoverableBTIntent, REQUEST_ENABLE_DISCOVERABLE);
    }

    public static void prependRumblePrefixToDeviceName(String prefix) {
        BluetoothAdapter adapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
        if (adapter == null)
            return;
        String name = adapter.getName();
        if(name == null)
            return;
        if(name.startsWith(prefix))
            return;
        else
            adapter.setName(prefix+name);
    }

    public static void unprependRumblePrefixFromDeviceName(String prefix) {
        BluetoothAdapter adapter = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext());
        if (adapter == null)
            return;
        String name = adapter.getName();
        if(name == null)
            return;
        if(name.startsWith(prefix))
            adapter.setName(name.substring(prefix.length()));
        else
            return;
    }
}