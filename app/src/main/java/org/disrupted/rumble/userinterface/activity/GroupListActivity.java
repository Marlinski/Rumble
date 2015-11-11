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

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.userinterface.events.UserJoinGroup;
import org.disrupted.rumble.userinterface.fragments.FragmentGroupList;
import org.disrupted.rumble.util.AESUtil;
import org.disrupted.rumble.util.HashUtil;

import java.nio.ByteBuffer;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class GroupListActivity extends AppCompatActivity {

    private static final String TAG = "GroupsActivity";

    CoordinatorLayout coordinatorLayout;

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_list);
        coordinatorLayout = (CoordinatorLayout)findViewById(R.id.coordinator_layout);

        /* setting up the toolbar */
        Toolbar toolbar = (Toolbar) findViewById(R.id.group_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        setTitle(R.string.navigation_drawer_group);

        /* setting up the list */
        Fragment fragmentGroupList = new FragmentGroupList();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragmentGroupList)
                .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.group_list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan_qrcode:
                IntentIntegrator.initiateScan(this);
                return true;
            case R.id.action_create_group:
                Intent create_group = new Intent(this, PopupCreateGroup.class );
                startActivity(create_group);
                return true;
            case android.R.id.home:
                finish();
                overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
        }
        return false;
    }


    /*
     * The QR-Code is a Base64 encoding of the following:
     *
     * +-------------------------------------------+
     * | Length |         group name               |  1 byte + group name
     * +--------+----------------------------------+
     * | Length |         Author (String)          |  1 byte + group ID
     * +-------------------------------------------+
     * |               Group Key                   |  group key
     * +--------+---------+------------------------+
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if ((result != null) && (result.getContents() != null)) {
            try {
                Log.d(TAG, result.getContents());
                byte[] resultbytes = Base64.decode(result.getContents().getBytes(), Base64.NO_WRAP);
                ByteBuffer byteBuffer = ByteBuffer.wrap(resultbytes);

                // extract group name
                int namesize = byteBuffer.get();
                if ((namesize < 0) || (namesize > Group.GROUP_NAME_MAX_SIZE))
                    throw new Exception();
                byte[] name = new byte[namesize];
                byteBuffer.get(name, 0, namesize);

                // extract group ID
                int gidsize = byteBuffer.get();
                if ((gidsize < 0) || (gidsize > HashUtil.expectedEncodedSize(Group.GROUP_GID_RAW_SIZE)))
                    throw new Exception();
                byte[] gid = new byte[gidsize];
                byteBuffer.get(gid, 0, gidsize);

                // extract group Key
                int keysize = (resultbytes.length - 2 - namesize - gidsize);
                if((keysize < 0) || (keysize > HashUtil.expectedEncodedSize(Group.GROUP_KEY_AES_SIZE)))
                    throw new Exception();
                byte[] key = new byte[keysize];
                byteBuffer.get(key, 0, keysize);
                Group group = new Group(new String(name), new String(gid), AESUtil.getSecretKeyFromByteArray(key));

                // add Group to database
                EventBus.getDefault().post(new UserJoinGroup(group));
                Snackbar.make(coordinatorLayout, "the group " + name + " has been added", Snackbar.LENGTH_SHORT)
                        .show();
            } catch (Exception e) {
                Snackbar.make(coordinatorLayout, "no group were added", Snackbar.LENGTH_SHORT)
                        .show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
    }

}
