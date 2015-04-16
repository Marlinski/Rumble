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

/**
 * @author Marlinski
 */
public class ChatStatus  {

    protected String      author_name;
    protected String      status;
    protected String      attachedFile;
    protected long        fileSize;
    protected long        timeOfArrival;

    public ChatStatus(String author, String status, long toa) {
        this.author_name = author;
        this.status = status;
        this.timeOfArrival = toa;
    }

    public long getFileSize() {       return fileSize;          }
    public String getAttachedFile() { return this.attachedFile; }

    public void setFileSize(long fileSize) {           this.fileSize = fileSize;         }
    public void setAttachedFile(String attachedFile) { this.attachedFile = attachedFile; }

}
