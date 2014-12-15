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

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.NetworkThread;
import org.disrupted.rumble.network.events.StatusSentEvent;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.exceptions.ProtocolNotFoundException;
import org.disrupted.rumble.network.exceptions.RecordNotFoundException;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.protocols.GenericProtocol;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.command.SendStatusMessageCommand;
import org.disrupted.rumble.util.FileUtil;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FirechatBTProtocol extends GenericProtocol implements NetworkThread {

    private static final String TAG = "FirechatBluetoothClient";

    private static final FirechatMessageParser parser = new FirechatMessageParser();
    private static final int BUFFER_SIZE = 1024;
    private PushbackInputStream pbin;
    protected boolean isBeingKilled;
    private BluetoothConnection con;

    public FirechatBTProtocol(BluetoothConnection con) {
        this.con = con;
        this.isBeingKilled = false;
    }
    @Override
    public String getNetworkThreadID() {
        return getProtocolID()+" "+con.getConnectionID();
    }
    @Override
    public String getProtocolID() {
        return FirechatProtocol.protocolID;
    }
    @Override
    public String getType() {
        return con.getLinkLayerIdentifier();
    }
    @Override
    public String getLinkLayerIdentifier() {
        return con.getLinkLayerIdentifier();
    }


    @Override
    public void run() {

        try {
            con.connect();
        } catch (LinkLayerConnectionException exception) {
            Log.d(TAG, "[!] FAILED: "+getNetworkThreadID()+" "+exception.getMessage());
            return;
        }

        try {
            NetworkCoordinator.getInstance().addProtocol(con.getRemoteMacAddress(), this);

            Log.d(TAG, "[+] ESTABLISHED: " + getNetworkThreadID());

            /*
             * this one automatically creates two thread, one for processing the command
             * and one for processing the network
             */
            onGenericProcotolConnected();

        } catch (RecordNotFoundException ignoredCauseImpossible) {
            Log.e(TAG, "[+] FAILED: "+getNetworkThreadID()+" cannot find the record for "+con.getRemoteMacAddress());
        }
        finally {
            try {
                NetworkCoordinator.getInstance().delProtocol(con.getRemoteMacAddress(), this);
            } catch (RecordNotFoundException ignoredCauseImpossible) {
            } catch (ProtocolNotFoundException ignoredCauseImpossible) {
            }

            if (!isBeingKilled)
                kill();
        }
    }

    /*
     * A Firechat message is a simple char stream representing a JSON file, ending with LF.
     * however, if a file is attached to the message (like a picture), the message is
     * followed by a binary stream representing the file.
     *
     *    I cannot use InputStream to read byte by byte with InputStream.read() trying to detect
     * the CRLF cause it would be way too long
     *
     *    On the other hand, I cannot use a BufferedInputStream either because it is char only
     * and it will be unable to read the following binary stream. plus, it will also mess with
     * the original InputStream as it reads ahead
     *
     *    Solution is thus to use a PushedBackInputStream that will read a whole buffer (1024)
     * and then it will search into it for a CRLF and pushback (unread) whatever follows for
     * further binary reading.
     */
    @Override
    public void processingPacketFromNetwork() {
        try {
            final int CR = 13;
            final int LF = 10;
            pbin = new PushbackInputStream(con.getInputStream(), BUFFER_SIZE);

            while (true) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int count = pbin.read(buffer, 0, BUFFER_SIZE);

                int i = 0;
                char currentCharVal = (char) buffer[i++];
                while ((currentCharVal != CR) && (currentCharVal != LF) && (i < count))
                    currentCharVal = (char) buffer[i++];

                if ((currentCharVal != CR) && (currentCharVal != LF)) {
                    //whatever it was, it was not a Firechat message
                    buffer = null;
                } else {
                    try {
                        pbin.unread(buffer, i, count - i);
                        onPacketReceived(new String(buffer, 0, i - 1));
                    } catch (IOException e) {
                        Log.e(TAG, "[!] Error while unread" + e.toString());
                    }
                }
            }
        } catch (IOException silentlyCloseConnection) {
        } catch (InputOutputStreamException silentlyCloseConnection) {
        }
    }

    public boolean onPacketReceived(String jsonString) {
        try {
            StatusMessage status = parser.networkToStatus(jsonString, pbin, con.getRemoteMacAddress());
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).insertStatus(status, null);
            Log.d(TAG, "Status received from Network:\n" + status.toString());
        } catch (JSONException ignore) {
            Log.d(TAG, "malformed JSON");
        }
        return false;
    }


    @Override
    public boolean isCommandSupported(String commandName) {
        if(commandName.equals(SendStatusMessageCommand.COMMAND_NAME))
            return true;
        return false;
    }

    @Override
    protected boolean onCommandReceived(Command command) {
        if(!isCommandSupported(command.getCommandName()))
            return false;

        if(command instanceof SendStatusMessageCommand) {
            StatusMessage statusMessage = ((SendStatusMessageCommand)command).getStatus();
            if(statusMessage.isForwarder(con.getRemoteMacAddress(), FirechatProtocol.protocolID))
                return false;

            String jsonStatus = parser.statusToNetwork(statusMessage);
            try {
                long timeToTransfer = System.currentTimeMillis();
                long bytesTransfered = jsonStatus.getBytes(Charset.forName("UTF-8")).length;
                con.getOutputStream().write(jsonStatus.getBytes(Charset.forName("UTF-8")));
                Log.d(TAG, jsonStatus.toString());
                if(!statusMessage.getFileName().equals("")) {
                    File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(), statusMessage.getFileName());
                    if(attachedFile.exists() && !attachedFile.isDirectory()) {
                        FileInputStream fis = new FileInputStream(attachedFile);
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int count;
                        while ((count = fis.read(buffer)) > 0) {
                            con.getOutputStream().write(buffer, 0, count);
                            bytesTransfered += count;
                        }
                        fis.close();
                    } else {
                        throw  new IOException("File: "+statusMessage.getFileName()+" does not exists");
                    }
                }

                timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
                long throughput = (bytesTransfered / (timeToTransfer == 0 ? 1: timeToTransfer));
                List<String> recipients = new LinkedList<String>();
                recipients.add(con.getRemoteMacAddress());
                EventBus.getDefault().post(new StatusSentEvent(
                        statusMessage,
                        recipients,
                        FirechatProtocol.protocolID,
                        BluetoothLinkLayerAdapter.LinkLayerIdentifier,
                        throughput)
                );

                //remove that
                Log.d(TAG, "Status Sent ("+con.getRemoteMacAddress()+","+(throughput/1000L)+"): " + statusMessage.toString());
                try {
                    Thread.sleep(1000);
                }catch(InterruptedException e) {}
            } catch(IOException ignore){
                Log.e(TAG, "[!] error while sending"+ignore.getMessage());
            } catch (InputOutputStreamException e) {
                Log.e(TAG, e.getMessage());
            }
        }

        return true;
    }

    @Override
    public void stop() {
        kill();
    }

    @Override
    public void kill() {
        this.isBeingKilled = true;
        try {
            con.disconnect();
        } catch (LinkLayerConnectionException e) {
            Log.e(TAG, e.getMessage());
        }
        Log.d(TAG, "[-] ENDED: " + getNetworkThreadID());
    }

}
