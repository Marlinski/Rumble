/*
 * Copyright (C) 2014 Lucien Loiseau
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

package org.disrupted.rumble.network.linklayer.wifi;

import android.net.wifi.ScanResult;

import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.util.NetUtil;

/**
 * @author Lucien Loiseau
 */
public class WifiAPNeighbour implements LinkLayerNeighbour{

    public ScanResult result;

    public WifiAPNeighbour(ScanResult result) {
        this.result = result;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public String getLinkLayerAddress() {
        return result.BSSID;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return WifiLinkLayerAdapter.LinkLayerIdentifier;
    }

    @Override
    public String getLinkLayerMacAddress() throws NetUtil.NoMacAddressException {
        return result.BSSID;
    }
}
