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

package org.disrupted.rumble.userinterface.fragments;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.userinterface.adapter.NavigationItem;
import org.disrupted.rumble.userinterface.adapter.NavigationItemListAdapter;
import org.disrupted.rumble.network.NetworkCoordinator;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public class FragmentNavigationDrawer extends Fragment {

    public static final String TAG = "DrawerNavigationFragment";

    private LinearLayout mDrawerFragmentLayout;

    private ListView mFirstListView;
    private NavigationItemListAdapter mFirstListAdapter;
    List<NavigationItem> firstList;

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mDrawerFragmentLayout = (LinearLayout) inflater.inflate(R.layout.fragment_navigation_drawer, container, false);

        Resources res = getActivity().getResources();
        mFirstListView   = (ListView) mDrawerFragmentLayout.findViewById(R.id.navigation_first_item_list);
        firstList = new LinkedList<NavigationItem>();
        firstList.add(new NavigationItem(R.drawable.ic_settings_applications_white_24dp, res.getString(R.string.navigation_drawer_settings), 1));
        firstList.add(new NavigationItem(R.drawable.ic_close_white_24dp, res.getString(R.string.navigation_drawer_exit), 2));

        mFirstListAdapter = new NavigationItemListAdapter(getActivity(), firstList);
        mFirstListView.setAdapter(mFirstListAdapter);
        mFirstListView.setOnItemClickListener(new NavigationItemClickListener());

        return mDrawerFragmentLayout;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private class NavigationItemClickListener implements ListView.OnItemClickListener {

        public NavigationItemClickListener() {
        }

        @Override
        public void onItemClick(AdapterView parent, View view, int position, long id) {
            switch(firstList.get(position).getID()) {
                case 2:
                    Intent stopIntent = new Intent(getActivity(), NetworkCoordinator.class);
                    stopIntent.setAction(NetworkCoordinator.ACTION_STOP_NETWORKING);
                    getActivity().stopService(stopIntent);
                    getActivity().finish();
                    System.exit(0);
                default:
            }
        }
    }
}
