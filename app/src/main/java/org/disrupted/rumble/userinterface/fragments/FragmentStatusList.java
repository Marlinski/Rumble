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

import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.disrupted.rumble.HomeActivity;
import org.disrupted.rumble.R;
import org.disrupted.rumble.database.GroupDatabase;
import org.disrupted.rumble.database.StatusDatabase;
import org.disrupted.rumble.database.SubscriptionDatabase;
import org.disrupted.rumble.database.events.StatusDatabaseEvent;
import org.disrupted.rumble.database.events.StatusDeletedEvent;
import org.disrupted.rumble.database.events.StatusUpdatedEvent;
import org.disrupted.rumble.userinterface.activity.PopupCompose;
import org.disrupted.rumble.userinterface.adapter.FilterListAdapter;
import org.disrupted.rumble.userinterface.adapter.StatusListAdapter;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.events.StatusInsertedEvent;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.userinterface.events.UserComposeStatus;

import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FragmentStatusList extends Fragment implements SwipeRefreshLayout.OnRefreshListener{

    private static final String TAG = "FragmentStatusList";

    private View mView;
    private ListView statusList;
    private SwipeRefreshLayout swipeLayout;
    private StatusListAdapter statusListAdapter;
    private FilterListAdapter filterListAdapter;

    private ListView filters;
    private List<String> subscriptionList;

    private int notif;

    public interface OnFilterClick {
        public void onClick(String filter);
    }
    public interface OnSubscriptionClick {
        public void onClick(String filter);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        mView = inflater.inflate(R.layout.status_list, container, false);
        notif = 0;

        // the filters
        filters = (ListView) (mView.findViewById(R.id.filter_list));
        filterListAdapter = new FilterListAdapter(getActivity());
        filters.setAdapter(filterListAdapter);
        filters.setClickable(false);
        subscriptionList = new ArrayList<String>();

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

        getSubscriptions();
        getStatuses();
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
        inflater.inflate(R.menu.public_message_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search_public:
                //do something
                return true;
            case R.id.action_compose:
                Intent compose = new Intent(getActivity(), PopupCompose.class );
                startActivity(compose);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void getStatuses() {
            StatusDatabase.StatusQueryOption options = new StatusDatabase.StatusQueryOption();
            options.groupName = GroupDatabase.DEFAULT_GROUP;
            options.answerLimit = 20;
            options.query_result = StatusDatabase.StatusQueryOption.QUERY_RESULT.LIST_OF_MESSAGE;
            options.order_by = StatusDatabase.StatusQueryOption.ORDER_BY.TIME_OF_ARRIVAL;

            if (filterListAdapter.getCount() == 0) {
                DatabaseFactory.getStatusDatabase(getActivity())
                        .getStatuses(options, onStatusesLoaded);
            } else {
                options.filterFlags |= StatusDatabase.StatusQueryOption.FILTER_TAG;
                options.hashtagFilters = filterListAdapter.getFilterList();
                DatabaseFactory.getStatusDatabase(getActivity())
                        .getStatuses(options, onStatusesLoaded);
            }
    }
    DatabaseExecutor.ReadableQueryCallback onStatusesLoaded = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(final Object result) {
            if (getActivity() == null)
                return;
            final ArrayList<StatusMessage> answer = (ArrayList<StatusMessage>)result;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusListAdapter.swap(answer);
                    statusListAdapter.notifyDataSetChanged();
                    swipeLayout.setRefreshing(false);
                    ((HomeActivity)getActivity()).refreshNotifications();
                }
            });
        }
    };

    @Override
    public void onRefresh() {
        swipeLayout.setRefreshing(true);
        getStatuses();
    }

    /*
     * Hashtag List and subscription
     */

    public void getSubscriptions() {
        DatabaseFactory.getSubscriptionDatabase(getActivity())
                .getLocalUserSubscriptions(onSubscriptionsLoaded);

    }
    SubscriptionDatabase.SubscriptionsQueryCallback onSubscriptionsLoaded = new SubscriptionDatabase.SubscriptionsQueryCallback() {
        @Override
        public void onSubscriptionsQueryFinished(ArrayList<String> answer) {
            if (getActivity() == null)
                return;
            final ArrayList<String> subscriptions = answer;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    subscriptionList.clear();
                    subscriptionList = subscriptions;
                    filterListAdapter.swapSubscriptions(subscriptions);
                    filterListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    OnFilterClick onFilterClick = new OnFilterClick() {
        @Override
        public void onClick(String filter) {
            filterListAdapter.deleteFilter(filter);
            filterListAdapter.notifyDataSetChanged();
            if(filterListAdapter.getCount() == 0)
                filters.setVisibility(View.GONE);
            getStatuses();
        }
    };


    // todo should have its own object
    OnSubscriptionClick onSubscriptionClick = new OnSubscriptionClick() {
        @Override
        public void onClick(String filter) {
            if(subscriptionList.contains(filter.toLowerCase()))
                DatabaseFactory.getSubscriptionDatabase(getActivity()).unsubscribeLocalUser(filter,onSubscribed);
            else
                DatabaseFactory.getSubscriptionDatabase(getActivity()).subscribeLocalUser(filter,onSubscribed);
        }
    };
    DatabaseExecutor.WritableQueryCallback onSubscribed = new DatabaseExecutor.WritableQueryCallback() {
        @Override
        public void onWritableQueryFinished(boolean success) {
            if(success)
                getSubscriptions();
        }
    };

    public void addFilter(String filter) {
        if(filterListAdapter.getCount() == 0)
            filters.setVisibility(View.VISIBLE);

        FilterListAdapter.FilterEntry entry = new FilterListAdapter.FilterEntry();
        entry.filter = filter;
        entry.filterClick = onFilterClick;
        entry.subscriptionClick = onSubscriptionClick;

        if(filterListAdapter.addFilter(entry)) {
            filterListAdapter.notifyDataSetChanged();
            getStatuses();
        }
    }

    public void onEvent(UserComposeStatus event) {
        getStatuses();
        /*
        final StatusMessage message = event.status;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusListAdapter.addStatus(message);
                statusListAdapter.notifyDataSetChanged();
            }
        });
        */
    }
    public void onEvent(StatusDeletedEvent event) {
        getStatuses();
        /*
        final String uuid = event.uuid;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(statusListAdapter.deleteStatus(uuid))
                    statusListAdapter.notifyDataSetChanged();
            }
        });
        */
    }
    public void onEvent(StatusUpdatedEvent event) {
        getStatuses();
        /*
        final StatusMessage message = event.status;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(statusListAdapter.updateStatus(message))
                    statusListAdapter.notifyDataSetChanged();
            }
        });
        */
    }
}
