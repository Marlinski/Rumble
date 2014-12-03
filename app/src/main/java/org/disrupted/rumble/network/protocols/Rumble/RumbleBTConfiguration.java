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

package org.disrupted.rumble.network.protocols.Rumble;

import java.util.UUID;

/**
 * @author Marlinski
 */
public class RumbleBTConfiguration {

    /* Bluetooth Identifier */
    public static final UUID   RUMBLE_BT_UUID_128 = UUID.fromString("db64c0d0-4dff-11e4-916c-0800200c9a66");
    public static final String RUMBLE_BT_STR        = "org.disrupted.rumble";


    public static boolean isBTUUIDRumble(UUID uuid){
        return ( (RumbleBTConfiguration.RUMBLE_BT_UUID_128.compareTo(uuid) == 0) );
    }

    public static boolean isBTUUIDRumble(String uuid){
        return (   (RumbleBTConfiguration.RUMBLE_BT_UUID_128.toString().equals(uuid)) );
    }

    public static boolean isBTUUIDStr(String str){
        return RUMBLE_BT_STR.equals(str);
    }

}
