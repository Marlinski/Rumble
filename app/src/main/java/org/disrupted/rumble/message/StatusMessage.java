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

package org.disrupted.rumble.message;


import android.util.Log;
import android.webkit.MimeTypeMap;

import org.disrupted.rumble.util.HashUtil;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Marlinski
 */
public class StatusMessage extends Message{

    private static final String TAG  = "StatusMessage";
    public  static final String TYPE = "STATUS";

    protected long        dbid;
    protected String      uuid;
    protected String      author;
    protected String      status;
    protected Set<String> hashtagSet;
    protected String      attachedFile;
    protected long        fileSize; // move it to file database
    protected long        timeOfCreation;
    protected long        timeOfArrival;
    protected long        hopCount;
    protected long        ttl;
    protected long        like;
    protected long        replication;
    protected boolean     read;
    protected Set<String> forwarderList;

    public StatusMessage(String post, String author, long timeOfCreation) {
        this.messageType = TYPE;

        this.status   = post;
        this.author = author;
        hashtagSet  = new HashSet<String>();
        Pattern hashtagPattern = Pattern.compile("#(\\w+|\\W+)");
        Matcher hashtagMatcher = hashtagPattern.matcher(post);
        hashtagSet  = new HashSet<String>();
        while (hashtagMatcher.find()) {
            hashtagSet.add(hashtagMatcher.group(0));
        }

        attachedFile   = "";
        fileSize       = 0;
        this.timeOfCreation = timeOfCreation;
        timeOfArrival  = (System.currentTimeMillis() / 1000L);
        hopCount       = 0;
        forwarderList  = new HashSet<String>();
        ttl            = 0;
        like           = 0;
        replication    = 0;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(author.getBytes());
            md.update(post.getBytes());
            md.update(ByteBuffer.allocate(8).putLong(timeOfCreation).array());
            byte[] digest = md.digest();
            uuid = new String(digest);
        }
        catch (NoSuchAlgorithmException ignore) {}
    }

    public long    getdbId() {              return this.dbid; }
    public String  getUuid() {              return this.uuid; }
    public String  getAuthor(){             return this.author; }
    public String  getPost(){               return this.status; }
    public Set<String> getHashtagSet(){     return this.hashtagSet; }
    public long  getTimeOfCreation(){       return this.timeOfCreation; }
    public long  getTimeOfArrival(){        return this.timeOfArrival; }
    public long getHopCount(){              return this.hopCount; }
    public Set<String> getForwarderList(){  return this.forwarderList; }
    public long getTTL(){                   return this.ttl;}
    public String  getFileName(){           return this.attachedFile; }
    public long    getFileSize(){           return this.fileSize; }
    public long    getFileID(){             return 0; }
    public long    getLike(){               return like; }
    public long    getReplication(){        return replication; }

    public void setdbId(long dbid) {              this.dbid           = dbid;     }
    public void setUuid(String uuid) {            this.uuid           = uuid;     }
    public void setFileName(String filename){     this.attachedFile   = filename; }
    public void setFileSize(long size) {          this.fileSize       = size;     }
    public void setTimeOfCreation(long toc){      this.timeOfCreation = toc;      }
    public void setTimeOfArrival(long toa){       this.timeOfArrival  = toa;      }
    public void setHopCount(long hopcount){       this.hopCount       = hopcount; }
    public void setLike(long like){               this.like           = like;    }
    public void setTTL(long ttl){                 this.ttl            = ttl;      }
    public void addHashtag(String tag){           this.hashtagSet.add(tag);       }
    public void setHashtagSet(Set<String> hashtagSet) {
        if(hashtagSet.size() > 0)
            hashtagSet.clear();
        this.hashtagSet = hashtagSet;
    }
    public void addReplication(long replication){ this.replication  += replication; }
    public void setRead(boolean read){            this.read = read; }
    public void setForwarderList(Set<String> fl){
        if(forwarderList.size() > 0)
            forwarderList.clear();
        this.forwarderList  = fl;
    }
    public void addForwarder(String linkLayerAddress, String protocolID) {
        forwarderList.add(HashUtil.computeHash(linkLayerAddress,protocolID));
    }


    public boolean hasBeenReadAlready(){ return read; }
    public boolean hasAttachedFile() {
        return (attachedFile != "");
    }
    public boolean isForwarder(String linkLayerAddress, String protocolID) {
        return forwarderList.contains(HashUtil.computeHash(linkLayerAddress,protocolID));
    }

    public String toString() {
        String s = new String();
        s += "Author: "+this.author+"\n";
        s += "Status:" +this.status+"\n";
        s += "Time:" +this.timeOfCreation+"\n";
        return s;
    }

}