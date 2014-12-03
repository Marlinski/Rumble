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

import org.disrupted.rumble.message.Message;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.protocols.Protocol;
import org.disrupted.rumble.network.protocols.command.ProtocolCommand;
import org.disrupted.rumble.network.protocols.command.SendStatusMessageCommand;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * @author Marlinski
 */
public class FireChatProtocol extends Protocol {

    private static final String TAG = "FirechatProtocol";
    public static final String ID = "Firechat";

    private static final FirechatMessageParser parser = new FirechatMessageParser();
    private Thread queueThread = null;

    public Protocol newInstance() {
        return new FireChatProtocol();
    }

    public FireChatProtocol() {
        this.protocolID = ID;
    }

    public boolean isCommandSupported(String commandName) {
        if(commandName.equals(SendStatusMessageCommand.COMMAND_NAME))
            return true;
        return false;
    }

    @Override
    synchronized public boolean onPacketReceived(byte[] bytes, int size) {
        try {
            String jsonString = new String(bytes, 0, size, "UTF-8");
            StatusMessage status = parser.networkToStatus(jsonString);
            onStatusMessageReceived(status);
        } catch (UnsupportedEncodingException ignore){
        } catch (JSONException ignore) {
            Log.d(TAG, "malformed JSON");
        }
        return false;
    }

    @Override
    synchronized public boolean onCommandReceived(ProtocolCommand command) {
        if(!isCommandSupported(command.getCommandName()))
            return false;
        return true;
    }
}
