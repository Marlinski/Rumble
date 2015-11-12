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

import android.os.Debug;
import android.util.Base64;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.events.FileReceived;
import org.disrupted.rumble.network.protocols.events.FileSent;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;
import org.disrupted.rumble.util.AESUtil;
import org.disrupted.rumble.util.FileUtil;

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
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;

import de.greenrobot.event.EventBus;

/**
 * A BlockFile is just a binary stream of size header.length
 *
 * +-------------------------------------------+
 * |            Attached Status                |  16 bytes
 * |                 UID                       |
 * +-----------+-------------------------------+
 * | Mime Type |                               |   1 byte + VARIABLE
 * +-----------+                               |
 * |                                           |
 * |                                           |
 * |                                           |
 * |       Binary File .....                   |
 * |                                           |
 * |                                           |
 * +-------------------------------------------+
 *
 * @author Marlinski
 */
public class BlockFile extends Block {

    public static final String TAG = "BlockFile";
    private static final int BUFFER_SIZE = 128*1024;

    /*
     * Byte size
     */
    private static final int FIELD_STATUS_ID_SIZE = PushStatus.STATUS_ID_RAW_SIZE;
    private static final int FIELD_MIME_TYPE_SIZE = 1;

    private  static final int MIN_PAYLOAD_SIZE = (
            FIELD_STATUS_ID_SIZE +
            FIELD_MIME_TYPE_SIZE);

    private  static final int MAX_PAYLOAD_SIZE = ( MIN_PAYLOAD_SIZE + PushStatus.STATUS_ATTACHED_FILE_MAX_SIZE);

    public final static int MIME_TYPE_IMAGE = 0x01;

    public  String filename;
    public  String status_id_base64;

    public BlockFile(BlockHeader header) {
        super(header);
        filename = "";
    }

    public BlockFile(String filename, String statud_id_base64) {
        super(new BlockHeader());
        header.setBlockType(BlockHeader.BLOCKTYPE_FILE);
        this.filename = filename;
        this.status_id_base64 = statud_id_base64;
    }

    public void sanityCheck() throws MalformedBlockPayload {
        if(header.getBlockType() != BlockHeader.BLOCKTYPE_FILE)
            throw new MalformedBlockPayload("Block type BLOCK_FILE expected", 0);
        if((header.getBlockLength() < MIN_PAYLOAD_SIZE) || (header.getBlockLength() > MAX_PAYLOAD_SIZE))
            throw new MalformedBlockPayload("wrong payload size: "+header.getBlockLength(), 0);
    }

