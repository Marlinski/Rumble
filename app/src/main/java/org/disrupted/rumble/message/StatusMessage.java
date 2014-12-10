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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Marlinski
 */
public class StatusMessage extends Message {

    private static final String TAG  = "StatusMessage";
    public  static final String TYPE = "STATUS";

    protected String      author;
    protected String      status;
    protected Set<String> hashtagSet;
    protected String      attachedFile;
    protected long        fileSize;
    protected String      timeOfCreation;
    protected String      timeOfArrival;
    protected Integer     hopCount;
    protected Integer     score;
    protected Integer     ttl;
    protected Set<String> forwarderList;

    public StatusMessage(String post, String author) {
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
        timeOfCreation = String.valueOf(System.currentTimeMillis() / 1000L);
        timeOfArrival  = String.valueOf(System.currentTimeMillis() / 1000L);
        hopCount       = 0;
        forwarderList  = new HashSet<String>();
        score          = 0;
        ttl            = 0;
    }

    public void setFileName(String filename){     this.attachedFile   = filename; }
    public void setFileSize(long size) {          this.fileSize       = size;     }
    public void setTimeOfCreation(String toc){    this.timeOfCreation = toc;      }
    public void setTimeOfArrival(String toa){     this.timeOfArrival  = toa;      }
    public void setHopCount(Integer hopcount){    this.hopCount       = hopcount; }
    public void setScore(Integer score){          this.score          = score;    }
    public void setTTL(Integer ttl){              this.ttl            = ttl;      }
    public void addHashtag(String tag){           this.hashtagSet.add(tag);       }
    public void setForwarderList(Set<String> fl){
        if(forwarderList.size() > 0)
            forwarderList.clear();
        this.forwarderList  = fl;
    }
    public void addForwarder(String macAddress) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(macAddress.getBytes());
            byte[] digest = md.digest();
            macAddress = new String(digest);
        }
        catch (NoSuchAlgorithmException ignore) {}
        forwarderList.add(macAddress);
    }

    public String  getAuthor(){             return this.author; }
    public String  getPost(){               return this.status; }
    public Set<String> getHashtagSet(){     return this.hashtagSet; }
    public String  getTimeOfCreation(){     return this.timeOfCreation; }
    public String  getTimeOfArrival(){      return this.timeOfArrival; }
    public Integer getHopCount(){           return this.hopCount; }
    public Set<String> getForwarderList(){  return this.forwarderList; }
    public Integer getScore(){              return this.score;}
    public Integer getTTL(){                return this.ttl;}
    public String  getFileName(){           return this.attachedFile; }
    public long    getFileSize(){           return this.fileSize; }
    public long    getFileID(){             return 0; }

    public boolean hasAttachedFile() {
        return (attachedFile != "");
    }
    public boolean isForwarder(String macAddress) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(macAddress.getBytes());
            byte[] digest = md.digest();
            macAddress = new String(digest);
        }
        catch (NoSuchAlgorithmException ignore) {}
        return forwarderList.contains(macAddress);
    }

    public String toString() {
        String s = new String();
        s += "Author: "+this.author+"\n";
        s += "Status:" +this.status+"\n";
        s += "Time:" +this.timeOfCreation+"\n";
        return s;
    }
}



