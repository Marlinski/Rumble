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
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.userinterface.events.UserJoinGroup;
import org.disrupted.rumble.userinterface.fragments.FragmentGroupList;
import org.disrupted.rumble.util.AESUtil;

import java.nio.ByteBuffer;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class GroupsActivity extends ActionBarActivity {

    private static final String TAG = "GroupsActivity";


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_activity);
        setTitle(R.string.navigation_drawer_group);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);

        Fragment fragmentGroupList = new FragmentGroupList();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragmentGroupList)
                .commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            Log.d(TAG, result.getContents());
            byte[] resultbytes = Base64.decode(result.getContents().getBytes(), Base64.NO_WRAP);
            ByteBuffer byteBuffer = ByteBuffer.wrap(resultbytes);

            // extract group name
            int namesize = byteBuffer.get();
            byte[] name  = new byte[namesize];
            byteBuffer.get(name,0,namesize);

            // extract group ID
            int gidsize = byteBuffer.get();
            byte[] gid  = new byte[gidsize];
            byteBuffer.get(gid,0,gidsize);

            // extract group Key
            int keysize = resultbytes.length-2-namesize-gidsize;
            byte[] key = new byte[keysize];
            byteBuffer.get(key,0,keysize);
            Group group = new Group(new String(name), new String(gid), AESUtil.getSecretKeyFromByteArray(key));

            // add Group to database
            EventBus.getDefault().post(new UserJoinGroup(group));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();
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

}
