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

package org.disrupted.rumble.network.protocols.firechat;


import org.disrupted.rumble.database.objects.ChatStatus;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.util.FileUtil;
import org.disrupted.rumble.util.HashUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * @author Marlinski
 */
public class FirechatMessageParser {

    private static final String TAG = "FirechatMessageParser";

    /*
     * JSON fields for nearby communication
     */
    public static final String TIMESTAMP = "t";
    public static final String UUID      = "uuid";
    public static final String USER      = "user";
    public static final String MESSAGE   = "msg";
    public static final String FIRECHAT  = "firechat";
    public static final String NAME      = "name";
    public static final String LOCATION  = "loc";
    public static final String LENGTH    = "length";
    public static final String URL       = "url";


    public String statusToNetwork(PushStatus message) {

        JSONObject jsonStatus = new JSONObject();
        try {
            jsonStatus.put(TIMESTAMP, message.getTimeOfCreation());
            jsonStatus.put(UUID, this.generateRandomUUID());
            jsonStatus.put(USER, message.getAuthor());

            if(!message.hasAttachedFile())
                jsonStatus.put(MESSAGE, message.getPost());
            else {
                try {
                    File file = new File(FileUtil.getReadableAlbumStorageDir(), message.getFileName());
                    if (file.exists() && !file.isDirectory()) {
                        jsonStatus.put(LENGTH, file.length());
                        jsonStatus.put(URL, "image");
                    }
                } catch(IOException ignore){
                }
            }

            String firechat = "#Nearby";
            if(message.getHashtagSet().size() > 0)
                firechat = message.getHashtagSet().iterator().next();
            firechat = firechat.substring(1);

            jsonStatus.put(FIRECHAT, firechat);
            jsonStatus.put(NAME, message.getAuthor());
        } catch ( JSONException e ) {
        }

        return jsonStatus.toString()+"\n";
    }

    public ChatStatus networkToStatus(JSONObject message) throws JSONException{
        ChatStatus retMessage = null;

        String post = "";
        long   length = 0;

        String uuid      = message.getString(UUID);      // don't know what to do with that either
        String author    = message.getString(NAME);
        String firechat  = message.getString(FIRECHAT);
        long   timestamp = message.getLong(TIMESTAMP);   // don't know what to do with that
        long   now = (System.currentTimeMillis() / 1000L);

        try { post   = message.getString(MESSAGE); } catch(JSONException ignore){ post = "";}
        try { length = message.getLong(LENGTH);    } catch(JSONException ignore){ length = 0; }

        /*
         * todo: I don't really get how firechat computes its timestamp, it doesn't seem
         * to be seconds since EPOCH so we keep the time of arrival instead
         */
        retMessage = new ChatStatus(author, post+" #"+firechat, now);
        retMessage.setFileSize(length);

        return retMessage;
    }


    private String generateRandomUUID() {
        char[] chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*(){}[]?><,./~`+=_-|".toCharArray();
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 16; i++) {
            char c = chars[random.nextInt(chars.length)];
            sb.append(c);
        }
        return sb.toString();
    }

}
