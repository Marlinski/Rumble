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

package org.disrupted.rumble.userinterface.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;

import org.disrupted.rumble.R;
import org.disrupted.rumble.userinterface.adapter.NeighborhoodListAdapter;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.NeighbourInfo;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.events.BluetoothScanEnded;
import org.disrupted.rumble.network.events.BluetoothScanStarted;
import org.disrupted.rumble.network.events.LinkLayerStarted;
import org.disrupted.rumble.network.events.LinkLayerStopped;
import org.disrupted.rumble.network.events.NeighborhoodChanged;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConfigureInteraction;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.wifi.WifiManagedLinkLayerAdapter;

import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FragmentNetworkDrawer extends Fragment {

    public static final String TAG = "NeighborhoodDrawerFragment";

    private LinearLayout mDrawerFragmentLayout;
    private ListView mDrawerNeighbourList;
    private NeighborhoodListAdapter listAdapter;
    NetworkCoordinator mNetworkCoordinator;
    boolean mBound = false;

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView");
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
        if(mBound)
            getActivity().unbindService(mConnection);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        mDrawerFragmentLayout   = (LinearLayout) inflater.inflate(R.layout.fragment_network_drawer, container, false);
        Intent intent = new Intent(getActivity(), NetworkCoordinator.class);
        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        return mDrawerFragmentLayout;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if(listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    public void initializeInterfaces() {
        Switch bluetoothSwitch = ((Switch) mDrawerFragmentLayout.findViewById(R.id.toggle_bluetooth));
        bluetoothSwitch.setChecked(mNetworkCoordinator.isLinkLayerEnabled(BluetoothLinkLayerAdapter.LinkLayerIdentifier));
    }

    public void initializeNeighbourview() {
        mDrawerNeighbourList = (ListView) mDrawerFragmentLayout.findViewById(R.id.neighbours_list_view);
        List<NeighbourInfo> neighborhood = mNetworkCoordinator.neighbourManager.getNeighbourList();
        listAdapter = new NeighborhoodListAdapter(getActivity(), neighborhood);
        mDrawerNeighbourList.setAdapter(listAdapter);
    }

    public void initializeProgressBar() {
        if (mNetworkCoordinator.isScanning()) {
            ((ImageButton) mDrawerFragmentLayout.findViewById(R.id.scanningButton)).setVisibility(View.GONE);
            ((ProgressBar) mDrawerFragmentLayout.findViewById(R.id.scanningProgressBar)).setVisibility(View.VISIBLE);
        }
    }

    public void onEvent(LinkLayerStarted event) {
        refreshInterfaces();
    }
    public void onEvent(LinkLayerStopped event) {
        refreshInterfaces();
    }
    public void onEvent(BluetoothScanStarted event) {
        ((ImageButton)mDrawerFragmentLayout.findViewById(R.id.scanningButton)).setVisibility(View.GONE);
        ((ProgressBar)mDrawerFragmentLayout.findViewById(R.id.scanningProgressBar)).setVisibility(View.VISIBLE);
    }
    public void onEvent(BluetoothScanEnded event) {
        ((ImageButton)mDrawerFragmentLayout.findViewById(R.id.scanningButton)).setVisibility(View.VISIBLE);
        ((ProgressBar)mDrawerFragmentLayout.findViewById(R.id.scanningProgressBar)).setVisibility(View.GONE);
    }
    public void onEvent(NeighborhoodChanged event) {
        refreshNeighborhood();
    }


    private void refreshInterfaces() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Switch bluetoothSwitch = ((Switch) mDrawerFragmentLayout.findViewById(R.id.toggle_bluetooth));
                bluetoothSwitch.setChecked(mNetworkCoordinator.isLinkLayerEnabled(BluetoothLinkLayerAdapter.LinkLayerIdentifier));
            }
        });
    }
    private void refreshNeighborhood() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                List<NeighbourInfo> neighborhood = mNetworkCoordinator.neighbourManager.getNeighbourList();
                if (mDrawerNeighbourList.getAdapter() != null) {
                    ((NeighborhoodListAdapter) mDrawerNeighbourList.getAdapter()).updateList(neighborhood);
                }
            }
        });
    }


    public void onForceScanClicked(View view) {
        if(mBound)
            mNetworkCoordinator.forceScan();
    }


    private ActionBar getActionBar() {
        return ((ActionBarActivity) getActivity()).getSupportActionBar();
    }

    /*
     * =======================================
     * Bluetooth Interface Management
     * =======================================
     */
    public void onBluetoothToggleClicked(View view) {
        boolean on = ((Switch) view).isChecked();
        if (on) {
            onBluetoothEnable();
        } else {
            onBluetoothDisable();
        }
    }

    /*
     * when starting the bluetooth link layer, we save the current state of the bluetooth interface
     * in order to put it back to the same state once the application will terminate
     */
    private void onBluetoothEnable() {
        if(!mBound)
            return;
        Log.d(TAG, "[+] Enabling Bluetooth and making it Discoverable");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.edit().putBoolean(
                getString(R.string.bluetooth_state_on_openning),
                BluetoothConfigureInteraction.isEnabled(getActivity())).apply();

        if(!BluetoothConfigureInteraction.isEnabled(getActivity())) {
            BluetoothConfigureInteraction.enableBT(getActivity());
            return;
        }

        mNetworkCoordinator.startLinkLayer(BluetoothLinkLayerAdapter.LinkLayerIdentifier);

        if(!BluetoothConfigureInteraction.isDiscoverable(getActivity())) {
            BluetoothConfigureInteraction.discoverableBT(getActivity());
            return;
        }
    }

    /*
     * When the user switches off the bluetooth link layer interface we shutdown the Bluetooth
     * interface if it was shutdown before starting our app
     */
    private void onBluetoothDisable() {
        if(!mBound)
            return;
        mNetworkCoordinator.stopLinkLayer(BluetoothLinkLayerAdapter.LinkLayerIdentifier);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if(prefs.getBoolean(getString(R.string.bluetooth_state_on_openning), false)) {
            BluetoothConfigureInteraction.disableBT(getActivity());
        }
    }

    public void manageBTCode(int requestCode, int resultCode, Intent data) {
        if(!mBound)
            return;
        if((requestCode == BluetoothConfigureInteraction.REQUEST_ENABLE_BT) && (resultCode == getActivity().RESULT_OK)) {
            Log.d(TAG, "[+] Bluetooth Enabled");

            mNetworkCoordinator.startLinkLayer(BluetoothLinkLayerAdapter.LinkLayerIdentifier);

            if(!BluetoothConfigureInteraction.isDiscoverable(getActivity())) {
                BluetoothConfigureInteraction.discoverableBT(getActivity());
                return;
            }
            return;
        }

        if((requestCode == BluetoothConfigureInteraction.REQUEST_ENABLE_BT) && (resultCode == getActivity().RESULT_CANCELED)) {
            ((Switch)mDrawerFragmentLayout.findViewById(R.id.toggle_bluetooth)).setChecked(false);
        }

        if((requestCode == BluetoothConfigureInteraction.REQUEST_ENABLE_DISCOVERABLE) && (resultCode == getActivity().RESULT_OK)) {
            Log.d(TAG, "[+] Device Discoverable");
            return;
        }
    }


    /*
     * =======================================
     * Wifi Interface Management
     * =======================================
     */
     public void onWifiToggleClicked(View view) {
        boolean on = ((Switch) view).isChecked();
        if (on) {
            onWifiEnable();
        } else {
            onWifiDisable();
        }
    }

    private void onWifiEnable() {
        if(!mBound)
            return;
        WifiManager wifiMan = (WifiManager) RumbleApplication.getContext().getSystemService(Context.WIFI_SERVICE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(RumbleApplication.getContext());
        prefs.edit().putBoolean(
                RumbleApplication.getContext().getString(R.string.wifi_state_on_openning),
                wifiMan.isWifiEnabled()).commit();
        if (!wifiMan.isWifiEnabled())
            wifiMan.setWifiEnabled(true);

        mNetworkCoordinator.startLinkLayer(WifiManagedLinkLayerAdapter.LinkLayerIdentifier);
    }


    private void onWifiDisable() {
        if(!mBound)
            return;
        WifiManager wifiMan = (WifiManager) RumbleApplication.getContext().getSystemService(Context.WIFI_SERVICE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(RumbleApplication.getContext());
        if(!prefs.getBoolean(
                RumbleApplication.getContext().getString(R.string.bluetooth_state_on_openning),
                false)) {
            wifiMan.setWifiEnabled(false);
        }

        mNetworkCoordinator.stopLinkLayer(WifiManagedLinkLayerAdapter.LinkLayerIdentifier);
    }


    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            NetworkCoordinator.LocalBinder binder = (NetworkCoordinator.LocalBinder) service;
            mNetworkCoordinator = binder.getService();
            mBound = true;
            initializeInterfaces();
            initializeNeighbourview();
            initializeProgressBar();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


}