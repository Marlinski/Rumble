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

package org.disrupted.rumble.network.protocols.rumble;

/**
 * @author Marlinski
 */
public class RumbleWifiConfiguration {

    public static final String NSD_SERVICE_NAME = "Rumble";
    public static final String NSD_SERVICE_TYPE = "_rumble._udp.";

    public static final String MULTICAST_ADDRESS = "239.192.0.0";

    public static final int MULTICAST_PORT = 9715;

    public static final int SERVER_PORT = 9716;

}
