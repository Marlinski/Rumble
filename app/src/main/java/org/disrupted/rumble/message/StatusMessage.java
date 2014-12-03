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
    protected String      filePath;
    protected String      timeOfCreation;
    protected String      timeOfArrival;
    protected Integer     hopCount;
    protected String      forwarderList;
    protected Integer     score;
    protected Integer     ttl;

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
        filePath       = "";
        timeOfCreation = String.valueOf(System.currentTimeMillis() / 1000L);
        timeOfArrival  = String.valueOf(System.currentTimeMillis() / 1000L);
        hopCount       = 0;
        forwarderList  = "";
        score          = 0;
        ttl            = 0;
    }

    public void setAttachedFile(String filename){ this.attachedFile = filename; }
    public void setFilePath(String filepath){     this.filePath     = filepath; }
    public void setTimeOfCreation(String toc){    this.timeOfCreation = toc; }
    public void setTimeOfArrival(String toa){     this.timeOfArrival  = toa; }
    public void setHopCount(Integer hopcount){    this.hopCount       = hopcount; }
    public void setForwarderList(String fl){      this.forwarderList  = fl; }
    public void setScore(Integer score){          this.score          = score;}
    public void setTTL(Integer ttl){              this.ttl            = ttl;}
    public void addHashtag(String tag){           this.hashtagSet.add(tag); }

    public String  getAuthor(){         return this.author; }
    public String  getPost(){           return this.status; }
    public Set<String> getHashtagSet(){ return this.hashtagSet; }
    public String  getTimeOfCreation(){ return this.timeOfCreation; }
    public String  getTimeOfArrival(){  return this.timeOfArrival; }
    public Integer getHopCount(){       return this.hopCount; }
    public String  getForwarderList(){  return this.forwarderList; }
    public Integer getScore(){          return this.score;}
    public Integer getTTL(){            return this.ttl;}
    public String  getAttachedFile(){   return this.attachedFile; }
    public String  getFilePath(){       return this.filePath; }
    public long    getFileID(){         return 0; }

    public String toString() {
        String s = new String();
        s += "Author: "+this.author+"\n";
        s += "Status:" +this.status+"\n";
        s += "Time:" +this.timeOfCreation+"\n";
        return s;
    }
}



