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

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.userinterface.adapter.IconTextItem;
import org.disrupted.rumble.userinterface.adapter.IconTextListAdapter;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public class Settings extends ActionBarActivity implements ListView.OnItemClickListener {

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
        setContentView(R.layout.settings);
        setTitle("Settings");

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);

        settingsList = new LinkedList<IconTextItem>();
        settingsList.add(new IconTextItem(
                R.drawable.ic_delete_white_24dp,
                getResources().getString(R.string.settings_action_delete),
                1));

        listAdapter = new IconTextListAdapter(this, settingsList);
        settingsListView = (ListView) findViewById(R.id.settings_list);
        settingsListView.setAdapter(listAdapter);
        settingsListView.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView parent, View view, int position, long id) {
        switch(settingsList.get(position).getID()) {
            case 1:
                DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).clearStatus(null);
            default:
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();
        if (id==android.R.id.home) {
            finish();
            //overridePendingTransition(0, R.anim.right_slide_out);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
        //overridePendingTransition(0, R.anim.right_slide_out);
    }
}
