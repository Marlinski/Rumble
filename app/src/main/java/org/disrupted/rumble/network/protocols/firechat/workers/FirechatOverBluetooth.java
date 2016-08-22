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

package org.disrupted.rumble.network.protocols.firechat.workers;

import android.content.Intent;
import android.net.Uri;
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.command.CommandSendChatMessage;
import org.disrupted.rumble.network.protocols.events.ChatMessageReceived;
import org.disrupted.rumble.network.protocols.events.ChatMessageSent;
import org.disrupted.rumble.network.protocols.events.ContactInformationReceived;
import org.disrupted.rumble.network.events.ChannelConnected;
import org.disrupted.rumble.network.events.ChannelDisconnected;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.command.CommandSendPushStatus;
import org.disrupted.rumble.network.protocols.firechat.FirechatBTState;
import org.disrupted.rumble.network.protocols.firechat.FirechatMessageParser;
import org.disrupted.rumble.network.protocols.firechat.FirechatProtocol;
import org.disrupted.rumble.util.FileUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class FirechatOverBluetooth extends ProtocolChannel {

    private static final String TAG = "FirechatOverBluetooth";

    private static final FirechatMessageParser parser = new FirechatMessageParser();
    private static final int BUFFER_SIZE = 1024;
    private PushbackInputStream pbin;
    private boolean working;
    private Contact remoteContact;

    private BluetoothNeighbour bluetoothNeighbour;

    public FirechatOverBluetooth(FirechatProtocol protocol, BluetoothConnection con) {
        super(protocol, con);
        this.working = false;
        bluetoothNeighbour = new BluetoothNeighbour(con.getRemoteLinkLayerAddress());
    }

    public BluetoothNeighbour getBluetoothNeighbour() {
        return bluetoothNeighbour;
    }

    @Override
    public boolean isWorking() {
        return working;
    }

    @Override
    public String getProtocolIdentifier() {
        return FirechatProtocol.protocolID;
    }

    @Override
    public String getWorkerIdentifier() {
        return getProtocolIdentifier()+" "+con.getConnectionID();
    }

    @Override
    public LinkLayerConnection getLinkLayerConnection() {
        return con;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return con.getLinkLayerIdentifier();
    }

    @Override
    public void cancelWorker() {
        FirechatBTState connectionState = ((FirechatProtocol)protocol).getBTState(((BluetoothConnection) con).getRemoteLinkLayerAddress());
        if(working) {
            Log.d(TAG, "[!] should not call cancelWorker() on a working Worker, call stopWorker() instead !");
            stopWorker();
        } else
            connectionState.notConnected();
    }

    @Override
    public void startWorker() {
        if(working)
            return;
        working = true;

        EventBus.getDefault().register(this);

        FirechatBTState connectionState = ((FirechatProtocol)protocol).getBTState(((BluetoothConnection)con).getRemoteLinkLayerAddress());

        try {
            con.connect();

            // little hack to force connection
            ((BluetoothConnection)con).getOutputStream().write(("{}").getBytes());

            connectionState.connected(this.getWorkerIdentifier());
            EventBus.getDefault().post(new ChannelConnected(
                            new BluetoothNeighbour(((BluetoothConnection)con).getRemoteLinkLayerAddress()),
                            this)
            );

            onChannelConnected();

            connectionState.notConnected();
            EventBus.getDefault().post(new ChannelDisconnected(
                            new BluetoothNeighbour(((BluetoothConnection)con).getRemoteLinkLayerAddress()),
                            this,
                            error)
            );
        } catch (IOException exception) {
            Log.d(TAG, "[!] FAILED CON: "+ exception.getMessage());
        }
        catch (FirechatBTState.StateException ignore) {
            Log.e(TAG, "[!] FAILED STATE: " + getWorkerIdentifier());
        } catch (LinkLayerConnectionException exception) {
            Log.d(TAG, "[!] FAILED CON: "+ exception.getMessage());
            connectionState.notConnected();
        } finally {
            stopWorker();
        }

    }

    /*
     * A Firechat message is a simple char stream representing a JSON file, ending with LF.
     * however, if a file is attached to the message (like a picture), the message is
     * followed by a binary stream representing the file.
     *
     *    We cannot use InputStream to read byte by byte with InputStream.read() trying to detect
     * the CRLF cause it would be way too inefficient
     *
     *    On the other hand, We cannot use a BufferedInputStream either because it is char only
     * and it will be unable to read the following binary stream. plus, it will also mess with
     * the original InputStream as BufferedInputStream reads ahead (more than necessary)
     *
     *    Solution is thus to use a PushedBackInputStream that will read a whole buffer (1024)
     * and then we will iterate through it until found a CRLF and will pushback (unread) whatever
     * follows for further binary reading.
     */
    @Override
    public void processingPacketFromNetwork() {
        try {
            final int CR = 13;
            final int LF = 10;
            pbin = new PushbackInputStream(((BluetoothConnection)con).getInputStream(), BUFFER_SIZE);

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
                        String jsonString  = new String(buffer, 0, i - 1);
                        JSONObject message = new JSONObject(jsonString);

                        ChatMessage status = parser.networkToChatMessage(message);
                        String filename = downloadFile(status.getFileSize());
                        if (filename != null) {
                            status.setAttachedFile(filename);
                        }

                        /*
                         * It is very important to post an event as it will be catch by the
                         * CacheManager and will update the database accordingly
                         */
                        EventBus.getDefault().post(new ChatMessageReceived(
                                        status,
                                        this)
                        );
                    } catch (JSONException ignore) {
                        Log.d(TAG, "malformed JSON");
                    } catch (IOException e) {
                        Log.e(TAG, "[!] Error while unread" + e.toString());
                    }
                }
            }
        } catch (IOException silentlyCloseConnection) {
            Log.d(TAG, silentlyCloseConnection.getMessage());
        } catch (InputOutputStreamException silentlyCloseConnection) {
            Log.d(TAG, silentlyCloseConnection.getMessage());
        }
    }


    public String downloadFile(long length) {
        if(length < 0)
            return null;
        File directory;
        try {
            directory = FileUtil.getWritableAlbumStorageDir();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            String suffix = ".jpg";
            File attachedFile = File.createTempFile(
                    imageFileName,  /* prefix */
                    suffix,         /* suffix */
                    directory       /* directory */
            );

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(attachedFile);
                int BUFFER_SIZE = 1024;
                byte[] buffer = new byte[BUFFER_SIZE];
                while (length > 0) {
                    long max_read = Math.min((long)BUFFER_SIZE,length);
                    int count = pbin.read(buffer, 0, (int)max_read);
                    if (count < 0)
                        throw new IOException("End of stream reached before downloading was complete");
                    length -= count;
                    fos.write(buffer, 0, count);
                }
            } finally {
                if(fos != null)
                    fos.close();
            }
            // add the photo to the media library
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(attachedFile);
            mediaScanIntent.setData(contentUri);
            RumbleApplication.getContext().sendBroadcast(mediaScanIntent);

            return attachedFile.getName();
        } catch (IOException e) {
            Log.e(TAG, "[-] file has not been downloaded ",e);
            return null;
        }
    }

    @Override
    protected boolean onCommandReceived(Command command) {
        if(command instanceof CommandSendPushStatus) {
            ChatMessage chatMessage = ((CommandSendChatMessage)command).getChatMessage();

            String jsonStatus = parser.chatMessageToNetwork(chatMessage);
            try {
                long timeToTransfer = System.currentTimeMillis();
                long bytesTransfered = jsonStatus.getBytes(Charset.forName("UTF-8")).length;
                ((BluetoothConnection)con).getOutputStream().write(jsonStatus.getBytes(Charset.forName("UTF-8")));

                if(chatMessage.hasAttachedFile()) {
                    File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(), chatMessage.getAttachedFile());
                    if(attachedFile.exists() && attachedFile.isFile()) {
                        FileInputStream fis = null;
                        try {
                            fis  = new FileInputStream(attachedFile);
                            byte[] buffer = new byte[BUFFER_SIZE];
                            int count;
                            while ((count = fis.read(buffer)) > 0) {
                                ((BluetoothConnection)con).getOutputStream().write(buffer, 0, count);
                                bytesTransfered += count;
                            }
                        } finally {
                            if(fis != null)
                                fis.close();
                        }
                    } else {
                        throw new IOException("File: "+ chatMessage.getAttachedFile()+" does not exists");
                    }
                }

                timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
                long throughput = (bytesTransfered / (timeToTransfer == 0 ? 1: timeToTransfer));
                List<String> recipients = new LinkedList<String>();
                recipients.add(((BluetoothConnection)con).getRemoteLinkLayerAddress());
                EventBus.getDefault().post(new ChatMessageSent(
                                chatMessage,
                                FirechatProtocol.protocolID,
                                BluetoothLinkLayerAdapter.LinkLayerIdentifier)
                );
            } catch(IOException ignore){
                Log.e(TAG, "[!] error while sending: "+ignore.getMessage());
            } catch (InputOutputStreamException e) {
                Log.e(TAG, e.getMessage());
            }
        }

        return true;
    }

    @Override
    public void stopWorker() {
        if(!working)
            return;
        this.working = false;
        EventBus.getDefault().unregister(this);
        try {
            con.disconnect();
        } catch (LinkLayerConnectionException ignore) {
        }
    }

    @Override
    public Set<Contact> getRecipientList() {
        Set<Contact> ret = new HashSet<Contact>(1);
        if(remoteContact != null)
            ret.add(remoteContact);
        return ret;
    }
    public void onEvent(ContactInformationReceived event) {
        if(event.channel.equals(this))
            this.remoteContact = event.contact;
    }

}
