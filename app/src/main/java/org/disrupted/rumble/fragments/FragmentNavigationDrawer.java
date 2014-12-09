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

package org.disrupted.rumble.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.disrupted.rumble.HomeActivity;
import org.disrupted.rumble.R;
import org.disrupted.rumble.adapter.NavigationItem;
import org.disrupted.rumble.adapter.NavigationItemListAdapter;

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
        mDrawerFragmentLayout = (LinearLayout) inflater.inflate(R.layout.navigation_drawer, container, false);

        mFirstListView   = (ListView) mDrawerFragmentLayout.findViewById(R.id.navigation_first_item_list);
        firstList = new LinkedList<NavigationItem>();
        //firstList.add(new NavigationItem(R.drawable.ic_blur_on_white_24dp, "Public Messages", 1));
        firstList.add(new NavigationItem(R.drawable.ic_world, "Public Messages", 1));
        firstList.add(new NavigationItem(R.drawable.ic_favorite_outline_white_24dp, "Favorites", 2));

        mFirstListAdapter = new NavigationItemListAdapter(getActivity(), firstList);
        mFirstListView.setAdapter(mFirstListAdapter);
        mFirstListView.setOnItemClickListener(new NavigationItemClickListener());

        Fragment fragment = new FragmentStatusList();
        if(fragment != null) {
            FragmentManager fragmentManager = getFragmentManager();
            fragmentManager.beginTransaction()
                    .replace(R.id.container, fragment)
                    .commit();
        }

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
            if(!mFirstListAdapter.setChecked(position))
                return;

            Fragment fragment = null;

            switch(firstList.get(position).getID()) {
                case 1:
                    fragment = new FragmentStatusList();
                    break;
                case 2:
                    fragment = new FragmentFavoriteList();
                default:
            }

            if(fragment != null) {
                FragmentManager fragmentManager = getFragmentManager();
                fragmentManager.beginTransaction()
                        .replace(R.id.container, fragment)
                        .commit();
                ((HomeActivity) getActivity()).closeDrawer();
            }
        }
    }
}
