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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
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

import org.disrupted.rumble.R;
import org.disrupted.rumble.network.NeighbourManager;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothUtil;
import org.disrupted.rumble.network.linklayer.wifi.WifiLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.wifi.WifiUtil;
import org.disrupted.rumble.network.linklayer.events.WifiModeChanged;
import org.disrupted.rumble.userinterface.adapter.NeighborhoodListAdapter;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.events.BluetoothScanEnded;
import org.disrupted.rumble.network.linklayer.events.BluetoothScanStarted;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStarted;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStopped;
import org.disrupted.rumble.network.linklayer.events.NeighborhoodChanged;
import org.disrupted.rumble.userinterface.views.MultiStateButton;

import java.util.Set;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FragmentNetworkDrawer extends Fragment {

    public static final String TAG = "FragmentNetworkDrawer";

    private LinearLayout mDrawerFragmentLayout;
    private MultiStateButton bluetoothController;
    private MultiStateButton wifiController;
    private ListView mDrawerNeighbourList;
    private NeighborhoodListAdapter listAdapter;
    private NetworkCoordinator mNetworkCoordinator;
    boolean mBound = false;
    ImageButton  forceScan;

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
        if(mBound) {
            getActivity().unbindService(mConnection);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        refreshBluetoothController();
        refreshWifiController();
        if(listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        mDrawerFragmentLayout = (LinearLayout)inflater.inflate(R.layout.fragment_network_drawer, container, false);

        // create the multistate bluetooth controller
        bluetoothController   = (MultiStateButton)mDrawerFragmentLayout.findViewById(R.id.bluetooth_controller);
        bluetoothController.addState(R.drawable.ic_bluetooth_disabled_white_18dp);
        bluetoothController.addState(R.drawable.ic_bluetooth_white_18dp);
        bluetoothController.addState(R.drawable.ic_bluetooth_searching_white_18dp);
        bluetoothController.setOnMultiStateClickListener(bluetoothControllerClick);

        // create the multistate wifi controller
        wifiController   = (MultiStateButton)mDrawerFragmentLayout.findViewById(R.id.wifi_controller);
        wifiController.addState(R.drawable.ic_signal_wifi_off_white_18dp);
        wifiController.addState(R.drawable.ic_signal_wifi_4_bar_white_18dp);
        wifiController.addState(R.drawable.ic_wifi_tethering_white_18dp);
        wifiController.setOnMultiStateClickListener(wifiControllerClick);

        // set the force scan button to refresh neighborhood
        forceScan       = (ImageButton)  mDrawerFragmentLayout.findViewById(R.id.scanningButton);
        forceScan.setOnClickListener(onForceScanClicked);

        // bind to the networkCoordinator service
        Intent intent = new Intent(getActivity(), NetworkCoordinator.class);
        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        return mDrawerFragmentLayout;
    }

    /*
     * Connection to the network service
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            NetworkCoordinator.LocalBinder binder = (NetworkCoordinator.LocalBinder) service;
            mNetworkCoordinator = binder.getService();
            mBound = true;
            initializeNeighbourview();
            initializeProgressBar();
            refreshBluetoothController();
            refreshWifiController();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    View.OnClickListener onForceScanClicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (mBound)
                        mNetworkCoordinator.forceScan();
                }
            }).start();
        }
    };

    /*
     * Bluetooth Logic
     */
    MultiStateButton.OnMultiStateClickListener bluetoothControllerClick = new MultiStateButton.OnMultiStateClickListener() {
        @Override
        public void onMultiStateClick(int oldState, int newState) {
            if(newState == oldState)
                return;
            switch (newState) {
                case 0:
                    if (BluetoothUtil.isEnabled())
                        BluetoothUtil.disableBT();
                    break;
                case 1:
                    if (!BluetoothUtil.isEnabled())
                        BluetoothUtil.enableBT(getActivity());
                    if (oldState == 2) {
                        BluetoothUtil.discoverableBT(getActivity(), 1);
                    }
                    break;
                case 2:
                    if (!BluetoothUtil.isEnabled()) {
                        BluetoothUtil.enableBT(getActivity());
                    } else {
                        if(!BluetoothUtil.isDiscoverable())
                            BluetoothUtil.discoverableBT(getActivity(), 3600);
                    }
                    break;
            }
            bluetoothController.setSelected(newState);
        }
    };

    MultiStateButton.OnMultiStateClickListener wifiControllerClick = new MultiStateButton.OnMultiStateClickListener() {
        @Override
        public void onMultiStateClick(final int oldState, int newState) {
            if(newState == oldState)
                return;
            wifiController.setSelected(newState);
            final int state = newState;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    switch (state) {
                        case 0:
                            if(WifiUtil.isEnabled())
                                WifiUtil.disableWifi();
                            if(WifiUtil.isWiFiApEnabled())
                                WifiUtil.disableAP();
                            break;
                        case 1:
                            EventBus.getDefault().post(new WifiModeChanged(WifiLinkLayerAdapter.WIFIMODE.WIFIMANAGED));
                            break;
                        case 2:
                            EventBus.getDefault().post(new WifiModeChanged(WifiLinkLayerAdapter.WIFIMODE.WIFIAP));
                            break;
                    }
                }
            }).start();
        }
    };


    public void manageBTCode(int requestCode, int resultCode, Intent data) {
        if(!mBound)
            return;
        if((requestCode == BluetoothUtil.REQUEST_ENABLE_BT) && (resultCode == Activity.RESULT_OK)) {
            if(!BluetoothUtil.isDiscoverable())
                BluetoothUtil.discoverableBT(getActivity(), 3600);
            refreshBluetoothController();
            return;
        }
        if((requestCode == BluetoothUtil.REQUEST_ENABLE_BT) && (resultCode == Activity.RESULT_CANCELED)) {
            refreshBluetoothController();
            return;
        }
        if(requestCode == BluetoothUtil.REQUEST_ENABLE_DISCOVERABLE)  {
            // the resultCode carries the discoverability timeout,
            // 1 means that we are disabling the discoverability
            if(resultCode != 1)
                bluetoothController.setSelected(2);
            else
                bluetoothController.setSelected(1);
            return;
        }
    }

    private void refreshBluetoothController() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (BluetoothUtil.isEnabled()) {
                    bluetoothController.setSelected(1);
                    if (BluetoothUtil.isDiscoverable())
                        bluetoothController.setSelected(2);
                } else {
                    bluetoothController.setSelected(0);
                }
                if(BluetoothUtil.isWorking()) {
                    bluetoothController.disable();
                } else {
                    bluetoothController.enable();
                }
            }
        });
    }

    private void refreshWifiController() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(WifiUtil.isWiFiApEnabled()) {
                    wifiController.setSelected(2);
                } else if (WifiUtil.isEnabled()) {
                    wifiController.setSelected(1);
                    if(WifiUtil.isConnected()) {
                        wifiController.setStateResource(1,R.drawable.ic_signal_wifi_4_bar_white_18dp);
                    } else {
                        wifiController.setStateResource(1,R.drawable.ic_signal_wifi_statusbar_connected_no_internet_4_white_18dp);
                    }
                } else {
                    wifiController.setSelected(0);
                }
            }
        });
    }

    public void initializeNeighbourview() {
        if (mNetworkCoordinator == null)
            return;
        mDrawerNeighbourList = (ListView) mDrawerFragmentLayout.findViewById(R.id.neighbours_list_view);
        Set<NeighbourManager.Neighbour> neighborhood = mNetworkCoordinator.neighbourManager.getNeighbourList();
        listAdapter = new NeighborhoodListAdapter(getActivity(), neighborhood);
        mDrawerNeighbourList.setAdapter(listAdapter);
        neighborhood.clear();
    }
    public void initializeProgressBar() {
        if (mNetworkCoordinator == null)
            return;
        if (mNetworkCoordinator.isScanning()) {
            ((ImageButton) mDrawerFragmentLayout.findViewById(R.id.scanningButton)).setVisibility(View.GONE);
            ((ProgressBar) mDrawerFragmentLayout.findViewById(R.id.scanningProgressBar)).setVisibility(View.VISIBLE);
        }
    }
    private void refreshNeighborhood() {
        if (mNetworkCoordinator == null)
            return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Set<NeighbourManager.Neighbour> neighborhood = mNetworkCoordinator.neighbourManager.getNeighbourList();
                if (mDrawerNeighbourList.getAdapter() != null)
                    listAdapter.swap(neighborhood);
                neighborhood.clear();
                listAdapter.notifyDataSetChanged();
            }
        });
    }


    /*
     *          Event management
     */
    public void onEvent(LinkLayerStarted event) {
        refreshBluetoothController();
        refreshWifiController();
    }
    public void onEvent(LinkLayerStopped event) {
        refreshBluetoothController();
        refreshWifiController();
    }
    public void onEvent(NeighborhoodChanged event) {
        refreshNeighborhood();
    }
    public void onEvent(BluetoothScanStarted event) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((ImageButton) mDrawerFragmentLayout.findViewById(R.id.scanningButton)).setVisibility(View.GONE);
                ((ProgressBar) mDrawerFragmentLayout.findViewById(R.id.scanningProgressBar)).setVisibility(View.VISIBLE);
            }
        });
    }
    public void onEvent(BluetoothScanEnded event) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((ImageButton) mDrawerFragmentLayout.findViewById(R.id.scanningButton)).setVisibility(View.VISIBLE);
                ((ProgressBar) mDrawerFragmentLayout.findViewById(R.id.scanningProgressBar)).setVisibility(View.GONE);
            }
        });
    }
}