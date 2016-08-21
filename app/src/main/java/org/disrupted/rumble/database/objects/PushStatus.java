/*
 * Copyright (C) 2014 Lucien Loiseau
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
 * @author Lucien Loiseau
 */
public class PushStatus {

    private static final String TAG  = "StatusMessage";

    public static final int STATUS_ID_RAW_SIZE       = 16;
    public static final int STATUS_POST_MAX_SIZE     = 20000;
    public static final int STATUS_HASHTAG_MAX_SIZE  = 50;
    public static final int STATUS_FILENAME_MAX_SIZE = 50;
    public static final int STATUS_ATTACHED_FILE_MAX_SIZE = 16000000; // limit 16Mb file

    protected long        dbid;
    protected String      uuid;
    protected Contact     author;
    protected Group       group;
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
    protected String      received_by; // sender UID

    // local user preference for this message
    protected boolean hasUserRead;
    protected boolean hasUserLiked;
    protected boolean hasUserSaved;

    public PushStatus(PushStatus message) {
        this.author          = message.author;
        this.group           = message.group;
        this.status          = message.status;
        this.timeOfCreation  = message.timeOfCreation;

        this.uuid = message.uuid;
        this.dbid = message.dbid;
        if(message.getHashtagSet() != null)
            this.hashtagSet  = new HashSet<String>(message.hashtagSet);
        else
            this.hashtagSet   = new HashSet<String>();
        this.attachedFile     = message.attachedFile;
        this.fileSize         = message.fileSize;
        this.timeOfArrival    = message.timeOfArrival;
        this.ttl              = message.ttl;
        this.hopCount         = message.hopCount;
        this.hopLimit         = message.hopLimit;
        this.like             = message.like;
        this.replication      = message.replication;
        this.duplicate        = message.duplicate;
        this.hasUserRead      = message.hasUserRead;
        this.hasUserLiked     = message.hasUserLiked;
        this.hasUserSaved     = message.hasUserSaved;
        this.received_by      = message.received_by;
    }

    public PushStatus(Contact author, Group group, String post, long timeOfCreation, String received_by) {
        this.uuid = HashUtil.computeStatusUUID(author.getUid(), group.getGid(), post, timeOfCreation);
        this.dbid   = -1;
        this.status = post;
        this.author = author;
        this.group  = group;
        this.received_by = received_by;
        hashtagSet  = new HashSet<String>();
        Pattern hashtagPattern = Pattern.compile("#(\\w+|\\W+)");
        Matcher hashtagMatcher = hashtagPattern.matcher(post);

        while (hashtagMatcher.find()) {
            hashtagSet.add(hashtagMatcher.group(0));
        }

        attachedFile   = "";
        fileSize       = 0;
        this.timeOfCreation = timeOfCreation;
        timeOfArrival       = System.currentTimeMillis();
        ttl            = -1;
        hopCount       = 0;
        hopLimit       = Integer.MAX_VALUE;
        like           = 0;
        replication    = 0;
        duplicate      = 0;
        hasUserRead  = false;
        hasUserLiked = false;
        hasUserSaved = false;
    }

    public long    getdbId() {              return this.dbid;                  }
    public String  getUuid() {              return this.uuid;                  }
    public Contact getAuthor() {            return this.author;                }
    public Group   getGroup()  {            return this.group;                 }
    public String  getPost(){               return this.status;                }
    public Set<String> getHashtagSet(){     return this.hashtagSet;            }
    public long    getTimeOfCreation(){     return this.timeOfCreation;        }
    public long    getTimeOfArrival(){      return this.timeOfArrival;         }
    public long    getTTL(){                return this.ttl;                   }
    public int     getHopCount(){           return this.hopCount;              }
    public int     getHopLimit(){           return this.hopLimit;              }
    public String  getFileName(){           return this.attachedFile;          }
    public long    getFileSize() {          return this.fileSize;              }
    public int     getLike(){               return like;                       }
    public int     getReplication(){        return replication;                }
    public int     getDuplicate(){          return duplicate;                  }
    public boolean hasAttachedFile(){       return (!attachedFile.equals("")); }
    public boolean hasUserLiked() {         return hasUserLiked;               }
    public boolean hasUserReadAlready() {   return hasUserRead;                }
    public boolean hasUserSaved() {         return hasUserSaved;               }
    public String  receivedBy() {           return received_by;                }
    public boolean isExpired() {
        return (System.currentTimeMillis() - timeOfCreation) < ttl;
    }
    
    public void setdbId(long dbid) {              this.dbid             = dbid;     }
    public void setFileName(String name){         this.attachedFile     = name;     }
    public void setFileSize(long fileSize) {      this.fileSize = fileSize;         }
    public void setTimeOfCreation(long toc){      this.timeOfCreation   = toc;      }
    public void setTimeOfArrival(long toa){       this.timeOfArrival    = toa;      }
    public void setTimeOfExpiration(long ttl){    this.ttl              = ttl;      }
    public void setHopCount(int hopcount){        this.hopCount         = hopcount; }
    public void setHopLimit(int hopLimit){        this.hopLimit         = hopLimit; }
    public void setLike(int like){                this.like             = like;     }
    public void addLike(){                        this.like++;                      }
    public void setTTL(long ttl){                 this.ttl              = ttl;      }
    public void setHashtagSet(Set<String> hashtagSet) {
        if(this.hashtagSet.size() > 0)
            this.hashtagSet.clear();
        if(hashtagSet == null)
            this.hashtagSet = new HashSet<String>();
        else
            this.hashtagSet = new HashSet<String>(hashtagSet);
    }
    public void addReplication(long replication){ this.replication  += replication; }
    public void addDuplicate(long duplicate){     this.duplicate  += duplicate; }
    public void setUserLike(boolean hasUserLiked){   this.hasUserLiked = hasUserLiked; }
    public void setUserRead(boolean userHasRead){    this.hasUserRead = userHasRead;   }
    public void setUserSaved(boolean hasUserSaved){  this.hasUserSaved = hasUserSaved; }
    public void setAuthor(Contact author) {
        this.author         = author;
        this.uuid  = HashUtil.computeStatusUUID(author.getUid(), group.getGid(), getPost(), timeOfCreation);
    }
    public void setGroup(Group group) {
        this.group = group;
        this.uuid  = HashUtil.computeStatusUUID(author.getUid(), group.getGid(), getPost(), timeOfCreation);
    }

    public void discard() {
        hashtagSet.clear();
    }

    @Override
    public boolean equals(Object o) {
        if(o == null)
            return false;
        if(o instanceof PushStatus) {
            PushStatus status = (PushStatus)o;
            return this.uuid.equals(status.uuid);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.uuid.hashCode();
    }

    @Override
    public String toString() {
        String s = new String();
        s += "Author: "+this.author.getUid()+" ("+this.author.getName()+")\n";
        s += "Group: "+this.group.getGid()+" ("+this.group.getName()+")\n";
        s += "Status:" +this.status+"\n";
        s += "Time:" +this.timeOfCreation+"\n";
        return s;
    }

}