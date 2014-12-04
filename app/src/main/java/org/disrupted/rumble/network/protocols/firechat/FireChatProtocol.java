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

import org.disrupted.rumble.database.events.NewStatusEvent;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.protocols.Protocol;
import org.disrupted.rumble.network.protocols.command.ProtocolCommand;
import org.disrupted.rumble.network.protocols.command.SendStatusMessageCommand;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FireChatProtocol extends Protocol {

    private static final String TAG = "FirechatProtocol";
    public static final String ID = "Firechat";


    private static final int BUFFER_SIZE = 1024;
    private PushbackInputStream pbin;

    private static final FirechatMessageParser parser = new FirechatMessageParser();
    public Thread statusThread = null;

    public Protocol newInstance() {
        return new FireChatProtocol();
    }

    public FireChatProtocol() {
        this.protocolID = ID;
    }

    @Override
    public void initializeConnection() {
        statusThread = new MonitorNewStatusThread();
        statusThread.start();
    }

    public boolean isCommandSupported(String commandName) {
        if(commandName.equals(SendStatusMessageCommand.COMMAND_NAME))
            return true;
        return false;
    }


    /*
     * Firechat messages are a simple char stream representing a JSON ending with LF
     * however, if a file is attached to the message (like a picture), the message is quickly
     * followed by a binary stream representing the file.
     *
     *    I cannot use InputStream to read byte by byte with InputStream.read() trying to detect
     * the CRLF cause it would be way too long
     *
     *    On the other hand, I cannot use a BufferedInputStream either because it is char only
     * and will be unable to read the following binary stream, and it will mess with the original
     * InputStream as it reads ahead
     *
     *    Solution is thus to use a PushedBackInputStream that will read a whole buffer (1024)
     * and then it will search into it for a CRLF and pushback whatever follows.
     */
    @Override
    public void processingPacketFromNetwork() throws IOException{
        final int CR = 13;
        final int LF = 10;
        pbin = new PushbackInputStream(in, BUFFER_SIZE);

        while (true) {
            byte[] buffer=new byte[BUFFER_SIZE];
            int count = pbin.read(buffer,0,BUFFER_SIZE);
            Log.d(TAG, "read "+count+" bytes");
            int i = 0;
            char currentCharVal = (char)buffer[i++];
            while( (currentCharVal!=CR) && (currentCharVal!=LF) && (i < count))
                currentCharVal = (char) buffer[i++];
            if(i == count) {
                buffer = null;
            } else {
                Log.d(TAG, "messages = "+new String(buffer, 0, i - 1));
                try {
                    pbin.unread(buffer, i, count - i);
                    onPacketReceived(new String(buffer, 0, i - 1));
                } catch(IOException e){
                    Log.e(TAG, "[!] Error while unread",e);
                }
            }
        }
    }


    /*
    @Override
    public void processingPacketFromNetwork() throws IOException{
        final int CR = 13;
        final int LF = 10;
        //BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        PushbackInputStream pbin = new PushbackInputStream(in);

        while (true) {
            char[] buffer=new char[BUFFER_SIZE];
            int i=0;

            //A firechat message is a JSON String ending with LF
            int currentCharVal=r.read();
            while( (currentCharVal!=CR) && (currentCharVal!=LF) && (currentCharVal>=0)) {
                buffer[i++] = (char) currentCharVal;
                if (i<BUFFER_SIZE)
                    currentCharVal=r.read();
                else
                    break;
            }

            r.mark(0);
            r.reset();
            //r.close();

            if (currentCharVal<0) {
                //error while reading but we still try to process whatever we received
                if (i > 0)
                    onPacketReceived(new String(buffer, 0, i));

                buffer = null;
                continue;
            }

            if((currentCharVal != CR) && (currentCharVal != LF)) {
                //we reached end of buffer, we silently discard the packet
                buffer = null;
                continue;
            }

            onPacketReceived(new String(buffer,0,i));
        }
    }
    */

    public boolean onPacketReceived(String jsonString) {
        try {
            StatusMessage status = parser.networkToStatus(jsonString, pbin);
            onStatusMessageReceived(status);
        } catch (JSONException ignore) {
            Log.d(TAG, "malformed JSON");
        }
        return false;
    }

    @Override
    public boolean onCommandReceived(ProtocolCommand command) {
        if(!isCommandSupported(command.getCommandName()))
            return false;
        if(command instanceof SendStatusMessageCommand) {
            StatusMessage statusMessage = ((SendStatusMessageCommand)command).getStatus();
            String jsonStatus = parser.statusToNetwork(statusMessage);
            try {
                this.out.write(jsonStatus.getBytes(Charset.forName("UTF-8")));
            }
            catch(IOException ignore){
                Log.e(TAG, "[!] error while sending");
            }
        }

        return true;
    }

    @Override
    public void destroyConnection() {
        statusThread.interrupt();
        try { pbin.close(); } catch(IOException ignore) {}
    }

    /*
     * This thread monitors the new status received and forward them to the neighbour
     * (unless it is one of his)
     */
    public class MonitorNewStatusThread extends Thread {

        private BlockingQueue<StatusMessage> messageQueue;

        public MonitorNewStatusThread() {
            messageQueue = new LinkedBlockingDeque<StatusMessage>();
        }

        @Override
        public void run() {
            super.run();
            try {
                EventBus.getDefault().register(this);
                while(true) {
                    StatusMessage message = messageQueue.take();

                    if (message.getAuthor() == record.getName())
                        continue;

                    ProtocolCommand sendMessageCommand = new SendStatusMessageCommand(message);
                    executeCommand(sendMessageCommand);
                }
            }
            catch(InterruptedException e) {
                EventBus.getDefault().unregister(this);
                messageQueue.clear();
                messageQueue = null;
            }

        }

        public void onEvent(NewStatusEvent newStatus) {
            if(newStatus.getStatus() == null)
                return;
            messageQueue.add(newStatus.getStatus());
        }

    }


}
