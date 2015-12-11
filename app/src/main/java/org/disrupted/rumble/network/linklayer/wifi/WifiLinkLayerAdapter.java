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

package org.disrupted.rumble.network.linklayer.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.linklayer.events.AccessPointDisabled;
import org.disrupted.rumble.network.linklayer.events.AccessPointEnabled;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStarted;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStopped;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.events.WifiModeChanged;

import java.util.concurrent.locks.ReentrantLock;

import de.greenrobot.event.EventBus;


/**
 * @author Lucien Loiseau
 */
public class WifiLinkLayerAdapter extends HandlerThread implements LinkLayerAdapter {

    private static final String TAG = "WifiLinkLayerAdapter";

    public static final String LinkLayerIdentifier = "WIFI";

    private static final ReentrantLock lock = new ReentrantLock();
    private String macAddress;
    private WifiManager wifiMan;
    private WifiInfo wifiInf;

    private long started_time_nano;
    private boolean register;
    private boolean activated;

    public enum WIFIMODE {
        WIFIMANAGED, WIFIAP
    }
    public WIFIMODE mode;

    public WifiLinkLayerAdapter() {
        super(TAG);
        macAddress = null;
        wifiMan    = null;
        wifiInf    = null;
        register   = false;
        activated  = false;
        mode       = WifiUtil.isWiFiApEnabled() ? WIFIMODE.WIFIAP : WIFIMODE.WIFIMANAGED;
        super.start();
    }

    @Override
    protected void finalize() throws Throwable {
        super.quit();
        super.finalize();
    }

    @Override
    public boolean isActivated() {
        return register;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return LinkLayerIdentifier;
    }

    @Override
    public void linkStart() {
        if(register)
            return;
        register = true;
        Log.d(TAG, "[+] Starting Wifi");

        wifiMan = (WifiManager) RumbleApplication.getContext().getSystemService(Context.WIFI_SERVICE);
        wifiInf = wifiMan.getConnectionInfo();
        macAddress = wifiInf.getMacAddress();

        if (WifiUtil.isWiFiApEnabled() || WifiUtil.isEnabled()) {
            linkConnected();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        Handler handler = new Handler(getLooper());
        RumbleApplication.getContext().registerReceiver(mReceiver, filter, null, handler);
        if(!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this);
    }

    @Override
    public void linkStop() {
        if(!register)
            return;
        register = false;

        Log.d(TAG, "[-] Stopping Wifi");
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);

        RumbleApplication.getContext().unregisterReceiver(mReceiver);
        linkDisconnected();

        wifiInf = null;
        wifiMan = null;
    }

    private void linkConnected() {
        if(activated)
            return;
        activated = true;
        Log.d(TAG, "[+] Wifi Activated");
        started_time_nano = System.nanoTime();
        EventBus.getDefault().post(new LinkLayerStarted(getLinkLayerIdentifier()));
    }

    private void linkDisconnected() {
        if(!activated)
            return;
        activated = false;

        Log.d(TAG, "[-] Wifi De-activated");
        EventBus.getDefault().post(new LinkLayerStopped(getLinkLayerIdentifier(),
                started_time_nano, System.nanoTime()));
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                NetworkInfo.State state = networkInfo.getState();
                //Log.d(TAG, intent.toString() + "-" + networkInfo.toString());

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
                         if (!activated)
                             return;

                         /*
                          * When switching from managed mode to AP, we first disconnect from the
                          * access point we were already connected (if any) and then we create
                          * the tethering access point. However Android sends the DISCONNECTED
                          * intent too late, after the access point creation. So if the mode is
                          * AP, we ignore this intent, instead, we already called linkDisconnected
                          * in onEventAsync(WifiModeChanged event)
                          */
                         if(mode != WIFIMODE.WIFIAP) {
                             Log.d(TAG, "[-] disconnected from a wifi access point");
                             linkDisconnected();
                         }
                     }
                }
            }
        }
    };

    public void onEvent(AccessPointEnabled event) {
        if(activated)
            return;
        linkConnected();
    }

    public void onEvent(AccessPointDisabled event) {
        if(!activated)
            return;
        linkDisconnected();
    }

    public void onEventAsync(WifiModeChanged event) {
        lock.lock();
        try {
            this.mode = event.mode;
            if(event.mode.equals(WIFIMODE.WIFIAP)) {
                if (WifiUtil.isEnabled()) {
                    WifiUtil.disableWifi();
                    linkDisconnected();
                }
                WifiUtil.enableAP();
            } else {
                if(WifiUtil.isWiFiApEnabled()) {
                    WifiUtil.disableAP();
                }
                if(!WifiUtil.isEnabled())
                    WifiUtil.enableWifi();
            }
        } finally {
            lock.unlock();
        }
    }
}
