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


import org.disrupted.rumble.util.HashUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Marlinski
 */
public class PushStatus extends Message{

    private static final String TAG  = "StatusMessage";
    public  static final String TYPE = "STATUS";

    protected long        dbid;
    protected String      uuid;
    protected String      author_id;
    protected String      group_id;
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

    protected Contact     author;
    protected Group       group;

    // local user preference for this message
    protected boolean hasUserRead;
    protected boolean hasUserLiked;
    protected boolean hasUserSaved;

    public PushStatus(PushStatus message) {
        this.author_id = message.getAuthorID();
        this.status = message.getPost();
        this.timeOfCreation = message.getTimeOfCreation();

        this.uuid = message.getUuid();
        this.dbid = message.getdbId();
        this.group_id = message.getGroupID();
        if(message.getHashtagSet() != null)
            this.hashtagSet = new HashSet<String>(message.getHashtagSet());
        else
            this.hashtagSet = new HashSet<String>();
        this.attachedFile = message.getFileName();
        this.fileSize = message.getFileSize();
        this.timeOfArrival = message.getTimeOfArrival();
        this.ttl = message.getTTL();
        this.hopCount = message.getHopCount();
        this.hopLimit = message.getHopLimit();
        this.like = message.getLike();
        this.replication  = message.getReplication();
        this.duplicate = message.getDuplicate();
        if(message.getForwarderList() != null)
            this.forwarderList = new HashSet<String>(message.getForwarderList());
        else
            this.forwarderList = new HashSet<String>();
        this.hasUserRead = message.hasUserReadAlready();
        this.hasUserLiked = message.hasUserLiked();
        this.hasUserSaved = message.hasUserSaved();
    }

    public PushStatus(String author_id, String group_id, String post, long timeOfCreation) {
        this.uuid = HashUtil.computeStatusUUID(author_id, group_id, post, timeOfCreation);
        this.messageType = TYPE;
        this.dbid   = -1;
        this.status = post;
        this.author_id = author_id;
        this.group_id  = group_id;
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
    public String  getAuthorID(){           return this.author_id;             }
    public String  getGroupID() {           return this.group_id;              }
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
    public Contact getAuthor() {            return this.author;                }
    public Group getGroup()  {              return this.group;                 }

    public void setdbId(long dbid) {              this.dbid           = dbid;     }
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
        if(this.hashtagSet.size() > 0)
            this.hashtagSet.clear();
        if(hashtagSet == null)
            this.hashtagSet = new HashSet<String>();
        else
            this.hashtagSet = hashtagSet;
    }
    public void addReplication(long replication){ this.replication  += replication; }
    public void addDuplicate(long duplicate){     this.duplicate  += duplicate; }
    public void setForwarderList(Set<String> forwarderList){
        if(this.forwarderList.size() > 0)
            this.forwarderList.clear();
        if(forwarderList == null)
            this.forwarderList = new HashSet<String>();
        else
            this.forwarderList  = forwarderList;
    }
    public void addForwarder(String linkLayerAddress, String protocolID) {
        forwarderList.add(HashUtil.computeForwarderHash(linkLayerAddress, protocolID));
    }
    public void setUserLike(boolean hasUserLiked){   this.hasUserLiked = hasUserLiked; }
    public void setUserRead(boolean userHasRead){    this.hasUserRead = userHasRead;   }
    public void setUserSaved(boolean hasUserSaved){  this.hasUserSaved = hasUserSaved; }
    public void setAuthor(Contact author) {       this.author         = author;        }
    public void setGroup(Group group) {           this.group          = group;         }


    public void discard() {
        hashtagSet = null;
        forwarderList = null;
    }

    public String toString() {
        String s = new String();
        s += "Author: "+this.author_id+"\n";
        s += "Group: "+this.group_id+"\n";
        s += "Status:" +this.status+"\n";
        s += "Time:" +this.timeOfCreation+"\n";
        return s;
    }

}