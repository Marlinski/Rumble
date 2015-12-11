/*
 * Copyright (C) 2014 Lucien Loiseau
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
 * @author Lucien Loiseau
 */
public class Contact {

    public static final int CONTACT_NAME_MAX_SIZE = 50;
    public static final int CONTACT_UID_RAW_SIZE  = 8;

    /* core attributes */
    protected String                uid;
    protected String                name;
    protected String                avatar;
    protected boolean               local;
    protected boolean               friend;
    protected long                  last_met;
    protected int                   nb_status_sent;
    protected int                   nb_status_received;

    /* dynamic attributes */
    protected Set<String>           joinedGroupIDs;
    protected Map<String, Integer>  hashtagInterests;
    protected Set<Interface>        interfaces;

    public static final int MAX_INTEREST_TAG_VALUE = 255;
    public static final int MIN_INTEREST_TAG_VALUE = 0;

    /* used to state wether we should consider or not the following attributes for update */
    public static final int FLAG_GROUP_LIST   = 0x01;
    public static final int FLAG_TAG_INTEREST = 0x02;


    public static Contact createLocalContact(String name) {
        String uid = HashUtil.computeContactUid(name,System.currentTimeMillis());
        return new Contact(name, uid, true);
    }

    public static Contact getLocalContact() {
        return DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getLocalContact();
    }

    public Contact(Contact contact) {
        this.uid                = contact.uid;
        this.name               = contact.name;
        this.avatar             = contact.avatar;
        this.local              = contact.local;
        this.friend             = contact.friend;
        this.last_met           = contact.last_met;
        this.nb_status_sent     = contact.nb_status_sent;
        this.nb_status_received = contact.nb_status_received;
        this.joinedGroupIDs     = new HashSet<String>(contact.joinedGroupIDs);
        this.hashtagInterests   = new HashMap<String, Integer>(contact.hashtagInterests);
        this.interfaces         = new HashSet<Interface>(contact.interfaces);
    }

    public Contact(String name, String uid, boolean local) {
        this.name               = name;
        this.uid                = uid;
        this.local              = local;
        joinedGroupIDs          = new HashSet<String>();
        hashtagInterests        = new HashMap<String, Integer>();
        interfaces              = new HashSet<Interface>();
        last_met                = 0;
        this.nb_status_received = 0;
        this.nb_status_sent     = 0;
    }

    public static boolean checkUsername(String username) {
        if(username.length() > CONTACT_NAME_MAX_SIZE)
            return false;
        if(username.contains("\n"))
            return false;
        return true;
    }


    public String getUid()    { return uid;}
    public String getName()   { return name;}
    public String getAvatar() { return avatar;}
    public boolean isLocal()  { return local;}
    public boolean isFriend() { return friend; }
    public long    lastMet()   { return last_met; }
    public int     nbStatusSent() { return nb_status_sent; }
    public int     nbStatusReceived() { return nb_status_received; }
    public Set<String> getJoinedGroupIDs() {             return joinedGroupIDs;   }
    public Map<String, Integer> getHashtagInterests() {  return hashtagInterests; }
    public Set<Interface> getInterfaces() {              return interfaces;       }

    public void lastMet(long date)   { this.last_met = date; }
    public void setStatusSent(int nb) {this.nb_status_sent = nb;}
    public void setStatusReceived(int nb) {this.nb_status_received = nb;}
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public void addGroup(String groupID) {
        joinedGroupIDs.add(groupID);
    }
    public void addTagInterest(String hashtag, int levelOfInterest) {
        hashtagInterests.put(hashtag, levelOfInterest);
    }
    public void addInterface(Interface interfaceID) {
        interfaces.add(interfaceID);
    }
    public void setHashtagInterests(Map<String, Integer> hashtagInterests) {
        if(this.hashtagInterests.size() > 0)
            this.hashtagInterests.clear();
        if(hashtagInterests != null)
            this.hashtagInterests = new HashMap<String, Integer>(hashtagInterests);
        else
            this.hashtagInterests = new HashMap<String, Integer>();
    }
    public void setJoinedGroupIDs(Set<String> joinedGroupIDs) {
        if(this.joinedGroupIDs.size() > 0)
            this.joinedGroupIDs.clear();
        if(joinedGroupIDs != null)
            this.joinedGroupIDs = new HashSet<String>(joinedGroupIDs);
        else
            this.joinedGroupIDs = new HashSet<String>();
    }
    public void setInterfaces(Set<Interface> interfaces) {
        if(this.interfaces.size() > 0)
            this.interfaces.clear();
        if(interfaces != null)
            this.interfaces = interfaces;
        else
            this.interfaces = new HashSet<Interface>();
    }

    @Override
    public boolean equals(Object o) {
        if(o == null)
            return false;
        if(o instanceof Contact) {
            Contact contact = (Contact)o;
            return this.uid.equals(contact.uid);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.uid.hashCode();
    }

    @Override
    public String toString() {
        String string =  "uid="+uid+" name="+name+" ("+(local ? "local" : "alien")+")  \n";
        string += "Groups=[";
        if(joinedGroupIDs != null) {
            for (String group : joinedGroupIDs) {
                string += group+", ";
            }
        }
        string += "] \nTag=[";
        if(hashtagInterests != null) {
            for (Map.Entry<String, Integer> entry : hashtagInterests.entrySet()) {
                string += entry.getKey() + ":" + entry.getValue() + " ";
            }
        }
        string += "]";
        return string;
    }

}
