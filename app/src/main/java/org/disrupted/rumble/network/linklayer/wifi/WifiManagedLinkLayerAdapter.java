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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStarted;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStopped;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;

import de.greenrobot.event.EventBus;


/**
 * @author Marlinski
 */
public class WifiManagedLinkLayerAdapter implements LinkLayerAdapter {

    private static final String TAG = "WifiManagedLinkLayerAdapter";

    public static final String LinkLayerIdentifier = "WIFIManagedMode";

    private NetworkCoordinator networkCoordinator;
    private String macAddress;
    private WifiManager wifiMan;
    private WifiInfo wifiInf;
    WifiManager.MulticastLock multicastLock;

    public boolean register;
    public boolean activated;

    public WifiManagedLinkLayerAdapter(NetworkCoordinator networkCoordinator) {
        this.networkCoordinator = networkCoordinator;
        macAddress = null;
        wifiMan    = null;
        wifiInf    = null;
        register   = false;
    }

    @Override
    public boolean isActivated() {
        return activated;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return LinkLayerIdentifier;
    }

    @Override
    public void linkStart() {
        if(register)
            return;

        Log.d(TAG, "[+] Starting Wifi Managed");
        wifiMan = (WifiManager) RumbleApplication.getContext().getSystemService(Context.WIFI_SERVICE);
        wifiInf = wifiMan.getConnectionInfo();
        macAddress = wifiInf.getMacAddress();
        multicastLock = wifiMan.createMulticastLock("org.disruptedsystems.rumble");

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        RumbleApplication.getContext().registerReceiver(mReceiver, filter);
        register = true;

        if (!wifiMan.isWifiEnabled()) {
            wifiMan.setWifiEnabled(true);
        } else {
            linkConnected();
            activated = true;
        }
    }

    @Override
    public void linkStop() {
        if(!register)
            return;
        register = false;

        Log.d(TAG, "[-] Stopping Wifi Managed");
        linkDisconnected();

        RumbleApplication.getContext().unregisterReceiver(mReceiver);

        wifiInf = null;
        wifiMan = null;
    }

    private void linkConnected() {
        if(activated)
            return;
        activated = true;

        /*
         * we enable multicast packet over WiFi, it is usually disabled to save battery but we
         * need it to send/receive message
         */
        multicastLock.acquire();

        EventBus.getDefault().post(new LinkLayerStarted(getLinkLayerIdentifier()));
    }

    private void linkDisconnected() {
        if(!activated)
            return;
        activated = false;

        EventBus.getDefault().post(new LinkLayerStopped(getLinkLayerIdentifier()));

        multicastLock.release();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                Log.d(TAG, intent.toString());
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                NetworkInfo.State state = networkInfo.getState();

                if(state == NetworkInfo.State.CONNECTED){
                    if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                        if(activated)
                            return;

                        Log.d(TAG, "[+] connected to a wifi access point");
                        linkConnected();
                    }
                }
                if(state == NetworkInfo.State.DISCONNECTED) {
                    if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                        if(!activated)
                            return;

                        Log.d(TAG, "[-] disconnected from a wifi access point");
                        linkDisconnected();
                    }
                }
            }
        }
    };

}
