/*
 * Copyright (C) 2014 Lucien Loiseau
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

import android.util.Base64;

import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;
import org.disrupted.rumble.util.EncryptedOutputStream;
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
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;


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
 * @author Lucien Loiseau
 */
public class BlockFile extends Block {

    public static final String TAG = "BlockFile";
    private static final int BUFFER_SIZE = 1024;


    /* Field Byte size */
    private static final int FIELD_STATUS_ID_SIZE = PushStatus.STATUS_ID_RAW_SIZE;
    private static final int FIELD_MIME_TYPE_SIZE = 1;

    /* Block Size Boundaries */
    private  static final int MIN_PAYLOAD_SIZE = (
            FIELD_STATUS_ID_SIZE +
            FIELD_MIME_TYPE_SIZE);
    private  static final int MAX_PAYLOAD_SIZE = ( MIN_PAYLOAD_SIZE + PushStatus.STATUS_ATTACHED_FILE_MAX_SIZE);

    /* Mime Types (so far we only authorize images) */
    public final static int MIME_TYPE_IMAGE = 0x01;

    /* Block Attributes */
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
    public long readBlock(InputStream in) throws MalformedBlockPayload, IOException, InputOutputStreamException {
        sanityCheck();

        /* read the block pseudo header */
        long readleft = header.getBlockLength();
        byte[] pseudoHeaderBuffer = new byte[MIN_PAYLOAD_SIZE];
        int count = in.read(pseudoHeaderBuffer, 0, MIN_PAYLOAD_SIZE);
        if (count < 0)
            throw new IOException("end of stream reached");
        if (count < MIN_PAYLOAD_SIZE)
            throw new MalformedBlockPayload("read less bytes than expected: "+count, count);

        BlockDebug.d(TAG,"BlockFileHeader received ("+count+" bytes): "+Arrays.toString(pseudoHeaderBuffer));

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
                if(directory == null)
                    throw new IOException();

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
                BlockDebug.d(TAG,"FILE received ("+attachedFile.length()+" bytes): "+filename);

                return header.getBlockLength();
            } catch (IOException e) {
                BlockDebug.e(TAG, "[-] file has not been downloaded",e);
                filename = "";
                return header.getBlockLength() - readleft;
            }
        } else {
            BlockDebug.d(TAG, "file type unknown; " + mime);
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
    public long writeBlock(OutputStream out, EncryptedOutputStream eos) throws IOException, InputOutputStreamException {
        if(filename == null)
            throw new IOException("filename is null");

        File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(), filename);
        if(!attachedFile.exists() || !attachedFile.isFile())
            throw new IOException(filename+" is not a file or does not exists");

        /* prepare the pseudo header */
        ByteBuffer pseudoHeaderBuffer = ByteBuffer.allocate(MIN_PAYLOAD_SIZE);
        byte[] status_id    = Base64.decode(status_id_base64, Base64.NO_WRAP);
        pseudoHeaderBuffer.put(status_id, 0, FIELD_STATUS_ID_SIZE);
        pseudoHeaderBuffer.put((byte) MIME_TYPE_IMAGE);

        /* send the header and the pseudo-header */
        long payloadSize = attachedFile.length();
        header.setPayloadLength(MIN_PAYLOAD_SIZE + payloadSize);
        header.writeBlockHeader(out);
        if(header.isEncrypted() && (eos != null))
            eos.write(pseudoHeaderBuffer.array());
        else
            out.write(pseudoHeaderBuffer.array());

        BlockDebug.d(TAG, "BlockFileHeader sent (" + pseudoHeaderBuffer.array().length + " bytes): "
                + Arrays.toString(pseudoHeaderBuffer.array()));

        /* sent the attached file */
        BufferedInputStream fis = null;
        long bytesSent = 0;
        try {
            byte[] fileBuffer = new byte[BUFFER_SIZE];
            fis = new BufferedInputStream(new FileInputStream(attachedFile));
            int bytesread = fis.read(fileBuffer, 0, BUFFER_SIZE);
            while (bytesread > 0) {
                if(header.isEncrypted() && (eos != null))
                    eos.write(fileBuffer, 0, bytesread);
                else
                    out.write(fileBuffer, 0, bytesread);
                bytesSent+=bytesread;
                bytesread = fis.read(fileBuffer, 0, BUFFER_SIZE);
            }
        } finally {
            if (fis != null)
                fis.close();
        }

        BlockDebug.d(TAG,"FILE sent ("+bytesSent+" bytes): "+attachedFile.getName());
        return header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH;
    }

    @Override
    public void dismiss() {
    }
}
