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

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.GroupDatabase;
import org.disrupted.rumble.database.StatusDatabase;
import org.disrupted.rumble.database.events.StatusDatabaseEvent;
import org.disrupted.rumble.database.events.StatusDeletedEvent;
import org.disrupted.rumble.database.events.StatusInsertedEvent;
import org.disrupted.rumble.database.events.StatusUpdatedEvent;
import org.disrupted.rumble.userinterface.fragments.FragmentDirectMessage;
import org.disrupted.rumble.userinterface.fragments.FragmentGroupStatus;
import org.disrupted.rumble.userinterface.fragments.FragmentNavigationDrawer;
import org.disrupted.rumble.userinterface.fragments.FragmentNetworkDrawer;
import org.disrupted.rumble.userinterface.fragments.FragmentStatusList;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConfigureInteraction;

import de.greenrobot.event.EventBus;

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

    private Fragment fragmentStatusList = new FragmentStatusList();
    private Fragment fragmentGroupStatus = new FragmentGroupStatus();
    private Fragment fragmentTchat = new FragmentDirectMessage();
    private View notifPublic;
    private View notifGroup;
    private View notifChat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.home_activity);

        mTitle = getTitle();
        actionBar = getSupportActionBar();

        /* sliding menu with both right and left drawer */
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
            mNavigationDrawerFragment = (FragmentNavigationDrawer) this.getSupportFragmentManager().findFragmentById(R.id.navigation_drawer_frame);
            mNetworkDrawerFragment = (FragmentNetworkDrawer) this.getSupportFragmentManager().findFragmentById(R.id.network_drawer_frame);
        }
        slidingMenu.attachToActivity(this, SlidingMenu.SLIDING_WINDOW);

        /* three tabs with notification icons */
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        notifPublic = renderTabView(this, R.drawable.ic_world);
        notifGroup  = renderTabView(this, R.drawable.ic_group_white_24dp);
        notifChat   = renderTabView(this, R.drawable.ic_forum_white_24dp);

        actionBar.addTab(actionBar.newTab()
                .setCustomView(notifPublic)
                .setTabListener(new HomeTabListener(fragmentStatusList)));
        actionBar.addTab(actionBar.newTab()
                .setCustomView(notifGroup)
                .setTabListener(new HomeTabListener(fragmentGroupStatus)));
        actionBar.addTab(actionBar.newTab()
                .setCustomView(notifChat)
                .setTabListener(new HomeTabListener(fragmentTchat)));

        // hide the action bar
        actionBar.setHomeButtonEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);

        // for notification
        refreshNotifications();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
        super.onDestroy();
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
            if (fragment != null) {
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

    public View renderTabView(Context context, int iconResource) {
        RelativeLayout view = (RelativeLayout) LayoutInflater.from(context).inflate(R.layout.badge_tab_layout, null);
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        ((ImageView)view.findViewById(R.id.tab_icon)).setImageResource(iconResource);
        ((TextView)view.findViewById(R.id.tab_badge)).setVisibility(View.INVISIBLE);
        return view;
    }

    public void refreshNotifications() {
        StatusDatabase.StatusQueryOption options = new StatusDatabase.StatusQueryOption();
        options.filterFlags = StatusDatabase.StatusQueryOption.FILTER_GROUP | StatusDatabase.StatusQueryOption.FILTER_READ;
        options.groupName = GroupDatabase.DEFAULT_GROUP;
        options.read = false;
        options.query_result = StatusDatabase.StatusQueryOption.QUERY_RESULT.COUNT;
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatuses(options, onRefreshPublic);
    }
    DatabaseExecutor.ReadableQueryCallback onRefreshPublic = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(Object object) {
            final Integer count = (Integer)object;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView view = (TextView)notifPublic.findViewById(R.id.tab_badge);
                    if (count > 0) {
                        view.setText(count.toString());
                        view.setVisibility(View.VISIBLE);
                    } else {
                        view.setVisibility(View.INVISIBLE);
                    }
                }
            });
        }
    };


    /*
     * Handling Events coming from outside the activity
     */
    public void onEvent(StatusDatabaseEvent event) {
        refreshNotifications();
    }

}
