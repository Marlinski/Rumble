/*
 * Copyright (C) 2014 Disrupted Systems
 * This file is part of Rumble.
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
 * You should have received a copy of the GNU General Public License along
 * with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.database.objects;

import org.disrupted.rumble.util.AESUtil;

import java.security.NoSuchAlgorithmException;

import javax.crypto.SecretKey;

/**
 * @author Marlinski
 */
public class Group {

    public static final String TAG = "Group";

    private String    name;
    private SecretKey key;
    private boolean   isPrivate;

    public Group(String name, boolean isPrivate) throws NoSuchAlgorithmException{
        this.name = name;
        if(isPrivate)
            key = AESUtil.generateRandomAESKey();
        else
            key = null;
        this.isPrivate = isPrivate;
    }

    public Group(String name, SecretKey key) {
        this.name = name;
        this.key = key;
        this.isPrivate = (key != null);
    }

    public final String getName() {
        return name;
    }

    public final SecretKey getGroupKey() {
        return key;
    }

    public boolean isIsprivate() {
        return isPrivate;
    }

}
