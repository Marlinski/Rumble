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

import android.database.Cursor;
import android.os.Build;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.network.events.ChannelDisconnected;
import org.disrupted.rumble.network.events.NeighbourReachable;
import org.disrupted.rumble.network.events.NeighbourUnreachable;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStarted;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStopped;
import org.disrupted.rumble.network.linklayer.wifi.WifiLinkLayerAdapter;
import org.disrupted.rumble.util.NetUtil;
import org.disrupted.rumble.util.RumblePreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

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
                .insertReachability(rowId, event.unreachable_time_nano, false, event.unreachable_time_nano - event.reachable_time_nano);
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

    public void onEventAsync(LinkLayerStarted event) {
        if(!event.linkLayerIdentifier.equals(WifiLinkLayerAdapter.LinkLayerIdentifier))
            return;

        if(RumblePreferences.UserOkWithSharingAnonymousData(RumbleApplication.getContext())
                && RumblePreferences.isTimeToSync(RumbleApplication.getContext())) {
            if(!NetUtil.isURLReachable("http://disruptedsystems.org/"))
                return;

            try {
                // generate the JSON file
                byte[] json = generateStatJSON().toString().getBytes();

                // configure SSL
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                InputStream caInput = new BufferedInputStream(RumbleApplication.getContext()
                        .getAssets().open("certs/disruptedsystemsCA.pem"));
                Certificate ca = cf.generateCertificate(caInput);

                String keyStoreType = KeyStore.getDefaultType();
                KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                keyStore.load(null, null);
                keyStore.setCertificateEntry("ca", ca);

                String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
                tmf.init(keyStore);

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), null);

                URL url = new URL("https://data.disruptedsystems.org/post");
                HttpsURLConnection urlConnection = (HttpsURLConnection)url.openConnection();
                urlConnection.setSSLSocketFactory(sslContext.getSocketFactory());

                // then configure the header
                urlConnection.setInstanceFollowRedirects(true);
                urlConnection.setRequestMethod("POST");
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setRequestProperty("charset", "utf-8");
                urlConnection.setRequestProperty("Content-Length", Integer.toString(json.length));
                urlConnection.setUseCaches(false);

                // connect and send the JSON
                urlConnection.setConnectTimeout(10 * 1000);
                urlConnection.connect();
                urlConnection.getOutputStream().write(json);
                if (urlConnection.getResponseCode() != 200)
                    throw new IOException("request failed");

                // erase the database
                RumblePreferences.updateLastSync(RumbleApplication.getContext());
                cleanDatabase();
            } catch (Exception ex)
            {
                Log.e(TAG, "Failed to establish SSL connection to server: " + ex.toString());
            }
        }
    }

    public JSONObject generateStatJSON() throws JSONException{
        JSONObject json = new JSONObject();
        json.put("rumble_version", RumbleApplication.BUILD_VERSION);
        json.put("android_build", Integer.toString(Build.VERSION.SDK_INT));
        json.put("phone_model",Build.MODEL);
        json.put("anonymous_id",RumblePreferences.getAnonymousID(RumbleApplication.getContext()));

        JSONArray resultSet;
        resultSet = DatabaseFactory.getStatChannelDatabase(RumbleApplication.getContext())
                .getJSON();
        json.put("channels",resultSet);
        resultSet = DatabaseFactory.getStatLinkLayerDatabase(RumbleApplication.getContext())
                .getJSON();
        json.put("link-layer",resultSet);
        resultSet = DatabaseFactory.getStatReachabilityDatabase(RumbleApplication.getContext())
                .getJSON();
        json.put("reachability",resultSet);

        return json;
    }

    public void cleanDatabase() {
        DatabaseFactory.getStatChannelDatabase(RumbleApplication.getContext())
                .clean();
        DatabaseFactory.getStatLinkLayerDatabase(RumbleApplication.getContext())
                .clean();
        DatabaseFactory.getStatReachabilityDatabase(RumbleApplication.getContext())
                .clean();
    }

}
