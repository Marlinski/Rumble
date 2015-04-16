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

package org.disrupted.rumble.network.events;

import org.disrupted.rumble.database.objects.PushStatus;

import java.util.List;

/**
 * This event holds every information known on a transmission that happened successfully. These
 * information includes:
 *
 * - The sent status (as it was sent)
 * - The receiver(s) (or an estimation of it in the case of Multicast IP)
 * - The protocol used to transmit this status (rumble, firechat)
 * - The link layer used (bluetooth, wifi)
 * - The size of the data transmitted (bytes)
 * - The duration of the transmission (ms)
 *
 * These information will be used by different component to update some informations :
 *  - The CacheManager to update its list and the neighbour's queue as well
 *  - The LinkLayerAdapte to update its internal metric that is used by getBestInterface
 *  - The FragmentStatusList to provide a visual feedback to the user
 *
 * @author Marlinski
 */
public class PushStatusSentEvent extends NetworkEvent {

    public PushStatus status;
    public List<String> recipients;
    public String protocolID;
    public String linkLayerIdentifier;
    public long size;
    public long duration;

    public PushStatusSentEvent(PushStatus status, List<String> recipients, String protocolID, String linkLayerIdentifier, long size, long duration) {
        this.status = status;
        this.recipients = recipients;
        this.protocolID = protocolID;
        this.linkLayerIdentifier = linkLayerIdentifier;
        this.size = size;
        this.duration = duration;
    }

}
