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

package org.disrupted.rumble.network.linklayer.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.support.v4.net.ConnectivityManagerCompat;
import android.util.Log;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.Neighbour;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.ThreadPoolCoordinator;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;
import org.disrupted.rumble.network.protocols.firechat.FirechatOverUDPMulticast;

import de.greenrobot.event.EventBus;


/**
 * @author Marlinski
 */
public class WifiManagedLinkLayerAdapter extends LinkLayerAdapter {

    private static final String TAG = "WifiManagedLinkLayerAdapter";

    public static final String LinkLayerIdentifier = "WIFI-Managed";

    private String macAddress;
    private WifiManager wifiMan;
    private WifiInfo wifiInf;
    WifiManager.MulticastLock multicastLock;
    private boolean register;


    public WifiManagedLinkLayerAdapter(NetworkCoordinator networkCoordinator) {
        super(networkCoordinator);
        macAddress = null;
        wifiMan = null;
        wifiInf = null;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return LinkLayerIdentifier;
    }

    @Override
    public void onLinkStart() {
        Log.d(TAG, "[+] Starting Wifi");
        wifiMan = (WifiManager) RumbleApplication.getContext().getSystemService(Context.WIFI_SERVICE);
        wifiInf = wifiMan.getConnectionInfo();
        macAddress = wifiInf.getMacAddress();
        multicastLock = wifiMan.createMulticastLock("rumble");

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        RumbleApplication.getContext().registerReceiver(mReceiver, filter);
        register = true;

        /*
         * we enable multicast packet over WiFi, it is usually disabled to save battery but we
         * need it to send/receive message
         */
        multicastLock.acquire();
        ThreadPoolCoordinator.getInstance().addNetworkThread( new FirechatOverUDPMulticast() );

    }

    @Override
    public void onLinkStop() {
        Log.d(TAG, "[-] Stopping Wifi");
        if(register)
            RumbleApplication.getContext().unregisterReceiver(mReceiver);

        ThreadPoolCoordinator.getInstance().killThreadType(LinkLayerIdentifier);
        NetworkCoordinator.getInstance().removeNeighborsType(LinkLayerIdentifier);
        multicastLock.release();

        wifiInf = null;
        wifiMan = null;

    }

    @Override
    public boolean isScanning() {
        return false;
    }

    @Override
    public void forceDiscovery() {
    }

    @Override
    public void connectTo(Neighbour neighbour, boolean force) {
    }


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                Log.d(TAG, intent.toString());
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if(info != null) {
                    if(info.isConnected()) {
                        Log.d(TAG, "[+] connected to the network");
                        ThreadPoolCoordinator.getInstance().addNetworkThread(new FirechatOverUDPMulticast());
                    }
                }
            }
        }
    };


}
