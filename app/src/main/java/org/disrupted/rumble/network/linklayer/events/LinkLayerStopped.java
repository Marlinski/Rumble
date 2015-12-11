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

package org.disrupted.rumble.network.linklayer.events;

import org.disrupted.rumble.network.events.NetworkEvent;

/**
 * @author Lucien Loiseau
 */
public class LinkLayerStopped extends NetworkEvent {

    public String linkLayerIdentifier;
    public long   started_time_nano;
    public long   stopped_time_nano;


    public LinkLayerStopped(String linkLayerIdentifier, long started_time_nano, long stopped_time_nano) {
        this.linkLayerIdentifier = linkLayerIdentifier;
        this.started_time_nano = started_time_nano;
        this.stopped_time_nano = stopped_time_nano;
    }

    @Override
    public String shortDescription() {
        return linkLayerIdentifier;
    }
}
