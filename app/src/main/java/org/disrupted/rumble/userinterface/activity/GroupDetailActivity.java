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

package org.disrupted.rumble.userinterface.activity;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;

import org.disrupted.rumble.userinterface.events.UserDeleteGroup;
import org.disrupted.rumble.userinterface.events.UserLeaveGroup;
import org.disrupted.rumble.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.userinterface.adapter.GroupDetailPagerAdapter;

import java.nio.ByteBuffer;
import java.util.Hashtable;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class GroupDetailActivity extends AppCompatActivity {

    private static final String TAG = "GroupStatusActivity";

    private Group group;
    private String groupName;
    private String groupID;

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getIntent().getExtras();
        groupName = args.getString("GroupName");
        groupID = args.getString("GroupID");

        group = DatabaseFactory.getGroupDatabase(this).getGroup(groupID);

        setContentView(R.layout.activity_group_detail);
        setTitle(groupName);

        /* setting up the toolbar */
        Toolbar toolbar = (Toolbar) findViewById(R.id.group_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        /* setting up the view pager and the tablayout */
        TabLayout tabLayout = (TabLayout) findViewById(R.id.group_tab_layout);
        ViewPager viewPager = (ViewPager) findViewById(R.id.group_viewpager);
        GroupDetailPagerAdapter pagerAdapter = new GroupDetailPagerAdapter(getSupportFragmentManager(), args);
        viewPager.setAdapter(pagerAdapter);
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.setSelectedTabIndicatorHeight(10);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.group_detail_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();
        if (id==R.id.group_action_invite) {
            invite();
        }
        if (id==R.id.group_action_delete) {
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        EventBus.getDefault().post(new UserDeleteGroup(groupID));
                        finish();
                        overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
                    }
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(GroupDetailActivity.this);
            builder.setMessage(R.string.group_confirm_delete).setPositiveButton("Yes", dialogClickListener)
                    .setNegativeButton("No", dialogClickListener).show();
        }
        if (id==R.id.group_action_leave) {
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        EventBus.getDefault().post(new UserLeaveGroup(groupID));
                        finish();
                        overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
                    }
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(GroupDetailActivity.this);
            if((group != null) && group.isPrivate())
                builder.setMessage(R.string.group_private_confirm_leave).setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();
            else
                builder.setMessage(R.string.group_confirm_leave).setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();
        }
        if (id==android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
    }

    public void invite() {
        ByteBuffer byteBuffer;
        byte[] keybytes;
        if(group.isPrivate())
            keybytes = group.getGroupKey().getEncoded();
        else
            keybytes = new byte[0];

        byteBuffer = ByteBuffer.allocate(2 + group.getName().length() + group.getGid().length() + keybytes.length);

        // send group name
        byteBuffer.put((byte) group.getName().length());
        byteBuffer.put(group.getName().getBytes(),0,group.getName().length());

        // send group ID
        byteBuffer.put((byte)group.getGid().length());
        byteBuffer.put(group.getGid().getBytes());

        // send key
        byteBuffer.put(keybytes);
        String buffer = Base64.encodeToString(byteBuffer.array(), Base64.NO_WRAP);

        try {
            IntentIntegrator.shareText(this, buffer);
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
                    Intent intent = new Intent(this, DisplayQRCode.class);
                    intent.putExtra("EXTRA_GROUP_NAME", groupName);
                    intent.putExtra("EXTRA_BUFFER", buffer);
                    intent.putExtra("EXTRA_QRCODE", image);
                    startActivity(intent);
                    overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_open_exit);
                }
            }catch(WriterException ignore) {
            }
        }
    }

}