    @Override
    public long readBlock(ProtocolChannel channel, InputStream in) throws MalformedBlockPayload, IOException, InputOutputStreamException {
        sanityCheck();

        long timeToTransfer = System.nanoTime();

        /* read the block pseudo header */
        long readleft = header.getBlockLength();
        byte[] pseudoHeaderBuffer = new byte[MIN_PAYLOAD_SIZE];
        int count = in.read(pseudoHeaderBuffer, 0, MIN_PAYLOAD_SIZE);
        if (count < 0)
            throw new IOException("end of stream reached");
        if (count < MIN_PAYLOAD_SIZE)
            throw new MalformedBlockPayload("read less bytes than expected: "+count, count);

        Log.d(TAG,"BlockFileHeader received ("+count+" bytes): "+new String(pseudoHeaderBuffer));

        /* process the block pseudo header */
        ByteBuffer byteBuffer = ByteBuffer.wrap(pseudoHeaderBuffer);
        byte[] uid = new byte[FIELD_STATUS_ID_SIZE];
        byteBuffer.get(uid, 0, FIELD_STATUS_ID_SIZE);
        readleft -= FIELD_STATUS_ID_SIZE;
        status_id_base64 = Base64.encodeToString(uid, 0, FIELD_STATUS_ID_SIZE, Base64.NO_WRAP);

        int mime = byteBuffer.get();
        readleft -= FIELD_MIME_TYPE_SIZE;

        // for now we only authorize image
        if ((mime == MIME_TYPE_IMAGE)) {
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
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while (readleft > 0) {
                        long max_read = Math.min((long) BUFFER_SIZE, readleft);
                        int bytesread = in.read(buffer, 0, (int) max_read);
                        if (bytesread < 0)
                            throw new IOException("End of stream reached before downloading was complete");
                        readleft -= bytesread;
                        fos.write(buffer, 0, bytesread);
                    }
                } finally {
                    if (fos != null)
                        fos.close();
                }

                filename = attachedFile.getName();
                Log.d(TAG,"FILE received ("+attachedFile.length()+" bytes): "+filename);

                timeToTransfer  = (System.nanoTime() - timeToTransfer);
                UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
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
                Log.e(TAG, "[-] file has not been downloaded " + e.getMessage());
                filename = "";
                return header.getBlockLength() - readleft;
            }
        } else {
            Log.d(TAG, "file type unknown; " + mime);
            byte[] buffer = new byte[BUFFER_SIZE];
            while (readleft > 0) {
                long max_read = Math.min((long) BUFFER_SIZE, readleft);
                int bytesread = in.read(buffer, 0, (int) max_read);
                if (bytesread < 0)
                    throw new IOException("End of stream reached before downloading was complete");
                readleft -= bytesread;
            }
            filename = "";
            return header.getBlockLength();
        }
    }

    @Override
    public long writeBlock(ProtocolChannel channel, OutputStream out) throws IOException, InputOutputStreamException {
        if(filename == null)
            throw new IOException("filename is null");

        File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(), filename);
        if(!attachedFile.exists() || !attachedFile.isFile())
            throw new IOException(filename+" is not a file or does not exists");

        long timeToTransfer = System.nanoTime();

        /* prepare the pseudo header */
        ByteBuffer pseudoHeaderBuffer = ByteBuffer.allocate(MIN_PAYLOAD_SIZE);
        byte[] status_id    = Base64.decode(status_id_base64, Base64.NO_WRAP);
        pseudoHeaderBuffer.put(status_id, 0, FIELD_STATUS_ID_SIZE);
        pseudoHeaderBuffer.put((byte) MIME_TYPE_IMAGE);

        /* send the header and the pseudo-header */
        long payloadSize = attachedFile.length();
        header.setPayloadLength(MIN_PAYLOAD_SIZE + payloadSize);
        header.writeBlockHeader(out);
        out.write(pseudoHeaderBuffer.array());

        Log.d(TAG, "BlockFileHeader sent (" + pseudoHeaderBuffer.array().length + " bytes): "
                + new String(pseudoHeaderBuffer.array()));

        /* sent the attached file */
        BufferedInputStream fis = null;
        long bytesSent = 0;
        try {
            byte[] fileBuffer = new byte[BUFFER_SIZE];
            fis = new BufferedInputStream(new FileInputStream(attachedFile));
            int bytesread = fis.read(fileBuffer, 0, BUFFER_SIZE);
            while (bytesread > 0) {
                out.write(fileBuffer, 0, bytesread);
                bytesSent+=bytesread;
                bytesread = fis.read(fileBuffer, 0, BUFFER_SIZE);
            }
        } finally {
            if (fis != null)
                fis.close();
        }

        Log.d(TAG,"FILE sent ("+bytesSent+" bytes): "+attachedFile.getName());

        timeToTransfer = (System.nanoTime() - timeToTransfer);
        List<String> recipients = new ArrayList<String>();
        UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
        recipients.add(con.getRemoteLinkLayerAddress());
        EventBus.getDefault().post(new FileSent(
                        filename,
                        recipients,
                        RumbleProtocol.protocolID,
                        channel.getLinkLayerIdentifier(),
                        header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH,
                        timeToTransfer)
        );

        return header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH;
    }

    @Override
    public void dismiss() {
    }
}
