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

package org.disrupted.rumble.network.protocols.firechat;

import java.util.UUID;

/**
 * @author Marlinski
 */
public class FirechatBTConfiguration {

    /* Bluetooth Identifier */
    //public static final UUID FIRECHAT_BT_UUID_128 = UUID.fromString("249916a5-4173-46e9-9320-36a8c1e8c487");
    public static final UUID FIRECHAT_BT_UUID_128 = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    public static final String FIRECHAT_BT_STR    = "FireChat";

    public static boolean isBTUUIDFirechat(UUID uuid){
        if(uuid == null) return false;
        return (FirechatBTConfiguration.FIRECHAT_BT_UUID_128.compareTo(uuid) == 0);
    }
    public static boolean isBTUUIDFirechat(String uuid){
        if(uuid == null) return false;
        return (FirechatBTConfiguration.FIRECHAT_BT_UUID_128.toString().equals(uuid));
    }

    public static boolean isBTStrFirechat(String str){
        if(str == null) return false;
        return FIRECHAT_BT_STR.equals(str);
    }


}
