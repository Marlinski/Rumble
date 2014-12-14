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

import org.disrupted.rumble.message.StatusMessage;

import java.util.List;

/**
 * This event holds every information known on a transmission that happened successfully. These
 * information includes:
 *
 * - which status was sent
 * - who received it (or an estimation of it in the case of Multicast IP)
 * - which protocol was used to transmit this status (rumble, firechat)
 * - which link layer was used (bluetooth, wifi)
 * - mean throughput (bytes/ms)
 *
 * These information will be used by different component to update some informations :
 *  - The MessageQueue to update its list and the neighbour's queue as well
 *  - The LinkLayerAdapte to update its internal metric that is used by getBestInterface
 *  - The FragmentStatusList to provide a visual feedback to the user
 *
 * @author Marlinski
 */
public class StatusSentEvent extends NetworkEvent {

    public StatusMessage status;
    public List<String> recipients;
    public String protocolID;
    public String linkLayerIdentifier;
    public long meanThroughput;

    public StatusSentEvent(StatusMessage status, List<String> recipients, String protocolID, String linkLayerIdentifier, long meanThroughput) {
        this.status = status;
        this.recipients = recipients;
        this.protocolID = protocolID;
        this.linkLayerIdentifier = linkLayerIdentifier;
        this.meanThroughput = meanThroughput;
    }

}
