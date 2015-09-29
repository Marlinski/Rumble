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
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.disrupted.rumble.userinterface.activity.HomeActivity;
import org.disrupted.rumble.R;
import org.disrupted.rumble.database.PushStatusDatabase;
import org.disrupted.rumble.database.events.GroupDeletedEvent;
import org.disrupted.rumble.database.events.StatusDeletedEvent;
import org.disrupted.rumble.database.events.StatusUpdatedEvent;
import org.disrupted.rumble.database.events.StatusWipedEvent;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.userinterface.activity.PopupComposeStatus;
import org.disrupted.rumble.userinterface.adapter.FilterListAdapter;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.userinterface.adapter.StatusRecyclerAdapter;
import org.disrupted.rumble.userinterface.events.UserComposeStatus;

import java.util.ArrayList;
import java.util.HashSet;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FragmentStatusList extends Fragment implements SwipeRefreshLayout.OnRefreshListener{

    private static final String TAG = "FragmentStatusList";

    private View mView;
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout swipeLayout;
    private StatusRecyclerAdapter statusRecyclerAdapter;
    private FilterListAdapter  filterListAdapter;
    private ListView filters;
    private FloatingActionButton composeFAB;
    private boolean noCoordinatorLayout;

    private String   filter_gid = null;
    private String   filter_uid = null;

    public interface OnFilterClick {
        public void onClick(String entry);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        Bundle args = getArguments();
        if(args != null) {
            this.filter_gid = args.getString("GroupID");
            this.filter_uid = args.getString("ContactID");
            this.noCoordinatorLayout = args.getBoolean("noCoordinatorLayout");

        }

        if(noCoordinatorLayout) {
            mView = inflater.inflate(R.layout.fragment_status_list_no_coordinatorlayout, container, false);
        } else {
            mView = inflater.inflate(R.layout.fragment_status_list, container, false);
        }

        // the filters
        filters = (ListView) (mView.findViewById(R.id.filter_list));
        filterListAdapter = new FilterListAdapter(getActivity());
        filters.setAdapter(filterListAdapter);
        filters.setClickable(false);

        // the list of status
        swipeLayout = (SwipeRefreshLayout) mView.findViewById(R.id.swipe_container);
        swipeLayout.setOnRefreshListener(this);

        /*
        final float density = getResources().getDisplayMetrics().density;
        final int swipeDistance = Math.round(64 * density);
        swipeLayout.setProgressViewOffset(true, 10, 10+swipeDistance);
        */
        mRecyclerView = (RecyclerView) mView.findViewById(R.id.status_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        statusRecyclerAdapter = new StatusRecyclerAdapter(getActivity(), this);
        mRecyclerView.setAdapter(statusRecyclerAdapter);

        // the compose button
        composeFAB = (FloatingActionButton) mView.findViewById(R.id.compose_fab);
        composeFAB.setOnClickListener(onFabClicked);

        refreshStatuses();
        EventBus.getDefault().register(this);

        return mView;
    }

    @Override
    public void onDestroy() {
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
        statusRecyclerAdapter.clean();
        super.onDestroy();
    }

    public View.OnClickListener onFabClicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Intent compose = new Intent(getActivity(), PopupComposeStatus.class );
            if(filter_gid != null)
                compose.putExtra("GroupID",filter_gid);
            startActivity(compose);
        }
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if(filter_uid == null) {
            inflater.inflate(R.menu.public_message_menu, menu);
            super.onCreateOptionsMenu(menu, inflater);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search_public:
                //do something
                return true;
            case R.id.action_compose:
                Intent compose = new Intent(getActivity(), PopupComposeStatus.class );
                if(filter_gid != null)
                    compose.putExtra("GroupID",filter_gid);
                startActivity(compose);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void refreshStatuses() {
        PushStatusDatabase.StatusQueryOption options = new PushStatusDatabase.StatusQueryOption();
        options.answerLimit = 20;
        options.query_result = PushStatusDatabase.StatusQueryOption.QUERY_RESULT.LIST_OF_MESSAGE;
        options.order_by = PushStatusDatabase.StatusQueryOption.ORDER_BY.TIME_OF_ARRIVAL;

        if(filter_gid != null) {
            options.filterFlags |= PushStatusDatabase.StatusQueryOption.FILTER_GROUP;
            options.groupIDFilters = new HashSet<String>();
            options.groupIDFilters.add(filter_gid);
        }
        if(filter_uid != null) {
            options.filterFlags |= PushStatusDatabase.StatusQueryOption.FILTER_AUTHOR;
            options.uid = filter_uid;
        }
        if (filterListAdapter.getCount() != 0) {
            options.filterFlags |= PushStatusDatabase.StatusQueryOption.FILTER_TAG;
            options.hashtagFilters = filterListAdapter.getFilterList();
        }
        DatabaseFactory.getPushStatusDatabase(getActivity())
                .getStatuses(options, onStatusesLoaded);
    }
    DatabaseExecutor.ReadableQueryCallback onStatusesLoaded = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(final Object result) {
            final ArrayList<PushStatus> answer = (ArrayList<PushStatus>)result;
            if (getActivity() == null)
                return;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusRecyclerAdapter.swap(answer);
                    statusRecyclerAdapter.notifyDataSetChanged();
                    swipeLayout.setRefreshing(false);

                    if (getActivity() != null) {
                        if(getActivity() instanceof HomeActivity)
                            ((HomeActivity) getActivity()).refreshChatNotifications();
                    }
                }
            });
        }
    };

    @Override
    public void onRefresh() {
        swipeLayout.setRefreshing(true);
        refreshStatuses();
    }

    /*
     * Hashtag List
     */
    OnFilterClick onFilterClick = new OnFilterClick() {
        @Override
        public void onClick(String filter) {
            filterListAdapter.deleteFilter(filter);
            filterListAdapter.notifyDataSetChanged();
            if(filterListAdapter.getCount() == 0)
                filters.setVisibility(View.GONE);
            refreshStatuses();
        }
    };
    public void addFilter(String filter) {
        if(filterListAdapter.getCount() == 0)
            filters.setVisibility(View.VISIBLE);

        FilterListAdapter.FilterEntry entry = new FilterListAdapter.FilterEntry();
        entry.filter = filter;
        entry.filterClick = onFilterClick;

        if(filterListAdapter.addFilter(entry)) {
            filterListAdapter.notifyDataSetChanged();
            refreshStatuses();
        }
    }

    public void onEvent(GroupDeletedEvent event) {
        refreshStatuses();
    }
    public void onEvent(StatusWipedEvent event) {
        refreshStatuses();
    }
    public void onEvent(UserComposeStatus event) {
        final PushStatus message = event.status;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusRecyclerAdapter.addStatus(message);
                statusRecyclerAdapter.notifyItemInserted(0);
                mRecyclerView.smoothScrollToPosition(0);
            }
        });

    }
    public void onEvent(StatusDeletedEvent event) {
        final String uuid = event.uuid;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
            int pos = statusRecyclerAdapter.deleteStatus(uuid);
            if(pos > 0)
                statusRecyclerAdapter.notifyItemRemoved(pos);
            }
        });
    }
    public void onEvent(StatusUpdatedEvent event) {
        final PushStatus message = event.status;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //if(statusRecyclerAdapter.updateStatus(message))
                //    statusRecyclerAdapter.notifyDataSetChanged();
            }
        });
    }

}
