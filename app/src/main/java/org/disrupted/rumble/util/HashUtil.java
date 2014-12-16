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

package org.disrupted.rumble.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Marlinski
 */
public class HashUtil {

    public static final String computeHash(String linkLayerAddress, String protocol) {
        String hash = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(linkLayerAddress.getBytes());
            md.update(protocol.getBytes());
            byte[] digest = md.digest();
            hash = new String(digest);
        }
        catch (NoSuchAlgorithmException ignore) {}
        return hash;
    }
}
