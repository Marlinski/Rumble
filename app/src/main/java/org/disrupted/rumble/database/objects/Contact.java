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

import org.disrupted.rumble.util.HashUtil;

/**
 * @author Marlinski
 */
public class Contact {

    public String uid;
    public String name;
    public String avatar;
    public boolean local;

    public static Contact createLocalContact(String name) {
        String uid = HashUtil.computeContactUid(name,System.currentTimeMillis());
        return new Contact(name, uid, true);
    }

    public Contact(String name, String uid, boolean local) {
        this.name = name;
        this.uid = uid;
        this.local = local;
    }

    public String getUid()    { return uid;}
    public String getName()   { return name;}
    public String getAvatar() { return avatar;}
    public boolean isLocal()  { return local;}

    public void setAvatar(String avatar) { this.avatar = avatar; }

}
