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

package org.disrupted.rumble.network;

import org.disrupted.rumble.network.linklayer.LinkLayerConnection;

/**
 * A worker is a thread, running into a WorkerPool. Because of resource limitation, we maintain
 * a fixed number of worker in the pool. When a worker is added to the pool, the pool will only
 * start the thread (by calling startWorker) if there is enough resources.
 *
 * A worker is also bounded to a specific protocol and a specifil linklayer, in other word,
 * he is the glue between one (or multiple) link-layer neighbour (Bluetooth, IPv4, IPv6) and
 * a protocol (like firechat or rumble).
 *
 * @author Lucien Loiseau
 */
public interface Worker {

    public String getWorkerIdentifier();

    public String getProtocolIdentifier();

    public String getLinkLayerIdentifier();

    public void cancelWorker();

    public void startWorker();

    public void stopWorker();

    public boolean isWorking();

}
