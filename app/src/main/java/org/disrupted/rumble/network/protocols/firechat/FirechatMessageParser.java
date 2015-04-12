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


import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.util.FileUtil;
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
    private static final String TIMESTAMP = "t";
    private static final String UUID      = "uuid";
    private static final String USER      = "user";
    private static final String MESSAGE   = "msg";
    private static final String FIRECHAT  = "firechat";
    private static final String NAME      = "name";
    private static final String LOCATION  = "loc";
    private static final String LENGTH    = "length";
    private static final String URL       = "url";


    public String statusToNetwork(StatusMessage message) {

        JSONObject jsonStatus = new JSONObject();
        try {
            jsonStatus.put(TIMESTAMP, message.getTimeOfCreation());
            jsonStatus.put(UUID, this.generateRandomUUID());
            jsonStatus.put(USER, message.getAuthor());

            if(message.getFileName().equals(""))
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

    public StatusMessage networkToStatus(String message) throws JSONException{
        StatusMessage retMessage = null;
        JSONObject object = new JSONObject(message);

        String post = "";
        long   length = 0;

        String uuid      = object.getString(UUID);
        String author    = object.getString(NAME);
        String firechat  = object.getString(FIRECHAT);
        long   timestamp = object.getLong(TIMESTAMP);
        long   now = (System.currentTimeMillis() / 1000L);

        try { post   = object.getString(MESSAGE); } catch(JSONException ignore){ post = "";}
        try { length = object.getLong(LENGTH);    } catch(JSONException ignore){ length = 0; }

        /*
         * todo: I don't really get how firechat computes its timestamp, it doesn't seem
         * to be seconds since EPOCH so we keep the time of arrival instead
         */
        retMessage = new StatusMessage(post+" #"+firechat, author, now);
        retMessage.setTimeOfArrival(now);
        retMessage.setFileSize(length);
        retMessage.setUuid(uuid);
        retMessage.setHopCount(1);

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
