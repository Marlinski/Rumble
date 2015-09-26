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

package org.disrupted.rumble.userinterface.adapter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.squareup.picasso.Picasso;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.userinterface.activity.DisplayQRCode;
import org.disrupted.rumble.userinterface.activity.GroupDetailActivity;
import org.disrupted.rumble.userinterface.events.UserDeleteGroup;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.crypto.SecretKey;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class GroupListAdapter extends BaseAdapter {

    public static final String TAG = "GroupListAdapter";

    private Activity activity;
    private LayoutInflater inflater;
    private ArrayList<Group> groupList;


    public GroupListAdapter(Activity activity) {
        this.activity = activity;
        this.inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.groupList = null;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        LinearLayout layout = (LinearLayout)inflater.inflate(R.layout.group_list_item, null, true);

        LinearLayout title     = (LinearLayout) layout.findViewById(R.id.group_title);
        ImageView group_lock   = (ImageView)    layout.findViewById(R.id.group_lock_image);
        TextView  group_name   = (TextView)     layout.findViewById(R.id.group_name);
        TextView  group_gid    = (TextView)     layout.findViewById(R.id.group_gid);
        TextView  group_desc   = (TextView)     layout.findViewById(R.id.group_desc);
        //TextView  unread_messages  = (TextView)  layout.findViewById(R.id.group_unread_messages);
        ImageView group_delete  = (ImageView)   layout.findViewById(R.id.group_delete);
        ImageView group_invite  = (ImageView)   layout.findViewById(R.id.group_invite);

        //group_name.setTextColor(ColorGenerator.DEFAULT.getColor(groupList.get(i).getName()));
        if(groupList.get(i).isIsprivate())
            Picasso.with(activity)
                    .load(R.drawable.ic_lock_grey600_24dp)
                    .into(group_lock);
        else
            Picasso.with(activity)
                    .load(R.drawable.ic_lock_open_grey600_24dp)
                    .into(group_lock);

        group_name.setText(groupList.get(i).getName());
        group_gid.setText("Group ID: "+groupList.get(i).getGid());
        if(groupList.get(i).getDesc().equals(""))
            group_desc.setVisibility(View.GONE);
        else
            group_desc.setText("Description: "+groupList.get(i).getDesc());


        /*
         * Manage click events
         */
        final String    gid          = groupList.get(i).getGid();
        final boolean   privateGroup = groupList.get(i).isIsprivate();
        final SecretKey key          = groupList.get(i).getGroupKey();
        final String    name         = groupList.get(i).getName();

        title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent groupStatusActivity = new Intent(activity, GroupDetailActivity.class );
                groupStatusActivity.putExtra("GroupID",gid);
                groupStatusActivity.putExtra("GroupName",name);
                activity.startActivity(groupStatusActivity);
                activity.overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_open_exit);
            }
        });

        group_invite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ByteBuffer byteBuffer;
                byte[] keybytes;
                if(privateGroup)
                    keybytes = key.getEncoded();
                else
                    keybytes = new byte[0];

                byteBuffer = ByteBuffer.allocate(2 + name.length() + gid.length() + keybytes.length);

                // send group name
                byteBuffer.put((byte)name.length());
                byteBuffer.put(name.getBytes(),0,name.length());

                // send group ID
                byteBuffer.put((byte)gid.length());
                byteBuffer.put(gid.getBytes());

                // send key
                byteBuffer.put(keybytes);
                String buffer = Base64.encodeToString(byteBuffer.array(),Base64.NO_WRAP);

                try {
                    IntentIntegrator.shareText(activity, buffer);
                } catch(ActivityNotFoundException notexists) {
                    Log.d(TAG, "Barcode scanner is not installed on this device");
                    int size = 200;
                    Hashtable<EncodeHintType, ErrorCorrectionLevel> hintMap = new Hashtable<EncodeHintType, ErrorCorrectionLevel>();
                    hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
                    QRCodeWriter qrCodeWriter = new QRCodeWriter();
                    try {
                        BitMatrix bitMatrix = qrCodeWriter.encode(buffer, BarcodeFormat.QR_CODE, size, size, hintMap);
                        Bitmap image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

                        if(image != null) {
                            for (int i = 0; i < size; i++) {
                                for (int j = 0; j < size; j++) {
                                    image.setPixel(i, j, bitMatrix.get(i, j) ? Color.BLACK : Color.WHITE);
                                }
                            }
                            Intent intent = new Intent(activity, DisplayQRCode.class);
                            intent.putExtra("EXTRA_GROUP_NAME", name);
                            intent.putExtra("EXTRA_BUFFER", buffer);
                            intent.putExtra("EXTRA_QRCODE", image);
                            activity.startActivity(intent);
                        }
                    }catch(WriterException ignore) {
                    }
                }

            }
        });

        group_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new UserDeleteGroup(gid));
            }
        });

        return layout;
    }

    @Override
    public long getItemId(int i) {
        if(groupList == null)
            return 0;
        if(groupList.isEmpty())
            return 0;
        return i;
    }

    @Override
    public Object getItem(int i) {
        return groupList.get(i);
    }

    @Override
    public int getCount() {
        if(groupList == null)
            return 0;
        else
            return groupList.size();
    }

    public void swap(ArrayList<Group> groupList) {
        if(this.groupList != null)
            this.groupList.clear();
        this.groupList = groupList;
        notifyDataSetChanged();
    }

}
