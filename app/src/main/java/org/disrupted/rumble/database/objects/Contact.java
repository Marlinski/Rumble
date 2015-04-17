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

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.util.HashUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Marlinski
 */
public class Contact {

    protected String uid;
    protected String name;
    protected String avatar;
    protected boolean local;
    protected boolean friend;

    /* used to state wether we should consider or not the following attributes for update */
    public static final int FLAG_GROUP_LIST   = 0x01;
    public static final int FLAG_TAG_INTEREST = 0x02;
    protected Set<String> joinedGroupIDs;
    protected Map<String, Integer> hashtagInterests;

    public static Contact createLocalContact(String name) {
        String uid = HashUtil.computeContactUid(name,System.currentTimeMillis());
        return new Contact(name, uid, true);
    }

    public static Contact getLocalContact() {
        return DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getLocalContact();
    }

    public Contact(String name, String uid, boolean local) {
        this.name = name;
        this.uid = uid;
        this.local = local;
        joinedGroupIDs = new HashSet<String>();
        hashtagInterests = new HashMap<String, Integer>();
    }

    public String getUid()    { return uid;}
    public String getName()   { return name;}
    public String getAvatar() { return avatar;}
    public boolean isLocal()  { return local;}
    public final Set<String> getJoinedGroupIDs() {             return joinedGroupIDs;   }
    public final Map<String, Integer> getHashtagInterests() {  return hashtagInterests; }
    public boolean isFriend() { return friend; }

    public void setAvatar(String avatar) { this.avatar = avatar; }
    public void addGroup(String groupID) {
        joinedGroupIDs.add(groupID);
    }
    public void addTagInterest(String hashtag, int levelOfInterest) {
        hashtagInterests.put(hashtag, levelOfInterest);
    }
    public void setHashtagInterests(Map<String, Integer> hashtagInterests) {
        if(this.hashtagInterests.size() > 0)
            this.hashtagInterests.clear();
        if(hashtagInterests != null)
            this.hashtagInterests = hashtagInterests;
        else
            this.hashtagInterests = new HashMap<String, Integer>();
    }
    public void setJoinedGroupIDs(Set<String> joinedGroupIDs) {
        if(this.joinedGroupIDs.size() > 0)
            this.joinedGroupIDs.clear();
        if(joinedGroupIDs != null)
            this.joinedGroupIDs = joinedGroupIDs;
        else
            this.joinedGroupIDs = new HashSet<String>();
    }

    @Override
    public String toString() {
        String string =  uid+" - "+name+" ("+(local ? "local" : "alien")+")  ";
        string += "Groups=(";
        if(joinedGroupIDs != null) {
            for (String group : joinedGroupIDs) {
                string += group;
            }
        }
        string += ") Tag=(";
        if(hashtagInterests != null) {
            for (Map.Entry<String, Integer> entry : hashtagInterests.entrySet()) {
                string += entry.getKey() + ":" + entry.getValue() + " ";
            }
        }
        string += ")";
        return string;
    }

}
