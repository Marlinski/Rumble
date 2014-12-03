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

import java.io.IOException;
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

    @Override
    public boolean onPacketReceived(byte[] bytes, int size) {
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
