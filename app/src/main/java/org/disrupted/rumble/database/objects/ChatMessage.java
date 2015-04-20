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
public class ChatMessage {

    protected Contact     contact;
    protected String      uuid;
    protected String      message;
    protected String      attachedFile;
    protected long        fileSize;
    protected long        timeOfArrival;
    protected boolean     read;

    public ChatMessage(Contact contact, String message, long timeOfArrival) {
        this.uuid = HashUtil.computeChatMessageUUID(contact.getUid(), message, timeOfArrival);
        this.contact = contact;
        this.message = message;
        this.timeOfArrival = timeOfArrival;

        this.read = false;
        this.attachedFile = "";
        this.fileSize = 0;
    }

    public ChatMessage(ChatMessage message) {
        this.contact = message.contact;
        this.message = message.message;
        this.timeOfArrival = message.timeOfArrival;
        this.uuid = HashUtil.computeChatMessageUUID(this.contact.getUid(), this.message, timeOfArrival);

        this.read = message.read;
        this.attachedFile = message.attachedFile;
        this.fileSize = message.fileSize;
    }

    public String  getUUID() {            return uuid;                     }
    public Contact getAuthor() {          return contact;                  }
    public String  getMessage() {         return message;                  }
    public long    getTimeOfArrival() {   return timeOfArrival;            }
    public long    getFileSize() {        return fileSize;                 }
    public String  getAttachedFile() {    return this.attachedFile;        }
    public boolean hasUserReadAlready() { return read;                     }
    public boolean hasAttachedFile()    { return !attachedFile.equals(""); }

    public void setFileSize(long fileSize) {           this.fileSize = fileSize;         }
    public void setAttachedFile(String attachedFile) { this.attachedFile = attachedFile; }
    public void setUserRead(boolean read) {            this.read = read;                 }


    @Override
    public String toString() {
        return message;
    }
}
