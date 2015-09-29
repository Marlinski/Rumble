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
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;

import org.disrupted.rumble.R;
import org.disrupted.rumble.userinterface.fragments.FragmentContactList;
import org.disrupted.rumble.userinterface.fragments.FragmentStatusList;

/**
 * @author Marlinski
 */
public class GroupDetailActivity extends AppCompatActivity {

    private static final String TAG = "GroupStatusActivity";

    private FloatingActionButton fab;
    private boolean message_has_focus;
    FragmentStatusList  statusFragment;
    FragmentContactList contactFragment;

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getIntent().getExtras();
        String name = args.getString("GroupName");

        setContentView(R.layout.fragment_group_detail);
        setTitle(name);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);


        /* create the two fragment that will be displayed on tab */
        statusFragment  = new FragmentStatusList();
        contactFragment = new FragmentContactList();
        statusFragment.setArguments(args);
        contactFragment.setArguments(args);

        /* populate the first tab */
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, statusFragment)
                .commit();
        message_has_focus = true;

        /* the floating action button */
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(((FragmentStatusList)statusFragment).onFabClicked);

        /* the tab layout */
        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.group_detail_tab_message));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.group_detail_tab_members));
        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                Fragment fragment = message_has_focus ? contactFragment : statusFragment;
                if (message_has_focus) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.container, fragment)
                            .commit();
                    fab.setVisibility(View.GONE);
                    message_has_focus = false;
                } else {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.container, fragment)
                            .commit();
                    fab.setVisibility(View.VISIBLE);
                    message_has_focus = true;
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                Fragment fragment = message_has_focus ? statusFragment : contactFragment;
                FragmentManager fragmentManager = getSupportFragmentManager();
                fragmentManager.beginTransaction()
                        .remove(fragment)
                        .commit();
            }
        });
        tabLayout.setSelectedTabIndicatorHeight(10);
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
