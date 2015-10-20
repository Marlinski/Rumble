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

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.userinterface.adapter.IconTextItem;
import org.disrupted.rumble.userinterface.adapter.IconTextListAdapter;
import org.disrupted.rumble.userinterface.events.UserWipeChatMessages;
import org.disrupted.rumble.userinterface.events.UserWipeStatuses;

import java.util.LinkedList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class SettingsActivity extends AppCompatActivity implements ListView.OnItemClickListener {

    private static final String TAG = "Settings";

    private ListView settingsListView;
    private IconTextListAdapter listAdapter;
    List<IconTextItem> settingsList;

    @Override
    protected void onDestroy() {
        settingsList.clear();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle("Settings");

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);

        // memory cleaning actions
        settingsList = new LinkedList<IconTextItem>();
        settingsList.add(new IconTextItem(
                R.drawable.ic_delete_white_24dp,
                getResources().getString(R.string.settings_action_wipe_status),
                1));
        settingsList.add(new IconTextItem(
                R.drawable.ic_delete_white_24dp,
                getResources().getString(R.string.settings_action_wipe_chat),
                2));
        listAdapter = new IconTextListAdapter(this, settingsList);
        settingsListView = (ListView) findViewById(R.id.settings_list);
        settingsListView.setAdapter(listAdapter);
        settingsListView.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView parent, View view, int position, long id) {
        switch(settingsList.get(position).getID()) {
            case 1:
                EventBus.getDefault().post(new UserWipeStatuses());
            case 2:
                EventBus.getDefault().post(new UserWipeChatMessages());
            default:
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();
        if (id==android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
    }
}
