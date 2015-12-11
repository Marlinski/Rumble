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

package org.disrupted.rumble.network.protocols.firechat;


import android.util.Base64;

import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.util.FileUtil;
import org.disrupted.rumble.util.HashUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Random;

/**
 * @author Lucien Loiseau
 */
public class FirechatMessageParser {

    private static final String TAG = "FirechatMessageParser";

    /*
     * JSON fields for nearby communication
     */
    public static final String TIMESTAMP = "t";
    public static final String UTC       = "st";
    public static final String UUID      = "uuid";
    public static final String USER      = "user";
    public static final String MESSAGE   = "msg";
    public static final String FIRECHAT  = "firechat";
    public static final String NAME      = "name";
    public static final String LOCATION  = "loc";
    public static final String LENGTH    = "length";
    public static final String URL       = "url";
    public static final String RUMBLEID  = "rumbleID";


    public String chatMessageToNetwork(ChatMessage message) {

        JSONObject jsonStatus = new JSONObject();
        try {
            NumberFormat formatter = new DecimalFormat("0.############E0");
            String timeScientificNotation = formatter.format(message.getTimestamp());
            jsonStatus.put(TIMESTAMP, timeScientificNotation);
            jsonStatus.put(UTC,       timeScientificNotation);
            jsonStatus.put(UUID,      message.getUUID());
            jsonStatus.put(USER,      message.getAuthor().getName());
            jsonStatus.put(RUMBLEID,  message.getAuthor().getUid());

            if(!message.hasAttachedFile()) {
                jsonStatus.put(MESSAGE, message.getMessage());
            } else {
                try {
                    File file = new File(FileUtil.getReadableAlbumStorageDir(), message.getAttachedFile());
                    if (file.exists() && !file.isDirectory()) {
                        jsonStatus.put(LENGTH, file.length());
                        jsonStatus.put(URL, "image");
                    }
                } catch(IOException ignore){
                }
            }

            jsonStatus.put(FIRECHAT, "Nearby");
            jsonStatus.put(NAME, message.getAuthor().getName());
        } catch ( JSONException e ) {
        }

        return jsonStatus.toString()+"\n";
    }

    public ChatMessage networkToChatMessage(JSONObject message) throws JSONException{
        ChatMessage retMessage = null;

        String post = "";
        long   length = 0;

        String firechatid = message.getString(UUID);
        String author     = message.getString(NAME);
        String firechat   = message.getString(FIRECHAT);
        String author_id  = message.getString(RUMBLEID);
        String timeScientificNotation  = message.getString(TIMESTAMP);
        long   timestamp = Double.valueOf(timeScientificNotation).longValue();

        try { post   = message.getString(MESSAGE); } catch(JSONException ignore){ post = "";}
        try { length = message.getLong(LENGTH);    } catch(JSONException ignore){ length = 0; }

        if(author_id.equals(""))
            author_id = HashUtil.computeContactUid(author+"FireChat",0);

        Contact contact = new Contact(author, author_id, false);
        long receivedAt = System.currentTimeMillis();
        retMessage = new ChatMessage(contact, post, timestamp, receivedAt, FirechatProtocol.protocolID);

        // we store the message in Base64 because it is more readable
        if(HashUtil.isBase64Encoded(firechatid))
            retMessage.setUUID(firechatid);
        else
            retMessage.setUUID(Base64.encodeToString(firechatid.getBytes(), Base64.NO_WRAP));
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
