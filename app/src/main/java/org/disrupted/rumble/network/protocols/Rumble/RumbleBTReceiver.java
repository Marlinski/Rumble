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

package org.disrupted.rumble.network.protocols.Rumble;

import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConnection;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.BlockHeader;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.BlockHello;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.BlockStatus;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.exceptions.BufferMismatchBlockSize;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.exceptions.MalformedBlock;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.exceptions.MalformedRumblePacket;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.exceptions.SubtypeUnknown;

import java.io.IOException;

/**
 * @author Marlinski
 */
public class RumbleBTReceiver {

    private static final String TAG = "RumbleReceiver";

    private BluetoothConnection con;

    public RumbleBTReceiver(BluetoothConnection con) {
        this.con = con;
    }

    public void run() throws IOException{

        while (true) {
            byte[] buffer = new byte[BlockHeader.HEADER_LENGTH];
            try {
                /* receiving header */
                int count = con.getInputStream().read(buffer, 0, BlockHeader.HEADER_LENGTH);
                if (count < BlockHeader.HEADER_LENGTH)
                    throw new MalformedRumblePacket();
                BlockHeader header = new BlockHeader(buffer);
                header.readBuffer();

                /* receiving payload */
                byte[] payload;
                if(header.getBlockLength() > 0) {
                    payload = new byte[header.getBlockLength()];
                    count = con.getInputStream().read(payload, 0, header.getBlockLength());
                    if (count < header.getBlockLength())
                        throw new MalformedRumblePacket();
                }else {
                    payload = null;
                }

                /* processing block */
                switch (header.getSubtype()) {
                    case (BlockHello.SUBTYPE):
                        BlockHello blockHello = new BlockHello(header);
                        break;
                    case (BlockStatus.SUBTYPE):
                        BlockStatus blockStatus = new BlockStatus(header,payload);
                        StatusMessage statusMessage = blockStatus.getMessage();
                        statusMessage.addForwarder(con.getRemoteMacAddress(), "Rumble");
                        Log.d(TAG, "Received Rumble message: "+statusMessage.toString());
                        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).insertStatus(statusMessage, null);
                        break;
                    default:
                        throw new SubtypeUnknown(header.getSubtype());
                }

            } catch (MalformedRumblePacket e) {
                Log.d(TAG, "[!] malformed packet, received data are shorter than expected");
            } catch (BufferMismatchBlockSize e) {
                Log.d(TAG, "[!] buffer is too short, check protocol version");
            } catch (SubtypeUnknown e) {
                Log.d(TAG, "[!] packet subtype is unknown: " + e.subtype);
            } catch(MalformedBlock e) {
                Log.d(TAG, "[!] cannot get the message");
            }finally {
                buffer = null;
            }
        }
    }



}
