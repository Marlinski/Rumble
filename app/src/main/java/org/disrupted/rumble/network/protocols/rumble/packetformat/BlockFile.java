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

import android.util.Base64;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
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
import java.util.Arrays;
import java.util.Date;

import javax.crypto.SecretKey;

/**
 * A BlockFile is just a binary stream of size header.length
 *
 * +-------------------------------------------+
 * |            Attached Status                |  16 bytes
 * |                 UID                       |
 * +-------------------------------------------+
 * |           Initialisation                  |  16 bytes Initialisation Vector
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

        // for now we only authorize image
        int mime = byteBuffer.get();
        readleft -= FIELD_MIME_TYPE_SIZE;

        Log.d(TAG, "uid: " + status_id_base64
                + " iv: " + Base64.encodeToString(iv, 0, FIELD_AES_IV_SIZE, Base64.NO_WRAP)
                + " sum: "+sum
                + " mime: "+mime);

        CONSUME_FILE:
        {
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

                    Log.d(TAG,"creating temp file: "+attachedFile.getName());
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(attachedFile);
                        final int BUFFER_SIZE = 2048;
                        byte[] buffer = new byte[BUFFER_SIZE];
                        while (readleft > 0) {
                            long max_read = Math.min((long) BUFFER_SIZE, readleft);
                            int bytesread = in.read(buffer, 0, (int) max_read);
                            if (bytesread < 0)
                                throw new IOException("End of stream reached before downloading was complete");
                            readleft -= bytesread;

                            if (sum == 0) {
                                // the IV is null so the file is not encrypted
                                fos.write(buffer, 0, bytesread);
                            } else {
                                if (this.key == null) {
                                    Log.d(TAG,"the key wasn't set, we look for it");
                                    // if the key wasn't set, we get the key from the status group
                                    PushStatus status = DatabaseFactory
                                            .getPushStatusDatabase(RumbleApplication.getContext())
                                            .getStatus(status_id_base64);
                                    if(status == null)
                                        break CONSUME_FILE;
                                    this.key = status.getGroup().getGroupKey();
                                    if(this.key == null)
                                        break CONSUME_FILE;
                                }
                                Log.d(TAG,"the key is found");

                                try {
                                    byte[] decrypted = AESUtil.decryptBlock(buffer, key, iv);
                                    fos.write(decrypted, 0, decrypted.length);
                                } catch (Exception e) {
                                    // error while decrypting the file ?!
                                    Log.d(TAG,"encryption failed");
                                    if (fos != null)
                                        fos.close();
                                    attachedFile.delete();
                                    break CONSUME_FILE;
                                }
                            }
                        }
                    } finally {
                        if (fos != null)
                            fos.close();
                    }
                    Log.d(TAG,"filename downloaded");
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
        Log.d(TAG,"consumming what's left: "+readleft);
        int BUFFER_SIZE = 2048;
        byte[] buffer = new byte[BUFFER_SIZE];
        while (readleft > 0) {
            long max_read = Math.min((long)BUFFER_SIZE,readleft);
            int bytesread = in.read(buffer, 0, (int)max_read);
            if (bytesread < 0)
                throw new IOException("End of stream reached before downloading was complete");
            readleft -= bytesread;
        }
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

        /* prepare the iv */
        byte[] iv;
        long payloadSize;
        if(this.key != null) {
            try {
                iv = AESUtil.generateRandomIV();
                payloadSize = AESUtil.expectedEncryptedSize(attachedFile.length());
            } catch(Exception e) {
                // somehow encryption didn't work.
                return 0;
            }
        } else {
            iv = new byte[FIELD_AES_IV_SIZE];
            Arrays.fill(iv, (byte) 0);
            payloadSize = attachedFile.length();
        }
        header.setPayloadLength(PSEUDO_HEADER_SIZE+payloadSize);

        /* prepare the pseudo header */
        ByteBuffer pseudoHeaderBuffer = ByteBuffer.allocate(PSEUDO_HEADER_SIZE);
        byte[] status_id    = Base64.decode(status_id_base64, Base64.NO_WRAP);
        pseudoHeaderBuffer.put(status_id, 0, FIELD_STATUS_ID_SIZE);
        pseudoHeaderBuffer.put(iv, 0, FIELD_AES_IV_SIZE);
        pseudoHeaderBuffer.put((byte) MIME_TYPE_IMAGE);

        Log.d(TAG, "uid: " + status_id_base64 + " iv: "
                + Base64.encodeToString(iv, 0, FIELD_AES_IV_SIZE, Base64.NO_WRAP));

        /* send the header, the pseudo-header and the attached file */
        header.writeBlockHeader(con.getOutputStream());
        con.getOutputStream().write(pseudoHeaderBuffer.array());
        BufferedInputStream fis = null;
        try {
            OutputStream out = con.getOutputStream();
            final int BUFFER_SIZE = 2048;
            byte[] fileBuffer = new byte[BUFFER_SIZE];
            fis = new BufferedInputStream(new FileInputStream(attachedFile));
            int bytesread = fis.read(fileBuffer, 0, BUFFER_SIZE);
            while (bytesread > 0) {
                if(this.key == null) {
                    out.write(fileBuffer, 0, bytesread);
                } else {
                    try {
                        byte[] encryptedBuffer = AESUtil.encryptBlock(fileBuffer, this.key, iv);
                        out.write(encryptedBuffer, 0, encryptedBuffer.length);
                    } catch (Exception e) {
                        // somehow encryption didn't work
                        this.key = null;
                        return 0;
                    }
                }
                bytesread = fis.read(fileBuffer, 0, BUFFER_SIZE);
            }
        } finally {
            if (fis != null)
                fis.close();
        }

        /*
        timeToTransfer = (System.nanoTime() - timeToTransfer);
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
        */

        return header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH;
    }

    @Override
    public void dismiss() {
    }
}
