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
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;

import android.support.v4.widget.DrawerLayout;
import android.view.View;

import org.disrupted.rumble.fragments.FragmentNavigationDrawer;
import org.disrupted.rumble.fragments.FragmentNetworkDrawer;
import org.disrupted.rumble.fragments.FragmentStatusList;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConfigureInteraction;

/**
 * @author Marlinski
 */
public class HomeActivity extends ActionBarActivity {

    private static final String TAG = "MainActivity";
    private CharSequence mTitle;

    private ActionBar actionBar;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private FragmentNavigationDrawer mNavigationDrawerFragment;
    private FragmentNetworkDrawer mNeighborhoodDrawerFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.home_activity);
        mTitle = getTitle();
        actionBar = getSupportActionBar();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                R.drawable.ic_drawer,
                R.string.drawer_open,
                R.string.drawer_close) {

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                invalidateOptionsMenu();
            }

            @Override
            public boolean onOptionsItemSelected(MenuItem item) {
                if(super.onOptionsItemSelected(item)) {
                    if(isRightDrawerOpen())
                        mDrawerLayout.closeDrawer(Gravity.END);
                    return true;
                }
                return false;
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        mNavigationDrawerFragment   = (FragmentNavigationDrawer)getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mNeighborhoodDrawerFragment = (FragmentNetworkDrawer)getSupportFragmentManager().findFragmentById(R.id.neighborhood_drawer);

        /*
        // increase the dragging area to swipe between the drawer
        // disabled because now we swipe between tab
        Field mLeftDragger  = null;
        Field mRightDragger = null;
        try {
            mLeftDragger  = mDrawerLayout.getClass().getDeclaredField("mLeftDragger");
            mRightDragger = mDrawerLayout.getClass().getDeclaredField("mRightDragger");
            mLeftDragger.setAccessible(true);
            mRightDragger.setAccessible(true);
            ViewDragHelper leftDraggerObj = (ViewDragHelper) mLeftDragger.get(mDrawerLayout);
            ViewDragHelper rightDraggerObj = (ViewDragHelper) mRightDragger.get(mDrawerLayout);
            Field mLeftEdgeSize = leftDraggerObj.getClass().getDeclaredField("mEdgeSize");
            Field mRightEdgeSize = rightDraggerObj.getClass().getDeclaredField("mEdgeSize");
            mLeftEdgeSize.setAccessible(true);
            mRightEdgeSize.setAccessible(true);
            mLeftEdgeSize.setInt(leftDraggerObj,  400);
            mRightEdgeSize.setInt(rightDraggerObj, 400);
        } catch (Exception e) {
        }
        */
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        restoreActionBar();
        return true;
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    public boolean isLeftDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(this.findViewById(R.id.navigation_drawer));
    }

    public boolean isRightDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(this.findViewById(R.id.neighborhood_drawer));
    }

    public void closeDrawer(){
        if(isLeftDrawerOpen()) {
            mDrawerLayout.closeDrawer(Gravity.START);
            return;
        }
        if(isRightDrawerOpen()) {
            mDrawerLayout.closeDrawer(Gravity.END);
            return;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    /*
     * User Interactions
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        if(mDrawerToggle.onOptionsItemSelected(item))
            return true;
        if(mNeighborhoodDrawerFragment.onOptionsItemSelected(item))
            return true;
        if(mNavigationDrawerFragment.onOptionsItemSelected(item))
            return true;

        switch(item.getItemId()) {
            case R.id.action_network:
                if(isLeftDrawerOpen())
                    mDrawerLayout.closeDrawer(Gravity.START);
                if(isRightDrawerOpen())
                    mDrawerLayout.closeDrawer(Gravity.END);
                else
                    mDrawerLayout.openDrawer(Gravity.END);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onBluetoothToggleClicked(View view) {
        mNeighborhoodDrawerFragment.onBluetoothToggleClicked(view);
    }
    public void onWifiToggleClicked(View view) {
        mNeighborhoodDrawerFragment.onWifiToggleClicked(view);
    }
    public void onForceScanClicked(View view) {
        mNeighborhoodDrawerFragment.onForceScanClicked(view);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FragmentStatusList.REQUEST_IMAGE_CAPTURE:
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container);
                if(fragment != null) {
                    fragment.onActivityResult(requestCode, resultCode, data);
                }
                break;
            case BluetoothConfigureInteraction.REQUEST_ENABLE_BT:
            case BluetoothConfigureInteraction.REQUEST_ENABLE_DISCOVERABLE:
                mNeighborhoodDrawerFragment.manageBTCode(requestCode, resultCode, data);
                break;
        }
    }


}
