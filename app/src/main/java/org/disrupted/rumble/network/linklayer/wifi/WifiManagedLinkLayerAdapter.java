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
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import org.disrupted.rumble.network.NeighbourDevice;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.ThreadPoolCoordinator;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;
import org.disrupted.rumble.network.protocols.Rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.Rumble.RumbleWifiConfiguration;

import java.net.InetAddress;
import java.util.List;

/**
 * @author Marlinski
 */
public class WifiManagedLinkLayerAdapter extends LinkLayerAdapter {

    private static final String TAG = "WifiManagedLinkLayerAdapter";

    public static final String LinkLayerIdentifier = "WIFI-Managed";

    String macAddress;
    WifiManager wifiMan;
    WifiInfo wifiInf;

    /*
     * only compatible with API 16 !
     */
    private NsdServiceInfo serviceInfo;
    private NsdManager     mNsdManager;
    private NsdManager.RegistrationListener mRegistrationListener;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdManager.ResolveListener mResolveListener;
    private String mServiceName;
    private boolean registered;


    public WifiManagedLinkLayerAdapter(NetworkCoordinator networkCoordinator) {
        super(networkCoordinator);
        macAddress = null;
        wifiMan = null;
        wifiInf = null;
        registered = false;
    }

    @Override
    public String getID() {
        return LinkLayerIdentifier;
    }

    @Override
    public void onLinkStart() {
        wifiMan = (WifiManager) networkCoordinator.getSystemService(Context.WIFI_SERVICE);
        if(!wifiMan.isWifiEnabled())
            return;

        wifiInf = wifiMan.getConnectionInfo();
        macAddress = wifiInf.getMacAddress();

        UDPServer udpRumbleServer = new UDPServer(
                macAddress,
                RumbleWifiConfiguration.SERVER_PORT,
                LinkLayerIdentifier,
                new RumbleProtocol(),
                null
        );
        ThreadPoolCoordinator.getInstance().addConnection(udpRumbleServer);

        registerService();
        registered = true;
    }

    @Override
    public void onLinkStop() {
        ThreadPoolCoordinator.getInstance().killThreadType(LinkLayerIdentifier);
        networkCoordinator.removeNeighborsType(LinkLayerIdentifier);
        if(!registered)
            return;
        if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
            mNsdManager.unregisterService(mRegistrationListener);
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
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
    public List<NeighbourDevice> getNeighborhood() {
        return null;
    }

    @Override
    public void connectTo(NeighbourDevice neighbourDevice, boolean force) {

    }




    /*
     * The following code only deals with DNS-SD registration / discovery
     * and requires API level 16 to works.
     *
     * Since service discovery is a heavy tasks (and thus drain the battery),
     * we may consider using our own strategy instead.
     * todo: do testings
     */

    public void registerService() {
        if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
            initializeRegistrationListener();

            serviceInfo  = new NsdServiceInfo();
            serviceInfo.setServiceName(RumbleWifiConfiguration.NSD_SERVICE_NAME);
            serviceInfo.setServiceType(RumbleWifiConfiguration.NSD_SERVICE_TYPE);
            serviceInfo.setPort(RumbleWifiConfiguration.SERVER_PORT);

            mNsdManager = (NsdManager) networkCoordinator.getSystemService(Context.NSD_SERVICE);
            mNsdManager.registerService(
                    serviceInfo,
                    NsdManager.PROTOCOL_DNS_SD,
                    mRegistrationListener);

            initializeDiscoveryListener();
            initializeResolveListener();
            mNsdManager.discoverServices(
                    RumbleWifiConfiguration.NSD_SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    mDiscoveryListener);

        }
    }

    public void initializeRegistrationListener() {
        if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
            mRegistrationListener = new NsdManager.RegistrationListener() {

                @Override
                public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
                        mServiceName = NsdServiceInfo.getServiceName();
                    }
                }

                @Override
                public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                }

                @Override
                public void onServiceUnregistered(NsdServiceInfo arg0) {
                    Log.d(TAG, "[-] Service unregistered");
                }

                @Override
                public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                }
            };
        }
    }

    public void initializeDiscoveryListener() {
        if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
            mDiscoveryListener = new NsdManager.DiscoveryListener() {

                @Override
                public void onDiscoveryStarted(String regType) {
                    Log.d(TAG, "Service discovery started");
                }

                @Override
                public void onServiceFound(NsdServiceInfo service) {
                    if( Build.VERSION.SDK_INT  > Build.VERSION_CODES.JELLY_BEAN) {
                        Log.d(TAG, "[+] Service discovery success" + service);
                        if (!service.getServiceType().equals(RumbleWifiConfiguration.NSD_SERVICE_TYPE)) {
                            Log.d(TAG, "[-] unknown Service Type: " + service.getServiceType());
                        } else if (service.getServiceName().equals(mServiceName)) {
                            Log.d(TAG, "[-] Same machine: " + mServiceName);
                        } else if (service.getServiceName().contains(RumbleWifiConfiguration.NSD_SERVICE_NAME)) {
                            Log.d(TAG, "[+] found rumble neighbour: " + mServiceName);
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
                        Log.e(TAG, "[+] Resolve Succeeded. " + serviceInfo);

                        if (serviceInfo.getServiceName().equals(mServiceName)) {
                            Log.d(TAG, "Same IP.");
                            return;
                        }

                        int port = serviceInfo.getPort();
                        InetAddress host = serviceInfo.getHost();
                    }
                }
            };
        }
    }

}
