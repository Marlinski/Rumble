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
 * +-------------------------------------------+
 * |           Initialisation                  |  AESUtil.IVSIZE bytes Initialisation Vector
 * |                Vector                     |  (is 0 if file is not encrypted)
 * +-----------+-------------------------------+
 * | Mime Type |                                  1 byte
 * +-----------+-------------------------------+  +++++++++++++++++++ BEGIN ENCRYPTED BLOCK
 * |                                           |
 * |                                           |
 * |                                           |  VARIABLE
 * |       Binary File .....                   |
 * |                                           |
 * |                                           |
 * +-------------------------------------------+  +++++++++++++++++++ END ENCRYPTED BLOCK
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
    private static final int FIELD_AES_IV_SIZE    = AESUtil.IVSIZE;
    private static final int FIELD_MIME_TYPE_SIZE = 1;

    private  static final int PSEUDO_HEADER_SIZE = (
            FIELD_STATUS_ID_SIZE +
            FIELD_AES_IV_SIZE +
            FIELD_MIME_TYPE_SIZE);

    private  static final int MAX_PAYLOAD_SIZE = ( PSEUDO_HEADER_SIZE + PushStatus.STATUS_ATTACHED_FILE_MAX_SIZE);

    public final static int MIME_TYPE_IMAGE = 0x01;

    public  String filename;
    public  String status_id_base64;
    private SecretKey key;


    public BlockFile(BlockHeader header) {
        super(header);
        filename = "";
    }

    public BlockFile(String filename, String statud_id_base64) {
        super(new BlockHeader());
        header.setBlockType(BlockHeader.BLOCKTYPE_FILE);
        this.filename = filename;
        this.status_id_base64 = statud_id_base64;
        this.key = null;
    }

    public void setEncryptionKey(SecretKey key){
        this.key = key;
    }

    @Override
    public long readBlock(ProtocolChannel channel) throws MalformedBlockPayload, IOException, InputOutputStreamException {
        UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
        if(header.getBlockType() != BlockHeader.BLOCKTYPE_FILE)
            throw new MalformedBlockPayload("Block type BLOCK_FILE expected", 0);

        long readleft = header.getBlockLength();
        if((readleft < PSEUDO_HEADER_SIZE) || (readleft > MAX_PAYLOAD_SIZE))
            throw new MalformedBlockPayload("wrong payload size", readleft);

        long timeToTransfer = System.nanoTime();

        /* read the block pseudo header */
        InputStream in = con.getInputStream();
        byte[] pseudoHeaderBuffer = new byte[PSEUDO_HEADER_SIZE];
        int count = in.read(pseudoHeaderBuffer, 0, PSEUDO_HEADER_SIZE);
        if (count < 0)
            throw new IOException("end of stream reached");
        if (count < PSEUDO_HEADER_SIZE)
            throw new MalformedBlockPayload("read less bytes than expected: "+count, count);

        /* process the block pseudo header */
        ByteBuffer byteBuffer = ByteBuffer.wrap(pseudoHeaderBuffer);
        byte[] uid = new byte[FIELD_STATUS_ID_SIZE];
        byteBuffer.get(uid, 0, FIELD_STATUS_ID_SIZE);
        readleft -= FIELD_STATUS_ID_SIZE;
        status_id_base64 = Base64.encodeToString(uid, 0, FIELD_STATUS_ID_SIZE, Base64.NO_WRAP);

        byte[] iv = new byte[FIELD_AES_IV_SIZE];
        byteBuffer.get(iv, 0, FIELD_AES_IV_SIZE);
        readleft -= FIELD_AES_IV_SIZE;
        int sum = 0;
        for (byte b : iv) {sum |= b;}

        int mime = byteBuffer.get();
        readleft -= FIELD_MIME_TYPE_SIZE;

        CONSUME_FILE:
        {
            if ((sum != 0) && (this.key == null)) {
                // if the key wasn't set, we get the key from the status group
                PushStatus status = DatabaseFactory
                        .getPushStatusDatabase(RumbleApplication.getContext())
                        .getStatus(status_id_base64);
                if (status == null) // this status does not exists
                    break CONSUME_FILE;
                this.key = status.getGroup().getGroupKey();
                if (this.key == null) // the group is public so encryption is not needed
                    break CONSUME_FILE;
            }

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
                    CipherInputStream cis = null;
                    try {
                        fos = new FileOutputStream(attachedFile);
                        if(this.key != null)
                            cis = AESUtil.getCipherInputStream(in, this.key, iv);

                        byte[] buffer = new byte[BUFFER_SIZE];
                        while (readleft > 0) {
                            long max_read = Math.min((long) BUFFER_SIZE, readleft);
                            int bytesread = (this.key == null) ?
                                    in.read(buffer, 0, (int) max_read) :
                                    cis.read(buffer, 0, (int) max_read);
                            if (bytesread < 0)
                                throw new IOException("End of stream reached before downloading was complete");
                            readleft -= bytesread;
                            fos.write(buffer, 0, bytesread);
                        }
                    } catch(Exception e){
                        e.printStackTrace();
                        attachedFile.delete();
                        break CONSUME_FILE;
                    } finally {
                        if(cis != null)
                            cis.close();
                        if (fos != null)
                            fos.close();
                    }

                    filename = attachedFile.getName();

                    /*
                    timeToTransfer  = (System.nanoTime() - timeToTransfer);
                    EventBus.getDefault().post(new FileReceived(
                                    attachedFile.getName(),
                                    status_id_base64,
                                    con.getRemoteLinkLayerAddress(),
                                    RumbleProtocol.protocolID,
                                    con.getLinkLayerIdentifier(),
                                    header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH,
                                    timeToTransfer)
                    );
                    */

                    return header.getBlockLength();
                } catch (IOException e) {
                    Log.e(TAG, "[-] file has not been downloaded " + e.getMessage());
                }
            }
        }

        // consume what's left
        byte[] buffer = new byte[BUFFER_SIZE];
        while (readleft > 0) {
            long max_read = Math.min((long)BUFFER_SIZE,readleft);
            int bytesread = in.read(buffer, 0, (int)max_read);
            if (bytesread < 0)
                throw new IOException("End of stream reached before downloading was complete");
            readleft -= bytesread;
        }
        filename = "";
        return header.getBlockLength();
    }

    @Override
    public long writeBlock(ProtocolChannel channel) throws IOException, InputOutputStreamException {
        UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
        if(filename == null)
            throw new IOException("filename is null");

        File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(), filename);
        if(!attachedFile.exists() || !attachedFile.isFile())
            throw new IOException(filename+" is not a file or does not exists");

        long timeToTransfer = System.nanoTime();
        long bytesSent = 0;

        /* prepare the iv */
        byte[] iv;
        long payloadSize;
        if(this.key != null) {
            try {
                iv = AESUtil.generateRandomIV();
                payloadSize = AESUtil.expectedEncryptedSize(attachedFile.length());
            } catch(Exception e) {
                return 0;
            }
        } else {
            iv = new byte[FIELD_AES_IV_SIZE];
            Arrays.fill(iv, (byte) 0);
            payloadSize = attachedFile.length();
        }

        Log.d(TAG,"IV STEP: "+(System.nanoTime() - timeToTransfer));

        /* prepare the pseudo header */
        ByteBuffer pseudoHeaderBuffer = ByteBuffer.allocate(PSEUDO_HEADER_SIZE);
        byte[] status_id    = Base64.decode(status_id_base64, Base64.NO_WRAP);
        pseudoHeaderBuffer.put(status_id, 0, FIELD_STATUS_ID_SIZE);
        pseudoHeaderBuffer.put(iv, 0, FIELD_AES_IV_SIZE);
        pseudoHeaderBuffer.put((byte) MIME_TYPE_IMAGE);

        /* send the header and the pseudo-header */
        header.setPayloadLength(PSEUDO_HEADER_SIZE+payloadSize);
        header.writeBlockHeader(con.getOutputStream());
        con.getOutputStream().write(pseudoHeaderBuffer.array());
        bytesSent += FIELD_STATUS_ID_SIZE+FIELD_AES_IV_SIZE+1;

        /* sent the attached file, encrypted if key is not null */
        BufferedInputStream fis = null;
        CipherOutputStream cos = null;
        try {
            OutputStream out = con.getOutputStream();
            if(this.key != null)
                cos = AESUtil.getCipherOutputStream(out,this.key, iv);

            byte[] fileBuffer = new byte[BUFFER_SIZE];
            fis = new BufferedInputStream(new FileInputStream(attachedFile));
            int bytesread = fis.read(fileBuffer, 0, BUFFER_SIZE);
            while (bytesread > 0) {
                if (this.key == null)
                    out.write(fileBuffer, 0, bytesread);
                else
                    cos.write(fileBuffer, 0, bytesread);
                bytesSent += bytesread;
                bytesread = fis.read(fileBuffer, 0, BUFFER_SIZE);
            }
        } catch(Exception e) {
            e.printStackTrace();
            return 0;
        } finally {
            if(cos != null)
                cos.close();
            if (fis != null)
                fis.close();
        }

        Log.d(TAG,"SENT: "+(System.nanoTime() - timeToTransfer));

        timeToTransfer = (System.nanoTime() - timeToTransfer);
        List<String> recipients = new ArrayList<String>();
        recipients.add(con.getRemoteLinkLayerAddress());
        EventBus.getDefault().post(new FileSent(
                        filename,
                        recipients,
                        RumbleProtocol.protocolID,
                        channel.getLinkLayerIdentifier(),
                        bytesSent,
                        timeToTransfer)
        );

        return header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH;
    }

    @Override
    public void dismiss() {
    }
}
