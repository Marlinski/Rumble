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

import org.disrupted.rumble.util.HashUtil;

/**
 * @author Lucien Loiseau
 */
public class ChatMessage {

    public static final int MSG_MAX_SIZE         = 1000;
    public static final int MSG_ID_RAW_SIZE      = 16;
    public static final int MSG_HASHTAG_MAX_SIZE = 50;

    protected Contact     contact;
    protected String      uuid;
    protected String      message;
    protected String      attachedFile;
    protected long        fileSize;
    protected long        author_timestamp;
    protected long        timestamp;
    protected boolean     read;
    protected String      protocolID;
    protected int         nbRecipients;

    public ChatMessage(Contact contact, String message, long author_timestamp, long timestamp, String protocolID) {
        this.uuid = HashUtil.computeChatMessageUUID(contact.getUid(), message, author_timestamp);
        this.contact = contact;
        this.message = message;
        this.author_timestamp = author_timestamp;
        this.timestamp = timestamp;
        this.protocolID = protocolID;

        this.read = false;
        this.attachedFile = "";
        this.fileSize = 0;
        this.nbRecipients = 0;
    }

    public ChatMessage(ChatMessage message) {
        this.contact = message.contact;
        this.message = message.message;
        this.author_timestamp = message.author_timestamp;
        this.timestamp = message.timestamp;
        this.uuid = message.uuid;
        this.protocolID = message.protocolID;

        this.read = message.read;
        this.attachedFile = message.attachedFile;
        this.fileSize = message.fileSize;
        this.nbRecipients = message.nbRecipients;
    }

    public String  getUUID() {            return uuid;                     }
    public Contact getAuthor() {          return contact;                  }
    public String  getMessage() {         return message;                  }
    public long    getAuthorTimestamp() { return author_timestamp;         }
    public long    getTimestamp() {       return timestamp;                }
    public long    getFileSize() {        return fileSize;                 }
    public String  getAttachedFile() {    return this.attachedFile;        }
    public boolean hasUserReadAlready() { return read;                     }
    public boolean hasAttachedFile()    { return !attachedFile.equals(""); }
    public String  getProtocolID() {      return protocolID;               }
    public int     getNbRecipients() {    return nbRecipients;                }

    public void setUUID(String UUID) {                 this.uuid = UUID;                 }
    public void setFileSize(long fileSize) {           this.fileSize = fileSize;         }
    public void setAttachedFile(String attachedFile) { this.attachedFile = attachedFile; }
    public void setUserRead(boolean read) {            this.read = read;                 }
    public void setNbRecipients(int nb) {              this.nbRecipients = nb;           }

    @Override
    public boolean equals(Object o) {
        if(o == null)
            return false;
        if(o instanceof ChatMessage) {
            ChatMessage msg = (ChatMessage)o;
            return this.uuid.equals(msg.uuid);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.uuid.hashCode();
    }


    @Override
    public String toString() {
        return message;
    }
}
