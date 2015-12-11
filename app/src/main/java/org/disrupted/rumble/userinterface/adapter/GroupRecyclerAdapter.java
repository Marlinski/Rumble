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

package org.disrupted.rumble.userinterface.adapter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import org.disrupted.rumble.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.squareup.picasso.Picasso;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.userinterface.activity.DisplayQRCode;
import org.disrupted.rumble.userinterface.activity.GroupDetailActivity;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.crypto.SecretKey;

/**
 * @author Lucien Loiseau
 */
public class GroupRecyclerAdapter extends RecyclerView.Adapter<GroupRecyclerAdapter.GroupHolder>  {

    public static final String TAG = "GroupListAdapter";

    public class GroupHolder extends RecyclerView.ViewHolder {

        LinearLayout title;
        ImageView group_lock;
        TextView  group_name;
        TextView  group_unread;
        TextView  group_desc;
        ImageView group_invite;

        public GroupHolder(View itemView) {
            super(itemView);

            title        = (LinearLayout) itemView.findViewById(R.id.group_title);
            group_lock   = (ImageView)    itemView.findViewById(R.id.group_lock_image);
            group_name   = (TextView)     itemView.findViewById(R.id.group_name);
            group_unread = (TextView)     itemView.findViewById(R.id.group_unread_msg);
            group_desc   = (TextView)     itemView.findViewById(R.id.group_desc);
            group_invite = (ImageView)   itemView.findViewById(R.id.group_invite);
        }

        public void bindGroup(Group group, int unread) {

            //group_name.setTextColor(ColorGenerator.DEFAULT.getColor(groupList.get(i).getName()));
            if(group.isPrivate())
                Picasso.with(activity)
                        .load(R.drawable.ic_lock_grey600_24dp)
                        .into(group_lock);
            else
                Picasso.with(activity)
                        .load(R.drawable.ic_lock_open_grey600_24dp)
                        .into(group_lock);

            group_name.setText(group.getName());
            group_unread.setText(""+unread);
            if(unread == 0) {
                group_unread.setVisibility(View.INVISIBLE);
            } else {
                group_unread.setVisibility(View.VISIBLE);
            }

            if(group.getDesc().equals(""))
                group_desc.setVisibility(View.GONE);
            else
                group_desc.setText("Description: "+group.getDesc());


            /*
             * Manage click events
             */
            final String    gid          = group.getGid();
            final boolean   privateGroup = group.isPrivate();
            final SecretKey key          = group.getGroupKey();
            final String    name         = group.getName();

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

                    //try {
                    //    IntentIntegrator intentIntegrator = new IntentIntegrator(activity);
                    //    intentIntegrator.shareText(buffer);
                    //} catch(ActivityNotFoundException notexists) {
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
                    //}

                }
            });


        }
    }

    private Activity activity;
    private ArrayList<Group> groupList;
    private ArrayList<Integer> unreadList;

    public GroupRecyclerAdapter(Activity activity) {
        this.activity = activity;
        this.groupList = null;
    }


    @Override
    public GroupHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LinearLayout layout = (LinearLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group_list, null, true);
        return new GroupHolder(layout);
    }

    @Override
    public void onBindViewHolder(GroupHolder groupHolder, int position) {
        Group contact  = groupList.get(position);
        Integer unread = unreadList.get(position);
        groupHolder.bindGroup(contact, unread);
    }

    @Override
    public int getItemCount() {
        if(groupList == null)
            return 0;
        else
            return groupList.size();
    }

    public void swap(ArrayList<Group> groupList) {
        if(this.groupList != null)
            this.groupList.clear();
        this.groupList  = groupList;
        this.unreadList = new ArrayList<>(groupList.size());
        for(int i = 0; i < groupList.size(); i++) {
            unreadList.add(0);
        }
        notifyDataSetChanged();
    }

    public void updateUnread(String gid, int unread) {
        for(int i = 0; i < groupList.size(); i++) {
            if(groupList.get(i).getGid().equals(gid)) {
                unreadList.set(i, unread);
                notifyItemChanged(i);
            }
        }
    }

}
