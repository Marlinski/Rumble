/*
 * Copyright (C) 2014 Disrupted Systems
 *
 * This file is part of Rumble.
 *
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
 * You should have received a copy of the GNU General Public License
 * along with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble;

import android.content.Intent;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.os.Bundle;

import android.view.View;

import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;

import org.disrupted.rumble.userinterface.fragments.FragmentDirectMessage;
import org.disrupted.rumble.userinterface.fragments.FragmentGroupStatus;
import org.disrupted.rumble.userinterface.fragments.FragmentNavigationDrawer;
import org.disrupted.rumble.userinterface.fragments.FragmentNetworkDrawer;
import org.disrupted.rumble.userinterface.fragments.FragmentStatusList;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConfigureInteraction;

/**
 * @author Marlinski
 */
public class HomeActivity extends ActionBarActivity {

    private static final String TAG = "MainActivity";
    private CharSequence mTitle;

    private ActionBar actionBar;
    private FragmentNavigationDrawer mNavigationDrawerFragment;
    private FragmentNetworkDrawer mNetworkDrawerFragment;
    private SlidingMenu slidingMenu;

    ActionBar.Tab publicStatus, groupStatus, tchatStatus;
    Fragment fragmentStatusList  = new FragmentStatusList();
    Fragment fragmentGroupStatus = new FragmentGroupStatus();
    Fragment fragmentTchat       = new FragmentDirectMessage();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.home_activity);
        mTitle = getTitle();
        actionBar = getSupportActionBar();

        slidingMenu = new SlidingMenu(this);
        slidingMenu.setShadowWidthRes(R.dimen.shadow_width);
        slidingMenu.setShadowDrawable(R.drawable.shadow);
        slidingMenu.setBehindOffsetRes(R.dimen.slidingmenu_offset);
        slidingMenu.setFadeDegree(0.35f);
        slidingMenu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
        slidingMenu.setMode(SlidingMenu.LEFT_RIGHT);
        slidingMenu.setMenu(R.layout.navigation_frame);
        slidingMenu.setSecondaryMenu(R.layout.network_frame);
        slidingMenu.setSecondaryShadowDrawable(R.drawable.shadowright);
        if (savedInstanceState == null) {
            mNavigationDrawerFragment = new FragmentNavigationDrawer();
            mNetworkDrawerFragment = new FragmentNetworkDrawer();
            this.getSupportFragmentManager().beginTransaction()
                .replace(R.id.navigation_drawer_frame, mNavigationDrawerFragment).commit();
            this.getSupportFragmentManager().beginTransaction()
                .replace(R.id.network_drawer_frame, mNetworkDrawerFragment).commit();
        } else {
            mNavigationDrawerFragment = (FragmentNavigationDrawer)this.getSupportFragmentManager().findFragmentById(R.id.navigation_drawer_frame);
            mNetworkDrawerFragment = (FragmentNetworkDrawer)this.getSupportFragmentManager().findFragmentById(R.id.network_drawer_frame);
        }
        slidingMenu.attachToActivity(this, SlidingMenu.SLIDING_WINDOW);


        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        publicStatus = actionBar.newTab().setIcon(R.drawable.ic_world)
                .setTabListener(new HomeTabListener(fragmentStatusList));
        groupStatus = actionBar.newTab().setIcon(R.drawable.ic_group_white_24dp)
                .setTabListener(new HomeTabListener(fragmentGroupStatus));
        tchatStatus = actionBar.newTab().setIcon(R.drawable.ic_forum_white_24dp)
                .setTabListener(new HomeTabListener(fragmentTchat));

        actionBar.addTab(publicStatus);
        actionBar.addTab(groupStatus);
        actionBar.addTab(tchatStatus);

        // hide the action bar
        actionBar.setHomeButtonEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);
    }


    public void onBluetoothToggleClicked(View view) {
        mNetworkDrawerFragment.onBluetoothToggleClicked(view);
    }
    public void onWifiToggleClicked(View view) {
        mNetworkDrawerFragment.onWifiToggleClicked(view);
    }
    public void onForceScanClicked(View view) {
        mNetworkDrawerFragment.onForceScanClicked(view);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case BluetoothConfigureInteraction.REQUEST_ENABLE_BT:
            case BluetoothConfigureInteraction.REQUEST_ENABLE_DISCOVERABLE:
                mNetworkDrawerFragment.manageBTCode(requestCode, resultCode, data);
                break;
        }
    }


    private class HomeTabListener implements ActionBar.TabListener {

        private Fragment fragment;

        public HomeTabListener(Fragment fragment) {
            this.fragment = fragment;
        }

        @Override
        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
            if(fragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.container, fragment)
                        .commit();
            }
        }

        @Override
        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            fragmentManager.beginTransaction()
                    .remove(fragment)
                    .commit();
        }

        @Override
        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        }
    }
}
