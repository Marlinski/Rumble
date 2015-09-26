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
import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;

import org.disrupted.rumble.userinterface.activity.HomeActivity;
import org.disrupted.rumble.R;
import org.disrupted.rumble.database.PushStatusDatabase;
import org.disrupted.rumble.database.events.ContactTagInterestUpdatedEvent;
import org.disrupted.rumble.database.events.GroupDeletedEvent;
import org.disrupted.rumble.database.events.StatusDeletedEvent;
import org.disrupted.rumble.database.events.StatusUpdatedEvent;
import org.disrupted.rumble.database.events.StatusWipedEvent;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.userinterface.activity.PopupComposeStatus;
import org.disrupted.rumble.userinterface.adapter.FilterListAdapter;
import org.disrupted.rumble.userinterface.adapter.StatusListAdapter;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
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
    private ListView statusList;
    private SwipeRefreshLayout swipeLayout;
    private StatusListAdapter  statusListAdapter;
    private FilterListAdapter  filterListAdapter;
    private ListView filters;

    private String   filter_gid = null;
    private String   filter_uid = null;

    public interface OnFilterClick {
        public void onClick(String entry);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        Bundle args = getArguments();
        if(args != null) {
            this.filter_gid = args.getString("GroupID");
            this.filter_uid = args.getString("ContactID");
        }

        mView = inflater.inflate(R.layout.status_list, container, false);

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
        statusList = (ListView) mView.findViewById(R.id.status_list);
        statusListAdapter = new StatusListAdapter(getActivity(), this);
        statusListAdapter.registerDataSetObserver(new DataSetObserver() {
                  public void onChanged() {
                      FrameLayout progressBar = (FrameLayout) mView.findViewById(R.id.status_list_progressbar);
                      progressBar.setVisibility(View.GONE);
                  }
              }
        );
        statusList.setAdapter(statusListAdapter);

        refreshStatuses();
        EventBus.getDefault().register(this);

        return mView;
    }

    @Override
    public void onDestroy() {
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
        statusListAdapter.clean();
        super.onDestroy();
    }

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
                    statusListAdapter.swap(answer);
                    statusListAdapter.notifyDataSetChanged();
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
                statusListAdapter.addStatus(message);
                statusListAdapter.notifyDataSetChanged();
            }
        });

    }
    public void onEvent(StatusDeletedEvent event) {
        final String uuid = event.uuid;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(statusListAdapter.deleteStatus(uuid))
                    statusListAdapter.notifyDataSetChanged();
            }
        });
    }
    public void onEvent(StatusUpdatedEvent event) {
        final PushStatus message = event.status;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(statusListAdapter.updateStatus(message))
                    statusListAdapter.notifyDataSetChanged();
            }
        });
    }
    public void onEvent(ContactTagInterestUpdatedEvent event) {
        if (event.contact.isLocal()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    filterListAdapter.notifyDataSetChanged();
                }
            });
        }
    }

}
