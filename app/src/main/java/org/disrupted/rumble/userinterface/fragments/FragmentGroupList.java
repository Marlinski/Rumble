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

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.PushStatusDatabase;
import org.disrupted.rumble.database.events.GroupDeletedEvent;
import org.disrupted.rumble.database.events.GroupInsertedEvent;
import org.disrupted.rumble.database.events.StatusInsertedEvent;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.network.protocols.events.PushStatusReceived;
import org.disrupted.rumble.userinterface.adapter.GroupRecyclerAdapter;

import java.util.ArrayList;
import java.util.HashSet;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FragmentGroupList extends Fragment {

    public static final String TAG = "FragmentGroupList";

    private View mView;
    private RecyclerView groupRecycler;
    private GroupRecyclerAdapter groupRecyclerAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        mView = inflater.inflate(R.layout.fragment_group_list, container, false);
        groupRecycler = (RecyclerView) mView.findViewById(R.id.group_list);
        groupRecycler.setLayoutManager(new LinearLayoutManager(getActivity()));

        groupRecyclerAdapter = new GroupRecyclerAdapter(getActivity());
        groupRecycler.setAdapter(groupRecyclerAdapter);
        EventBus.getDefault().register(this);
        getGroupList();
        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();
        getGroupList();
    }

    @Override
    public void onDestroy() {
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    public void getGroupList() {
        DatabaseFactory.getGroupDatabase(getActivity()).getGroups(onGroupsLoaded);
    }

    private DatabaseExecutor.ReadableQueryCallback onGroupsLoaded = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(final Object result) {
            if(getActivity() == null)
                return;
            Activity activity = FragmentGroupList.this.getActivity();
            if(activity != null) {
                activity.runOnUiThread(new Runnable() {
                                           @Override
                                           public void run() {
                                               ArrayList<Group> answer = (ArrayList<Group>) (result);
                                               groupRecyclerAdapter.swap(answer);
                                           }
                                       }
                );
            }

            // update the number of unread message for every group
            for(Group group : (ArrayList<Group>)(result)) {
                refreshBadge(group.getGid());
            }
        }
    };

    public void refreshBadge(String gid) {
        PushStatusDatabase.StatusQueryOption statusQueryOption = new PushStatusDatabase.StatusQueryOption();
        statusQueryOption.filterFlags = PushStatusDatabase.StatusQueryOption.FILTER_READ;
        statusQueryOption.read = false;
        statusQueryOption.filterFlags |= PushStatusDatabase.StatusQueryOption.FILTER_GROUP;
        statusQueryOption.groupIDFilters = new HashSet<>();
        statusQueryOption.groupIDFilters.add(gid);
        statusQueryOption.query_result = PushStatusDatabase.StatusQueryOption.QUERY_RESULT.COUNT;
        DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext())
                .getStatuses(statusQueryOption, new GroupUnreadCallback(gid));
    }
    private class GroupUnreadCallback implements DatabaseExecutor.ReadableQueryCallback {
        String gid;
        public GroupUnreadCallback(String gid) {
            this.gid = gid;
        }
        @Override
        public void onReadableQueryFinished(Object object) {
            final Integer count = (Integer)object;
            Activity activity = FragmentGroupList.this.getActivity();
            if(activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        groupRecyclerAdapter.updateUnread(gid, count);
                    }
                });
            }
        }
    }

    public void onEvent(StatusInsertedEvent event) {
        refreshBadge(event.status.getGroup().getGid());
    }
    public void onEvent(GroupInsertedEvent event) {
        getGroupList();
    }
    public void onEvent(GroupDeletedEvent event) {
        getGroupList();
    }
}
