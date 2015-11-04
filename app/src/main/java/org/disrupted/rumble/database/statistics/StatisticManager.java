/*
 * Copyright (C) 2014 Disrupted Systems
 * This file is part of Rumble.
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
 * You should have received a copy of the GNU General Public License along
 * with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.database.statistics;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.network.events.ChannelDisconnected;
import org.disrupted.rumble.network.events.ConnectedToAP;
import org.disrupted.rumble.network.events.NeighbourReachable;
import org.disrupted.rumble.network.events.NeighbourUnreachable;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStopped;
import org.disrupted.rumble.util.HashUtil;
import org.disrupted.rumble.util.NetUtil;
import org.disrupted.rumble.util.SharedPreferenceUtil;

import java.util.Random;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class StatisticManager {

    private static final String TAG = "StatisticManager";

    private static final int MAX_IMAGE_SIZE_ON_DISK = 500000;
    private static final int MAX_IMAGE_BORDER_PX = 1000;

    private static final Object lock = new Object();
    private static StatisticManager instance;

    private boolean started;

    public static StatisticManager getInstance() {
        synchronized (lock) {
            if (instance == null)
                instance = new StatisticManager();

            return instance;
        }
    }

    public void start() {
        if(!started) {
            Log.d(TAG, "[+] Starting Statistic Manager");
            started = true;
            EventBus.getDefault().register(this);
        }
    }

    public void stop() {
        if(started) {
            Log.d(TAG, "[-] Stopping Statistic Manager");
            started = false;
            if(EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);
        }
    }

    public void onEvent(LinkLayerStopped event) {
        // then the statistic
        DatabaseFactory.getStatLinkLayerDatabase(RumbleApplication.getContext())
                .insertLinkLayerStat(event.linkLayerIdentifier, event.started_time_nano, event.stopped_time_nano);
    }
    public void onEvent(NeighbourReachable event) {
        String mac;
        try {
            mac = event.neighbour.getLinkLayerMacAddress();
        } catch (NetUtil.NoMacAddressException ie) {
            mac = "00:00:00:00:00:00";
        }
        // first we add the Interface if needed
        long rowId = DatabaseFactory.getStatInterfaceDatabase(RumbleApplication.getContext())
                .getInterfaceDBIDFromMac(mac);
        if(rowId < 0) {
            rowId = DatabaseFactory.getStatInterfaceDatabase(RumbleApplication.getContext())
                    .insertInterface(mac,event.neighbour.getLinkLayerIdentifier().equals(BluetoothLinkLayerAdapter.LinkLayerIdentifier));
        }
        // then the statistic
        DatabaseFactory.getStatReachabilityDatabase(RumbleApplication.getContext())
                .insertReachability(rowId, event.reachable_time_nano, true, 0);
    }
    public void onEvent(NeighbourUnreachable event) {
        String mac;
        try {
            mac = event.neighbour.getLinkLayerMacAddress();
        } catch (NetUtil.NoMacAddressException ie) {
            mac = "00:00:00:00:00:00";
        }
        // first we add the Interface if needed
        long rowId = DatabaseFactory.getStatInterfaceDatabase(RumbleApplication.getContext())
                .getInterfaceDBIDFromMac(mac);
        if(rowId < 0) {
            rowId = DatabaseFactory.getStatInterfaceDatabase(RumbleApplication.getContext())
                    .insertInterface(mac,event.neighbour.getLinkLayerIdentifier().equals(BluetoothLinkLayerAdapter.LinkLayerIdentifier));
        }
        // then the statistic
        DatabaseFactory.getStatReachabilityDatabase(RumbleApplication.getContext())
                .insertReachability(rowId, event.unreachable_time_nano, false, event.unreachable_time_nano-event.reachable_time_nano);
    }
    public void onEvent(ChannelDisconnected event) {
        String mac;
        try {
            mac = event.neighbour.getLinkLayerMacAddress();
        } catch (NetUtil.NoMacAddressException ie) {
            return;
        }
        // first we add the Interface if needed
        long rowId = DatabaseFactory.getStatInterfaceDatabase(RumbleApplication.getContext())
                .getInterfaceDBIDFromMac(mac);
        if(rowId < 0) {
            rowId = DatabaseFactory.getStatInterfaceDatabase(RumbleApplication.getContext())
                    .insertInterface(mac,event.neighbour.getLinkLayerIdentifier().equals(BluetoothLinkLayerAdapter.LinkLayerIdentifier));
        }
        // then the statistic
        if(event.channel.connection_end_time - event.channel.connection_start_time == 0)
            return;
        DatabaseFactory.getStatChannelDatabase(RumbleApplication.getContext())
                .insertChannelStat(rowId, event.channel.connection_start_time, event.channel.connection_end_time,
                        event.channel.getProtocolIdentifier(), event.channel.bytes_received, event.channel.in_transmission_time,
                        event.channel.bytes_sent, event.channel.out_transmission_time, event.channel.status_received,
                        event.channel.status_sent);
    }

    public void onEventAsync(ConnectedToAP event) {
        if(SharedPreferenceUtil.isTimeToSync()) {
            if(!NetUtil.isURLReachable("http://stats.disruptedsystems.org/"))
                return;
        }
    }

}
