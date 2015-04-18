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

package org.disrupted.rumble.userinterface.activity;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.PushStatusDatabase;
import org.disrupted.rumble.database.events.StatusDatabaseEvent;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.userinterface.events.UserJoinGroup;
import org.disrupted.rumble.userinterface.fragments.FragmentDirectMessage;
import org.disrupted.rumble.userinterface.fragments.FragmentNavigationDrawer;
import org.disrupted.rumble.userinterface.fragments.FragmentNetworkDrawer;
import org.disrupted.rumble.userinterface.fragments.FragmentStatusList;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConfigureInteraction;
import org.disrupted.rumble.util.AESUtil;

import java.nio.ByteBuffer;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class HomeActivity extends ActionBarActivity {

    private static final String TAG = "HomeActivity";
    private CharSequence mTitle;

    private ActionBar actionBar;
    private FragmentNavigationDrawer mNavigationDrawerFragment;
    private FragmentNetworkDrawer mNetworkDrawerFragment;
    public SlidingMenu slidingMenu;

    private Fragment fragmentStatusList = new FragmentStatusList();
    private Fragment fragmentTchat = new FragmentDirectMessage();
    private View notifPublic;
    private View notifChat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.fragment_activity);

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
        notifChat   = renderTabView(this, R.drawable.ic_forum_white_24dp);

        actionBar.addTab(actionBar.newTab()
                .setCustomView(notifPublic)
                .setTabListener(new HomeTabListener(fragmentStatusList)));
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

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            slidingMenu.toggle();
            return true;
        }
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            if(slidingMenu.isMenuShowing()) {
                slidingMenu.toggle();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
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

    /*
     * Receive QR CODE or Bluetooth Enable/Disable
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            byte[] resultbytes = Base64.decode(result.getContents().getBytes(),Base64.NO_WRAP);
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
        PushStatusDatabase.StatusQueryOption options = new PushStatusDatabase.StatusQueryOption();
        options.filterFlags = PushStatusDatabase.StatusQueryOption.FILTER_READ;
        options.read = false;
        options.query_result = PushStatusDatabase.StatusQueryOption.QUERY_RESULT.COUNT;
        DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).getStatuses(options, onRefreshPublic);
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
