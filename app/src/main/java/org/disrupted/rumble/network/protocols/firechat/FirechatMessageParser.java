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


import android.util.Log;

import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.util.FileUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

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

            if(message.getFileName() == "")
                jsonStatus.put(MESSAGE, message.getPost());
            else {
                //todo use a FILE DATABASE
                File file=new File(FileUtil.getReadableAlbumStorageDir(), message.getFileName());
                jsonStatus.put(LENGTH, file.length());
                jsonStatus.put(URL, "image");
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

    public StatusMessage networkToStatus(String message, PushbackInputStream in, String macAddress) throws JSONException{
        StatusMessage retMessage = null;
        JSONObject object = new JSONObject(message);

        String post = "";
        long   length = 0;

        String author    = object.getString(NAME);
        String timestamp = object.getString(TIMESTAMP);
        String uuid      = object.getString(UUID);
        String firechat  = object.getString(FIRECHAT);

        try { post   = object.getString(MESSAGE); } catch(JSONException ignore){ post = "";}
        try { length = object.getLong(LENGTH); } catch(JSONException ignore){ length = 0; }

        retMessage = new StatusMessage(post+" #"+firechat, author);

        Log.d(TAG, message);
        if(length > 0) {
            String fileName = author+"-"+timestamp+".jfif";
            String filePath = downloadFile(fileName, length, in);
            if(filePath != "") {
                Log.d(TAG, "[+] downloaded !");
                retMessage.setFileName(fileName);
                retMessage.setFileSize(length);
            }
        }
        retMessage.setTimeOfCreation(timestamp);
        retMessage.setTimeOfArrival(timestamp);
        retMessage.setHopCount(1);
        retMessage.addForwarder(macAddress);

        return retMessage;

    }

    public String downloadFile(String fileName, long length, PushbackInputStream in) {
        File directory = FileUtil.getWritableAlbumStorageDir(length);
        Log.d(TAG, "[+] Downloading file to: "+directory);
        if(directory != null) {
            File attachedFile = new File(directory + File.separator + fileName);
            FileOutputStream fos;
            try {
                if (!attachedFile.createNewFile())
                    throw new FileNotFoundException("Cannot create file "+attachedFile.toString());

                fos = new FileOutputStream(attachedFile);

                final int BUFFER_SIZE = 1024;
                while (length > 0) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int count;
                    count = in.read(buffer, 0, BUFFER_SIZE);
                    if (count < 0)
                        throw new IOException("End of stream reached");
                    length -= count;
                    fos.write(buffer, 0, count);
                }
                fos.close();
                return fileName;
            }
            catch (IOException e) {
                Log.e(TAG, "[-] file has not been downloaded ",e);
            }
        }
        return "";
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
