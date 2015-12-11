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

package org.disrupted.rumble.util;

import android.util.Base64;

import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.database.objects.PushStatus;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * @author Lucien Loiseau
 */
public class HashUtil {

    public static final int expectedEncodedSize(int size) {
        return (int)(4*Math.ceil((double) size/3));
    }

    public static final String computeInterfaceID(String linkLayerAddress,String protocol) {
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

    public static final String computeStatusUUID(String author_uid, String group_gid, String post, long timeOfCreation) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(author_uid.getBytes());
            md.update(group_gid.getBytes());
            md.update(post.getBytes());
            md.update(ByteBuffer.allocate(8).putLong(timeOfCreation).array());
            return Base64.encodeToString(md.digest(),0, PushStatus.STATUS_ID_RAW_SIZE,Base64.NO_WRAP);
        }
        catch (NoSuchAlgorithmException ignore) {
            return null;
        }
    }

    public static final String computeChatMessageUUID(String author_uid, String message, long timeOfCreation) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(author_uid.getBytes());
            md.update(message.getBytes());
            md.update(ByteBuffer.allocate(8).putLong(timeOfCreation).array());
            return Base64.encodeToString(md.digest(),0,PushStatus.STATUS_ID_RAW_SIZE,Base64.NO_WRAP);
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
            return Base64.encodeToString(md.digest(),0, Contact.CONTACT_UID_RAW_SIZE,Base64.NO_WRAP);
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
            return Base64.encodeToString(md.digest(),0, Group.GROUP_GID_RAW_SIZE,Base64.NO_WRAP);
        }
        catch (NoSuchAlgorithmException ignore) {
            return null;
        }
    }

    public static boolean isBase64Encoded(String str)
    {
        try
        {
            byte[] data = Base64.decode(str, Base64.NO_WRAP);
            return true;
        } catch(Exception e)
        {
            return false;
        }
    }

    public static String generateRandomString(int size) {
        char[] chars = "ABCDEF+=012!GHIJKL@345MNOPQR678STUVWXYZ9/".toCharArray();
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < size; i++) {
            char c1 = chars[random.nextInt(chars.length)];
            sb.append(c1);
        }
        return sb.toString();
    }
}
