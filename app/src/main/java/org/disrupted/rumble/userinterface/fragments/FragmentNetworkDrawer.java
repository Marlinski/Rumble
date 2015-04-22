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
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;

import org.disrupted.rumble.R;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothUtil;
import org.disrupted.rumble.network.linklayer.wifi.WifiUtil;
import org.disrupted.rumble.userinterface.adapter.NeighborhoodListAdapter;
import org.disrupted.rumble.network.NeighbourInfo;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.events.BluetoothScanEnded;
import org.disrupted.rumble.network.linklayer.events.BluetoothScanStarted;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStarted;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStopped;
import org.disrupted.rumble.network.linklayer.events.NeighborhoodChanged;
import org.disrupted.rumble.network.linklayer.wifi.WifiManagedLinkLayerAdapter;

import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FragmentNetworkDrawer extends Fragment {

    public static final String TAG = "FragmentNetworkDrawer";

    private LinearLayout mDrawerFragmentLayout;
    private ListView mDrawerNeighbourList;
    private NeighborhoodListAdapter listAdapter;
    NetworkCoordinator mNetworkCoordinator;
    boolean mBound = false;
    Switch bluetoothToggle;
    Switch wifiToggle;
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
        if(mBound)
            getActivity().unbindService(mConnection);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if(listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        mDrawerFragmentLayout   = (LinearLayout) inflater.inflate(R.layout.fragment_network_drawer, container, false);
        bluetoothToggle = (Switch) mDrawerFragmentLayout.findViewById(R.id.toggle_bluetooth);
        wifiToggle      = (Switch) mDrawerFragmentLayout.findViewById(R.id.toggle_wifi);
        forceScan       = (ImageButton) mDrawerFragmentLayout.findViewById(R.id.scanningButton);

        bluetoothToggle.setOnClickListener(onBluetoothToggleClicked);
        bluetoothToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                refreshInterfaces();
            }
        });
        wifiToggle.setOnClickListener(onWifiToggleClicked);
        wifiToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                refreshInterfaces();
            }
        });

        forceScan.setOnClickListener(onForceScanClicked);

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
            refreshInterfaces();
            initializeNeighbourview();
            initializeProgressBar();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

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

    private void refreshInterfaces() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bluetoothToggle.setChecked(
                        BluetoothUtil.isEnabled() &&
                        mNetworkCoordinator.isLinkLayerEnabled(BluetoothLinkLayerAdapter.LinkLayerIdentifier));
                wifiToggle.setChecked(
                        WifiUtil.isEnabled() &&
                        mNetworkCoordinator.isLinkLayerEnabled(WifiManagedLinkLayerAdapter.LinkLayerIdentifier));
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

    /*
     *         User Interactions
     */
    View.OnClickListener onBluetoothToggleClicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (!mNetworkCoordinator.isLinkLayerEnabled(BluetoothLinkLayerAdapter.LinkLayerIdentifier)) {
                        mNetworkCoordinator.linkLayerStart(BluetoothLinkLayerAdapter.LinkLayerIdentifier);
                        if (!BluetoothUtil.isEnabled())
                            BluetoothUtil.enableBT(getActivity());
                        else if (!BluetoothUtil.isDiscoverable())
                            BluetoothUtil.discoverableBT(getActivity());
                    } else {
                        if (!BluetoothUtil.isEnabled())
                            BluetoothUtil.enableBT(getActivity());
                        else if (!BluetoothUtil.isDiscoverable())
                            BluetoothUtil.discoverableBT(getActivity());
                        else
                            mNetworkCoordinator.linkLayerStop(BluetoothLinkLayerAdapter.LinkLayerIdentifier);
                    }
                }
            }).start();
        }
    };

    public void manageBTCode(int requestCode, int resultCode, Intent data) {
        if(!mBound)
            return;
        if((requestCode == BluetoothUtil.REQUEST_ENABLE_BT) && (resultCode == getActivity().RESULT_OK)) {
            if(!BluetoothUtil.isDiscoverable())
                BluetoothUtil.discoverableBT(getActivity());
            refreshInterfaces();
            return;
        }
        if((requestCode == BluetoothUtil.REQUEST_ENABLE_BT) && (resultCode == getActivity().RESULT_CANCELED)) {
            mNetworkCoordinator.linkLayerStop(BluetoothLinkLayerAdapter.LinkLayerIdentifier);
            refreshInterfaces();
            return;
        }

        if((requestCode == BluetoothUtil.REQUEST_ENABLE_DISCOVERABLE) && (resultCode == getActivity().RESULT_OK)) {
            Log.d(TAG, "[+] Device Discoverable");
            refreshInterfaces();
            return;
        }
    }

    View.OnClickListener onWifiToggleClicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    //WifiUtil.enableAP();
                    if (!mNetworkCoordinator.isLinkLayerEnabled(WifiManagedLinkLayerAdapter.LinkLayerIdentifier)) {
                        mNetworkCoordinator.linkLayerStart(WifiManagedLinkLayerAdapter.LinkLayerIdentifier);
                        if (!WifiUtil.isEnabled())
                            WifiUtil.enableWifi();
                    } else {
                        if (!WifiUtil.isEnabled())
                            WifiUtil.enableWifi();
                        else
                            mNetworkCoordinator.linkLayerStop(WifiManagedLinkLayerAdapter.LinkLayerIdentifier);
                    }
                }
            }).start();
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
     *          Event management
     */
    public void onEvent(LinkLayerStarted event) {
        refreshInterfaces();
    }
    public void onEvent(LinkLayerStopped event) {
        refreshInterfaces();
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