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
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.events.GroupDeletedEvent;
import org.disrupted.rumble.database.events.GroupInsertedEvent;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.userinterface.activity.PopupCreateGroup;
import org.disrupted.rumble.userinterface.adapter.GroupListAdapter;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FragmentGroupList extends Fragment {

    public static final String TAG = "FragmentGroupList";

    private View mView;
    private ListView groupList;
    private GroupListAdapter groupListAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.fragment_group_list, container, false);
        groupList = (ListView) mView.findViewById(R.id.group_list);
        groupListAdapter = new GroupListAdapter(getActivity());
        groupList.setAdapter(groupListAdapter);
        EventBus.getDefault().register(this);
        getGroupList();
        return mView;
    }

    @Override
    public void onDestroy() {
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.group_list_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan_qrcode:
                IntentIntegrator.initiateScan(getActivity());
                return true;
            case R.id.action_create_group:
                Intent create_group = new Intent(getActivity(), PopupCreateGroup.class );
                startActivity(create_group);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void getGroupList() {
        DatabaseFactory.getGroupDatabase(getActivity()).getGroups(onGroupsLoaded);
    }
    private DatabaseExecutor.ReadableQueryCallback onGroupsLoaded = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(final Object result) {
            if(getActivity() == null)
                return;
            getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ArrayList<Group> answer = (ArrayList<Group>)(result);
                        groupListAdapter.swap(answer);
                    }
                }
            );
        }
    };

    public void onEvent(GroupInsertedEvent event) {
        getGroupList();
    }
    public void onEvent(GroupDeletedEvent event) {
        getGroupList();
    }
}
