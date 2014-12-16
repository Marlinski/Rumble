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
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.linklayer.wifi.events.NewServiceDetected;
import org.disrupted.rumble.network.protocols.rumble.RumbleWifiConfiguration;

import java.net.InetAddress;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class WifiManagerServiceDiscovery {

    private static final String TAG = "WifiManagerServiceDiscovery";

    private NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdManager.ResolveListener mResolveListener;

    public WifiManagerServiceDiscovery() {
    }

    public void start() {
        mNsdManager = (NsdManager) RumbleApplication.getContext().getSystemService(Context.NSD_SERVICE);

        initializeDiscoveryListener();
        initializeResolveListener();

        if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
            mNsdManager.discoverServices(
                    RumbleWifiConfiguration.NSD_SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    mDiscoveryListener);
        }

    }

    public void stop() {
        if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
    }


    public void initializeDiscoveryListener() {
        if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
            mDiscoveryListener = new NsdManager.DiscoveryListener() {

                @Override
                public void onDiscoveryStarted(String regType) {
                    Log.d(TAG, "[+] Service discovery started");
                }

                @Override
                public void onServiceFound(NsdServiceInfo service) {
                    if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
                        if (!service.getServiceType().equals(RumbleWifiConfiguration.NSD_SERVICE_TYPE)) {
                            Log.d(TAG, "[-] unknown Service Type: " + service.getServiceType());
                        } else if (service.getServiceName().equals(RumbleWifiConfiguration.NSD_SERVICE_NAME)) {
                            Log.d(TAG, "[-] Same machine: ");
                        } else if (service.getServiceName().contains(RumbleWifiConfiguration.NSD_SERVICE_NAME)) {
                            Log.d(TAG, "[+] found rumble neighbour: ");
                            mNsdManager.resolveService(service, mResolveListener);
                        }
                    }
                }

                @Override
                public void onServiceLost(NsdServiceInfo service) {
                    Log.e(TAG, "[-] service lost" + service);
                }

                @Override
                public void onDiscoveryStopped(String serviceType) {
                    Log.d(TAG, "[-] Discovery stopped: " + serviceType);
                }

                @Override
                public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                    if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
                        Log.e(TAG, "[!] Start Discovery failed: " + errorCode);
                        mNsdManager.stopServiceDiscovery(this);
                    }
                }

                @Override
                public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                    if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
                        Log.e(TAG, "[!] Stop Discovery failed: Error code:" + errorCode);
                        mNsdManager.stopServiceDiscovery(this);
                    }
                }
            };
        }
    }

    public void initializeResolveListener() {
        if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
            mResolveListener = new NsdManager.ResolveListener() {

                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.e(TAG, "[!] Resolve failed" + errorCode);
                }

                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {
                    if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
                        EventBus.getDefault().post(new NewServiceDetected(serviceInfo));
                    }
                }
            };
        }
    }



}
