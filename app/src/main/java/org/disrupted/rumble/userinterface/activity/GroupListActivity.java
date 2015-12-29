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

package org.disrupted.rumble.userinterface.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;

import org.disrupted.rumble.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.userinterface.events.UserJoinGroup;
import org.disrupted.rumble.userinterface.fragments.FragmentGroupList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.greenrobot.event.EventBus;
import info.vividcode.android.zxing.CaptureActivity;
import info.vividcode.android.zxing.CaptureActivityIntents;
import info.vividcode.android.zxing.CaptureResult;

/**
 * @author Lucien Loiseau
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
            case R.id.action_create_group:
                PopupMenu popup = new PopupMenu(this, findViewById(R.id.action_create_group));

                // reflection hack, maybe create a new class that inherits popupmenu instead ?
                try {
                    Field[] fields = popup.getClass().getDeclaredFields();
                    for (Field field : fields) {
                        if ("mPopup".equals(field.getName())) {
                            field.setAccessible(true);
                            Object menuPopupHelper = field.get(popup);
                            Class<?> classPopupHelper = Class.forName(menuPopupHelper
                                    .getClass().getName());
                            Method setForceIcons = classPopupHelper.getMethod(
                                    "setForceShowIcon", boolean.class);
                            setForceIcons.invoke(menuPopupHelper, true);
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                popup.getMenuInflater().inflate(R.menu.popup_add_group_options, popup.getMenu());
                popup.setOnMenuItemClickListener(addGroupPopupMenu);
                popup.show();
                return true;
            case android.R.id.home:
                finish();
                overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
        }
        return false;
    }

    PopupMenu.OnMenuItemClickListener addGroupPopupMenu = new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_scan_qrcode:
                    Intent captureIntent = new Intent(GroupListActivity.this, CaptureActivity.class);
                    CaptureActivityIntents.setPromptMessage(captureIntent, "Barcode scanning...");
                    startActivityForResult(captureIntent, 1);
                    return true;
                case R.id.action_create_group:
                    Intent create_group = new Intent(GroupListActivity.this, PopupCreateGroup.class);
                    startActivity(create_group);
                    return true;
                case R.id.action_input_key:
                    Intent input_group = new Intent(GroupListActivity.this, PopupInputGroupKey.class);
                    startActivity(input_group);
                    return true;
            }
            return false;
        }
    };




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
        if(data == null)
            return;
        CaptureResult result = CaptureResult.parseResultIntent(data);
        if ((result != null) && (result.getContents() != null)) {
                Log.d(TAG, result.getContents());
                String base64ID = new String(result.getContents().getBytes());
                Group group = Group.getGroupFromBase64ID(base64ID);
                if(group == null) {
                    Snackbar.make(coordinatorLayout, "no group were added", Snackbar.LENGTH_SHORT)
                            .show();
                } else {
                    // add Group to database
                    EventBus.getDefault().post(new UserJoinGroup(group));
                    Snackbar.make(coordinatorLayout, "the group " + group.getName() + " has been added", Snackbar.LENGTH_SHORT)
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
