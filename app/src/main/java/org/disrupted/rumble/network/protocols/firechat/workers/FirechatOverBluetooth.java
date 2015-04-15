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

package org.disrupted.rumble.network.protocols.firechat.workers;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.events.NeighbourConnected;
import org.disrupted.rumble.network.events.NeighbourDisconnected;
import org.disrupted.rumble.network.events.StatusReceivedEvent;
import org.disrupted.rumble.network.events.StatusSentEvent;
import org.disrupted.rumble.database.objects.StatusMessage;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.protocols.ProtocolWorker;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.command.SendStatusMessageCommand;
import org.disrupted.rumble.network.protocols.firechat.FirechatBTState;
import org.disrupted.rumble.network.protocols.firechat.FirechatMessageParser;
import org.disrupted.rumble.network.protocols.firechat.FirechatNeighbour;
import org.disrupted.rumble.network.protocols.firechat.FirechatProtocol;
import org.disrupted.rumble.util.FileUtil;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FirechatOverBluetooth extends ProtocolWorker {

    private static final String TAG = "FirechatOverBluetooth";

    private FirechatProtocol protocol;
    private static final FirechatMessageParser parser = new FirechatMessageParser();
    private static final int BUFFER_SIZE = 1024;
    private PushbackInputStream pbin;
    private BluetoothConnection con;
    private boolean working;

    private BluetoothNeighbour bluetoothNeighbour;
    private FirechatNeighbour firechatNeighbour;

    public FirechatOverBluetooth(FirechatProtocol protocol, BluetoothConnection con) {
        this.protocol = protocol;
        this.con = con;
        this.working = false;
        bluetoothNeighbour = new BluetoothNeighbour(con.getRemoteLinkLayerAddress());
        firechatNeighbour  = new FirechatNeighbour(
                bluetoothNeighbour.getLinkLayerAddress(),
                bluetoothNeighbour.getBluetoothDeviceName(),
                bluetoothNeighbour);
    }

    public BluetoothNeighbour getBluetoothNeighbour() {
        return bluetoothNeighbour;
    }

    public FirechatNeighbour getFirechatNeighbour() {
        return firechatNeighbour;
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
        FirechatBTState connectionState = protocol.getBTState(con.getRemoteLinkLayerAddress());
        if(working) {
            Log.d(TAG, "[!] should not call cancelWorker() on a working Worker, call stopWorker() instead !");
            stopWorker();
            if (connectionState.getState().equals(FirechatBTState.FirechatBluetoothState.CONNECTED)) {
                connectionState.notConnected();
                EventBus.getDefault().post(new NeighbourDisconnected(
                                new BluetoothNeighbour(con.getRemoteLinkLayerAddress()),
                                getProtocolIdentifier())
                );
            } else {
                connectionState.notConnected();
            }
        } else
            connectionState.notConnected();
    }

    @Override
    public void startWorker() {
        if(working)
            return;
        working = true;

        FirechatBTState connectionState = protocol.getBTState(con.getRemoteLinkLayerAddress());

        try {
            con.connect();

            // little hack to force connection
            con.getOutputStream().write(("{}").getBytes());

            connectionState.connected(this.getWorkerIdentifier());
            EventBus.getDefault().post(new NeighbourConnected(
                            new BluetoothNeighbour(con.getRemoteLinkLayerAddress()),
                            getProtocolIdentifier())
            );

            onWorkerConnected();

            connectionState.notConnected();
            EventBus.getDefault().post(new NeighbourDisconnected(
                            new BluetoothNeighbour(con.getRemoteLinkLayerAddress()),
                            getProtocolIdentifier())
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
                        long timeToTransfer = System.currentTimeMillis();
                        String jsonString = new String(buffer, 0, i - 1);

                        StatusMessage status = parser.networkToStatus(jsonString);
                        String filename = downloadFile(status.getFileSize());
                        if (filename != null) {
                            status.setFileName(filename);
                        } else {
                            status.setFileName("");
                        }

                        /*
                         * It is very important to post an event as it will be catch by the
                         * CacheManager and will update the database accordingly
                         */
                        timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
                        EventBus.getDefault().post(new StatusReceivedEvent(
                                        status,
                                        con.getRemoteLinkLayerAddress(),
                                        FirechatProtocol.protocolID,
                                        BluetoothLinkLayerAdapter.LinkLayerIdentifier,
                                        status.getFileSize()+jsonString.length(),
                                        timeToTransfer)
                        );

                        Log.d(TAG, "Status received from Network:\n" + status.toString());

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
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
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
            } catch(IOException e) {
                throw  e;
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
            if(statusMessage.isForwarder(con.getRemoteLinkLayerAddress(), FirechatProtocol.protocolID))
                return false;

            String jsonStatus = parser.statusToNetwork(statusMessage);
            try {
                long timeToTransfer = System.currentTimeMillis();
                long bytesTransfered = jsonStatus.getBytes(Charset.forName("UTF-8")).length;
                con.getOutputStream().write(jsonStatus.getBytes(Charset.forName("UTF-8")));

                if(statusMessage.hasAttachedFile()) {
                    File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(),statusMessage.getFileName());
                    if(attachedFile.exists() && attachedFile.isFile()) {
                        FileInputStream fis = null;
                        try {
                            fis  = new FileInputStream(attachedFile);
                            byte[] buffer = new byte[BUFFER_SIZE];
                            int count;
                            while ((count = fis.read(buffer)) > 0) {
                                con.getOutputStream().write(buffer, 0, count);
                                bytesTransfered += count;
                            }
                        } finally {
                            if(fis != null)
                                fis.close();
                        }
                    } else {
                        throw new IOException("File: "+statusMessage.getFileName()+" does not exists");
                    }
                }

                timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
                long throughput = (bytesTransfered / (timeToTransfer == 0 ? 1: timeToTransfer));
                List<String> recipients = new LinkedList<String>();
                recipients.add(con.getRemoteLinkLayerAddress());
                EventBus.getDefault().post(new StatusSentEvent(
                        statusMessage,
                        recipients,
                        FirechatProtocol.protocolID,
                        BluetoothLinkLayerAdapter.LinkLayerIdentifier,
                        bytesTransfered,
                        timeToTransfer)
                );

                Log.d(TAG, "Status Sent ("+con.getRemoteLinkLayerAddress()+","+(throughput/1000L)+"): " + statusMessage.toString());
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
        try {
            con.disconnect();
        } catch (LinkLayerConnectionException ignore) {
        }
    }

}
