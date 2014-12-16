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

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.Neighbour;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.ThreadPoolCoordinator;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;
import org.disrupted.rumble.network.protocols.firechat.FirechatOverUDPMulticast;


/**
 * @author Marlinski
 */
public class WifiManagedLinkLayerAdapter extends LinkLayerAdapter {

    private static final String TAG = "WifiManagedLinkLayerAdapter";

    public static final String LinkLayerIdentifier = "WIFI-Managed";

    String macAddress;
    WifiManager wifiMan;
    WifiInfo wifiInf;


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
        wifiMan = (WifiManager) RumbleApplication.getContext().getSystemService(Context.WIFI_SERVICE);
        if(!wifiMan.isWifiEnabled())
            return;

        /*
         * we enable multicast packet over WiFi, it is usually disabled to save battery but we
         * need it to send/receive message
         */
        WifiManager.MulticastLock multicastLock = wifiMan.createMulticastLock("rumble");
        multicastLock.acquire();

        ThreadPoolCoordinator.getInstance().addNetworkThread( new FirechatOverUDPMulticast() );

        wifiInf = wifiMan.getConnectionInfo();
        macAddress = wifiInf.getMacAddress();
    }

    @Override
    public void onLinkStop() {
        ThreadPoolCoordinator.getInstance().killThreadType(LinkLayerIdentifier);
        NetworkCoordinator.getInstance().removeNeighborsType(LinkLayerIdentifier);

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
}
