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

import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.SecretKey;

/**
 * @author Marlinski
 */
public class HashUtil {

    public static final String computeForwarderHash(String linkLayerAddress, String protocol) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(linkLayerAddress.getBytes());
            md.update(protocol.getBytes());
            return Base64.encodeToString(md.digest(), 0, 16, Base64.NO_WRAP);
        }
        catch (NoSuchAlgorithmException ignore) {
            return null;
        }
    }

    public static final String computeStatusUUID(String author, String post, long timeOfCreation) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(author.getBytes());
            md.update(post.getBytes());
            md.update(ByteBuffer.allocate(8).putLong(timeOfCreation).array());
            return Base64.encodeToString(md.digest(),0,16,Base64.NO_WRAP);
        }
        catch (NoSuchAlgorithmException ignore) {
            return null;
        }
    }

    public static final String computeContactUid(String name, long time) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(name.getBytes());
            md.update(ByteBuffer.allocate(8).putLong(time).array());
            return Base64.encodeToString(md.digest(),0,8,Base64.NO_WRAP);
        }
        catch (NoSuchAlgorithmException ignore) {
            return null;
        }
    }

    public static final String computeGroupUid(String name, boolean isPrivate) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(name.getBytes());
            if(isPrivate)
                md.update(ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array());
            return Base64.encodeToString(md.digest(),0,8,Base64.NO_WRAP);
        }
        catch (NoSuchAlgorithmException ignore) {
            return null;
        }
    }
}
