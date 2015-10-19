/*
 * Copyright (C) 2014 Disrupted Systems
 * This file is part of Rumble.
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
 * You should have received a copy of the GNU General Public License along
 * with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.network.protocols.rumble.packetformat;

import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.events.FileReceived;
import org.disrupted.rumble.network.protocols.events.FileSent;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;
import org.disrupted.rumble.util.FileUtil;
import org.disrupted.rumble.util.HashUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * A BlockFile is just a binary stream of size header.length
 *
 * +-------------------------------------------+
 * |                                           |
 * |            Attached Status  UID           |  16 bytes
 * |                                           |
 * |                                           |
 * +-----------+-------------------------------+
 * |   MIME    |                               |  1 byte
 * +-----------+                               |
 * |                                           |
 * |       Binary .....                        |
 * |                                           |
 * |                                           |
 * +-------------------------------------------+
 *
 * @author Marlinski
 */
public class BlockFile extends Block {

    public static final String TAG = "BlockFile";

    /*
     * Byte size
     */
    private static final int STATUS_ID_SIZE   = PushStatus.STATUS_ID_RAW_SIZE;
    private static final int MIME_TYPE_SIZE   = 1;

    private  static final int MIN_PAYLOAD_SIZE = ( STATUS_ID_SIZE + MIME_TYPE_SIZE);
    private  static final int MAX_PAYLOAD_SIZE = ( MIN_PAYLOAD_SIZE + PushStatus.STATUS_ATTACHED_FILE_MAX_SIZE);

    public final static int MIME_TYPE_IMAGE = 0x01;

    public String filename;
    public String statud_id_base64;

    public BlockFile(BlockHeader header) {
        super(header);
    }

    public BlockFile(String filename, String statud_id_base64) {
        super(new BlockHeader());
        header.setBlockType(BlockHeader.BLOCKTYPE_FILE);
        this.filename = filename;
        this.statud_id_base64 = statud_id_base64;
    }

    @Override
    public long readBlock(ProtocolChannel channel) throws MalformedBlockPayload, IOException, InputOutputStreamException {
        UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
        if(header.getBlockType() != BlockHeader.BLOCKTYPE_FILE)
            throw new MalformedBlockPayload("Block type BLOCK_FILE expected", 0);

        long readleft = header.getBlockLength();
        if((readleft < 0) || (readleft > MAX_PAYLOAD_SIZE))
            throw new MalformedBlockPayload("wrong payload size", readleft);

        long timeToTransfer = System.currentTimeMillis();

        /* read the block pseudo header */
        InputStream in = con.getInputStream();
        byte[] pseudoHeaderBuffer = new byte[MIN_PAYLOAD_SIZE];
        int count = in.read(pseudoHeaderBuffer, 0, MIN_PAYLOAD_SIZE);
        if (count < 0)
            throw new IOException("end of stream reached");
        if (count < MIN_PAYLOAD_SIZE)
            throw new MalformedBlockPayload("read less bytes than expected: "+count, count);

        ByteBuffer byteBuffer = ByteBuffer.wrap(pseudoHeaderBuffer);
        byte[] uid = new byte[STATUS_ID_SIZE];
        byteBuffer.get(uid, 0, STATUS_ID_SIZE);
        readleft -= STATUS_ID_SIZE;

        int mime = byteBuffer.get();
        readleft -= MIME_TYPE_SIZE;

        if((mime == MIME_TYPE_IMAGE)) {
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
                    final int BUFFER_SIZE = 1024;
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while (readleft > 0) {
                        long max_read = Math.min((long)BUFFER_SIZE,readleft);
                        int bytesread = in.read(buffer, 0, (int)max_read);
                        if (bytesread < 0)
                            throw new IOException("End of stream reached before downloading was complete");
                        readleft -= bytesread;
                        fos.write(buffer, 0, bytesread);
                    }
                } finally {
                    if (fos != null)
                        fos.close();
                }

                timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
                // update the database
                String status_id_base64 = Base64.encodeToString(uid,0,STATUS_ID_SIZE,Base64.NO_WRAP);
                EventBus.getDefault().post(new FileReceived(
                                attachedFile.getName(),
                                status_id_base64,
                                con.getRemoteLinkLayerAddress(),
                                RumbleProtocol.protocolID,
                                con.getLinkLayerIdentifier(),
                                header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH,
                                timeToTransfer)
                );

                return header.getBlockLength();
            } catch (IOException e) {
                Log.e(TAG, "[-] file has not been downloaded "+e.getMessage());
            }
        } else {
            int BUFFER_SIZE = 1024;
            byte[] buffer = new byte[BUFFER_SIZE];
            while (readleft > 0) {
                long max_read = Math.min((long)BUFFER_SIZE,readleft);
                int bytesread = in.read(buffer, 0, (int)max_read);
                if (bytesread < 0)
                    throw new IOException("End of stream reached before downloading was complete");
                readleft -= bytesread;
            }
        }
        return 0;
    }

    @Override
    public long writeBlock(ProtocolChannel channel) throws IOException, InputOutputStreamException {
        UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
        if(filename == null)
            throw new IOException("filename is null");

        byte[] status_id    = Base64.decode(statud_id_base64, Base64.NO_WRAP);

        File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(), filename);
        if(!attachedFile.exists() || !attachedFile.isFile())
            throw new IOException(filename+" is not a file or does not exists");

        long timeToTransfer = System.currentTimeMillis();

        /* calculate the total block size */
        long size = attachedFile.length();
        this.header.setPayloadLength(size + MIN_PAYLOAD_SIZE);

        ByteBuffer bufferBlockFilePseudoHeader = ByteBuffer.allocate(MIN_PAYLOAD_SIZE);
        bufferBlockFilePseudoHeader.put(status_id, 0, STATUS_ID_SIZE);
        bufferBlockFilePseudoHeader.put((byte) MIME_TYPE_IMAGE);

        /* send the header, the pseudo-header and the attached file */
        header.writeBlock(con.getOutputStream());
        con.getOutputStream().write(bufferBlockFilePseudoHeader.array());

        BufferedInputStream fis = null;
        try {
            OutputStream out = con.getOutputStream();
            final int BUFFER_SIZE = 1024;
            byte[] fileBuffer = new byte[BUFFER_SIZE];
            fis = new BufferedInputStream(new FileInputStream(attachedFile));
            int bytesread = fis.read(fileBuffer, 0, BUFFER_SIZE);
            while (bytesread > 0) {
                out.write(fileBuffer, 0, bytesread);
                bytesread = fis.read(fileBuffer, 0, BUFFER_SIZE);
            }
        } finally {
            if (fis != null)
                fis.close();
        }

        timeToTransfer = (System.currentTimeMillis() - timeToTransfer);
        List<String> recipients = new ArrayList<String>();
        recipients.add(con.getRemoteLinkLayerAddress());
        EventBus.getDefault().post(new FileSent(
                        filename,
                        recipients,
                        RumbleProtocol.protocolID,
                        BluetoothLinkLayerAdapter.LinkLayerIdentifier,
                        header.getBlockLength() + header.BLOCK_HEADER_LENGTH,
                        timeToTransfer)
        );

        return 0;
    }

    @Override
    public void dismiss() {

    }
}
