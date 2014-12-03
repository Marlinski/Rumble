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

package org.disrupted.rumble.fragments;

import android.content.Intent;
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

import org.disrupted.rumble.HomeActivity;
import org.disrupted.rumble.R;
import org.disrupted.rumble.adapter.NeighborhoodListAdapter;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.events.BluetoothScanEnded;
import org.disrupted.rumble.network.events.BluetoothScanStarted;
import org.disrupted.rumble.network.events.ConnectToNeighbourDevice;
import org.disrupted.rumble.network.events.NeighborhoodChanged;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConfigureInteraction;

import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class DrawerNeighborhoodFragment extends Fragment {

    public static final String TAG = "NeighborhoodDrawerFragment";

    private LinearLayout mDrawerFragmentLayout;
    private ListView mDrawerNeighbourList;
    private NeighborhoodListAdapter listAdapter;

    private boolean registered;

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        EventBus.getDefault().register(this);
        registered = true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mDrawerFragmentLayout   = (LinearLayout) inflater.inflate(R.layout.neighborhood_drawer, container, false);
        initializeNeighbourview();
        initializeProgressBar();
        return mDrawerFragmentLayout;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, " onResume");
        if(listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    public void initializeNeighbourview() {
        mDrawerNeighbourList = (ListView) mDrawerFragmentLayout.findViewById(R.id.neighbours_list_view);
        if( ((HomeActivity)getActivity()).mBound) {
            List<String> neighborhood = ((HomeActivity) getActivity()).mNetworkCoordinator.getNeighborList();
            listAdapter = new NeighborhoodListAdapter(getActivity(), neighborhood);
        }else {
            listAdapter = new NeighborhoodListAdapter(getActivity());
        }
        mDrawerNeighbourList.setAdapter(listAdapter);
    }

    public void initializeProgressBar() {
        if(((HomeActivity)getActivity()).mBound) {
            if(((HomeActivity)getActivity()).mNetworkCoordinator.isScanning()){
                ((ImageButton)mDrawerFragmentLayout.findViewById(R.id.scanningButton)).setVisibility(View.GONE);
                ((ProgressBar)mDrawerFragmentLayout.findViewById(R.id.scanningProgressBar)).setVisibility(View.VISIBLE);
            }
        }
    }

    public void onEvent(BluetoothScanStarted event) {
        // style="?android:attr/progressBarStyleHorizontal"
        ((ImageButton)mDrawerFragmentLayout.findViewById(R.id.scanningButton)).setVisibility(View.GONE);
        ((ProgressBar)mDrawerFragmentLayout.findViewById(R.id.scanningProgressBar)).setVisibility(View.VISIBLE);
    }

    public void onEvent(BluetoothScanEnded event) {
        ((ImageButton)mDrawerFragmentLayout.findViewById(R.id.scanningButton)).setVisibility(View.VISIBLE);
        ((ProgressBar)mDrawerFragmentLayout.findViewById(R.id.scanningProgressBar)).setVisibility(View.GONE);
    }

    public void onEvent(NeighborhoodChanged event) {
        refresh();
    }

    public void onEvent(ConnectToNeighbourDevice event) {
        //todo: refresh only the neighbour being affected
        refresh();
    }

    private void refresh() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if( ((HomeActivity)getActivity()).mBound) {
                    List<String> neighborhood = ((HomeActivity) getActivity()).mNetworkCoordinator.getNeighborList();
                    if (mDrawerNeighbourList.getAdapter() != null)
                        ((NeighborhoodListAdapter) mDrawerNeighbourList.getAdapter()).updateList(neighborhood);
                }
            }
        });
    }

    public void onForceScanClicked(View view) {
        ((HomeActivity) getActivity()).mNetworkCoordinator.forceScan();
    }

    /*
     * =======================================
     * Bluetooth and Wifi Interface Management
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

    private void onBluetoothDisable() {
        Intent startDiscovery = new Intent(getActivity(), NetworkCoordinator.class);
        startDiscovery.setAction(NetworkCoordinator.ACTION_FORCE_STOP_BLUETOOTH);
        getActivity().startService(startDiscovery);
    }

    private void onBluetoothEnable() {
        Log.d(TAG, "[+] Enabling Bluetooth and making it Discoverable");

        if(!BluetoothConfigureInteraction.isEnabled(getActivity())) {
            BluetoothConfigureInteraction.enableBT(getActivity());
            return;
        }
        Log.d(TAG, "[+] Sending START BLUETOOTH intent");
        Intent startDiscovery= new Intent(getActivity(), NetworkCoordinator.class);
        startDiscovery.setAction(NetworkCoordinator.ACTION_FORCE_START_BLUETOOTH);
        getActivity().startService(startDiscovery);

        if(!BluetoothConfigureInteraction.isDiscoverable(getActivity())) {
            BluetoothConfigureInteraction.discoverableBT(getActivity());
            return;
        }
    }

    public void manageBTCode(int requestCode, int resultCode, Intent data) {
        if((requestCode == BluetoothConfigureInteraction.REQUEST_ENABLE_BT) && (resultCode == getActivity().RESULT_OK)) {
            Log.d(TAG, "-- Bluetooth Enabled");

            Log.d(TAG, "[+] Sending START BLUETOOTH intent");
            Intent startDiscovery= new Intent(getActivity(), NetworkCoordinator.class);
            startDiscovery.setAction(NetworkCoordinator.ACTION_FORCE_START_BLUETOOTH);
            getActivity().startService(startDiscovery);

            if(!BluetoothConfigureInteraction.isDiscoverable(getActivity())) {
                BluetoothConfigureInteraction.discoverableBT(getActivity());
                return;
            }
            return;
        }

        if((requestCode == BluetoothConfigureInteraction.REQUEST_ENABLE_BT) && (resultCode == getActivity().RESULT_CANCELED)) {
            ((Switch)getActivity().findViewById(R.id.toggle_bluetooth)).setChecked(false);
        }

        if((requestCode == BluetoothConfigureInteraction.REQUEST_ENABLE_DISCOVERABLE) && (resultCode == getActivity().RESULT_OK)) {
            Log.d(TAG, "Device Discoverable");
            return;
        }
    }

    private ActionBar getActionBar() {
        return ((ActionBarActivity) getActivity()).getSupportActionBar();
    }

}