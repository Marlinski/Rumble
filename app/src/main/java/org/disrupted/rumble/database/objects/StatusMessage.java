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

package org.disrupted.rumble.database.objects;


import org.disrupted.rumble.database.GroupDatabase;
import org.disrupted.rumble.util.HashUtil;

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
    protected String      group;
    protected String      status;
    protected Set<String> hashtagSet;
    protected String      attachedFile;
    protected long        fileSize; // firechat only
    protected long        timeOfCreation;
    protected long        timeOfArrival;
    protected long        ttl;
    protected int         hopCount;
    protected int         hopLimit;
    protected int         like;
    protected int         replication;
    protected int         duplicate;
    protected Set<String> forwarderList;

    // local user preference for this message
    protected boolean hasUserRead;
    protected boolean hasUserLiked;
    protected boolean hasUserSaved;

    public StatusMessage(StatusMessage message) {
        this.dbid = message.getdbId();
        this.uuid = message.getUuid();
        this.author = message.getAuthor();
        this.group = message.getGroup();
        this.status = message.getPost();
        this.hashtagSet = new HashSet<String>(message.getHashtagSet());
        this.attachedFile = message.getFileName();
        this.fileSize = message.getFileSize();
        this.timeOfCreation = message.getTimeOfCreation();
        this.timeOfArrival = message.getTimeOfArrival();
        this.ttl = message.getTTL();
        this.hopCount = message.getHopCount();
        this.hopLimit = message.getHopLimit();
        this.like = message.getLike();
        this.replication  = message.getReplication();
        this.duplicate = message.getDuplicate();
        this.forwarderList = new HashSet<String>(message.getForwarderList());
        this.hasUserRead = message.hasUserReadAlready();
        this.hasUserLiked = message.hasUserLiked();
        this.hasUserSaved = message.hasUserSaved();
    }


    public StatusMessage(String post, String author, long timeOfCreation) {
        this.uuid = HashUtil.computeStatusUUID(post, author, timeOfCreation);
        this.messageType = TYPE;
        this.dbid   = -1;
        this.status = post;
        this.author = author;
        this.group  = GroupDatabase.DEFAULT_PUBLIC_GROUP;
        hashtagSet  = new HashSet<String>();
        Pattern hashtagPattern = Pattern.compile("#(\\w+|\\W+)");
        Matcher hashtagMatcher = hashtagPattern.matcher(post);
        hashtagSet  = new HashSet<String>();
        while (hashtagMatcher.find()) {
            hashtagSet.add(hashtagMatcher.group(0));
        }

        attachedFile   = "";
        fileSize = 0;
        this.timeOfCreation = timeOfCreation;
        timeOfArrival  = (System.currentTimeMillis() / 1000L);
        hopCount       = 0;
        hopLimit       = Integer.MAX_VALUE;
        forwarderList  = new HashSet<String>();
        ttl            = 0;
        like           = 0;
        replication    = 0;
        duplicate      = 0;

        hasUserRead  = false;
        hasUserLiked = false;
        hasUserSaved = false;
    }


    public long    getdbId() {              return this.dbid;                  }
    public String  getUuid() {              return this.uuid;                  }
    public String  getAuthor(){             return this.author;                }
    public String  getGroup() {             return this.group;                 }
    public String  getPost(){               return this.status;                }
    public Set<String> getHashtagSet(){     return this.hashtagSet;            }
    public long    getTimeOfCreation(){     return this.timeOfCreation;        }
    public long    getTimeOfArrival(){      return this.timeOfArrival;         }
    public int     getHopCount(){           return this.hopCount;              }
    public int     getHopLimit(){           return this.hopLimit;              }
    public Set<String> getForwarderList(){  return this.forwarderList;         }
    public long    getTTL(){                return this.ttl;                   }
    public String  getFileName(){           return this.attachedFile;          }
    public long    getFileSize() {          return this.fileSize;              }
    public int     getLike(){               return like;                       }
    public int     getReplication(){        return replication;                }
    public int     getDuplicate(){          return duplicate;                  }
    public boolean hasAttachedFile(){       return (!attachedFile.equals("")); }
    public boolean hasUserLiked() {         return hasUserLiked;               }
    public boolean hasUserReadAlready() {   return hasUserRead;                }
    public boolean hasUserSaved() {         return hasUserSaved;               }
    public boolean isForwarder(String linkLayerAddress, String protocolID) {
        return forwarderList.contains(HashUtil.computeForwarderHash(linkLayerAddress, protocolID));
    }

    public void setdbId(long dbid) {              this.dbid           = dbid;     }
    public void setUuid(String uuid) {            this.uuid           = uuid;     }
    public void setGroup(String group) {          this.group           = group;   }
    public void setFileName(String filename){     this.attachedFile   = filename; }
    public void setFileSize(long fileSize) {      this.fileSize = fileSize;       }
    public void setTimeOfCreation(long toc){      this.timeOfCreation = toc;      }
    public void setTimeOfArrival(long toa){       this.timeOfArrival  = toa;      }
    public void setHopCount(int hopcount){        this.hopCount       = hopcount; }
    public void setHopLimit(int hopLimit){        this.hopLimit       = hopLimit; }
    public void setLike(int like){                this.like           = like;     }
    public void addLike(){                        this.like++;                    }
    public void setTTL(long ttl){                 this.ttl            = ttl;      }
    public void setHashtagSet(Set<String> hashtagSet) {
        if(hashtagSet.size() > 0)
            hashtagSet.clear();
        this.hashtagSet = hashtagSet;
    }
    public void addReplication(long replication){ this.replication  += replication; }
    public void addDuplicate(long duplicate){     this.duplicate  += duplicate; }
    public void setForwarderList(Set<String> forwarderList){
        if(this.forwarderList.size() > 0)
            this.forwarderList.clear();
        this.forwarderList  = forwarderList;
    }
    public void addForwarder(String linkLayerAddress, String protocolID) {
        forwarderList.add(HashUtil.computeForwarderHash(linkLayerAddress, protocolID));
    }
    public void setUserLike(boolean hasUserLiked){   this.hasUserLiked = hasUserLiked; }
    public void setUserRead(boolean userHasRead){    this.hasUserRead = userHasRead; }
    public void setUserSaved(boolean hasUserSaved){  this.hasUserSaved = hasUserSaved; }

    public void discard() {
        hashtagSet = null;
        forwarderList = null;
    }

    public String toString() {
        String s = new String();
        s += "Author: "+this.author+"\n";
        s += "Status:" +this.status+"\n";
        s += "Time:" +this.timeOfCreation+"\n";
        return s;
    }

}