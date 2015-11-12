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
import org.disrupted.rumble.util.HashUtil;

import java.security.NoSuchAlgorithmException;

import javax.crypto.SecretKey;

/**
 * @author Marlinski
 */
public class Group {

    public static final String TAG = "Group";

    public static final String DEFAULT_PUBLIC_GROUP = "rumble.public";
    public static final int GROUP_NAME_MAX_SIZE = 50;
    public static final int GROUP_DESC_MAX_SIZE = 140;
    public static final int GROUP_GID_RAW_SIZE  = 8;
    public static final int GROUP_KEY_AES_SIZE  = AESUtil.KEYSIZE/8;

    private String    name;
    private String    gid;
    private SecretKey key;
    private String    desc;
    private boolean   isPrivate;

    public static Group getDefaultGroup() {
        try {
            Group defaultGroup =  createNewGroup(DEFAULT_PUBLIC_GROUP, false);
            defaultGroup.setDesc("open and public group for delay tolerant communication with Rumble.");
            return defaultGroup;
        } catch ( NoSuchAlgorithmException impossible ) {
            return null;
        }
    }

    public static Group createNewGroup(String name, boolean isPrivate) throws NoSuchAlgorithmException{
        SecretKey key = null;
        if(isPrivate)
            key = AESUtil.generateRandomAESKey();
        String gid = HashUtil.computeGroupUid(name, isPrivate);
        return new Group(name, gid, key);
    }

    public Group(String name, String gid, SecretKey key) {
        this.name = name;
        this.gid  = gid;
        this.key  = key;
        this.isPrivate = (key != null);
        this.desc = "";
    }

    public final String getName() {        return name;      }
    public final String getGid() {         return gid;       }
    public final SecretKey getGroupKey() { return key;       }
    public boolean isPrivate() {         return isPrivate; }
    public final String getDesc() {        return desc;      }

    public void setDesc(String desc) {     this.desc = desc; }


    @Override
    public boolean equals(Object o) {
        if(o == null)
            return false;
        if(o instanceof Group) {
            Group group = (Group)o;
            return this.gid.equals(group.gid);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.gid.hashCode();
    }

    @Override
    public String toString() {
        return "Group Name="+name+" GID="+gid;
    }
}
