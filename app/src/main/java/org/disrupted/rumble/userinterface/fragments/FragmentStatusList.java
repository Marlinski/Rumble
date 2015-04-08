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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.StatusDatabase;
import org.disrupted.rumble.database.SubscriptionDatabase;
import org.disrupted.rumble.database.events.StatusDeletedEvent;
import org.disrupted.rumble.userinterface.adapter.FilterListAdapter;
import org.disrupted.rumble.userinterface.adapter.StatusListAdapter;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.events.StatusInsertedEvent;
import org.disrupted.rumble.message.StatusMessage;

import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FragmentStatusList extends Fragment {

    private static final String TAG = "FragmentStatusList";

    private View mView;
    private StatusListAdapter statusListAdapter;
    private FilterListAdapter filterListAdapter;

    private ListView filters;
    private List<String> subscriptionList;
    private ProgressBar progressBar;

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

        // the filters
        filters = (ListView) (mView.findViewById(R.id.filter_list));
        filterListAdapter = new FilterListAdapter(getActivity());
        filters.setAdapter(filterListAdapter);
        filters.setClickable(false);
        subscriptionList = new ArrayList<String>();

        // progress bar
        progressBar = (ProgressBar) mView.findViewById(R.id.loadingStatusList);

        // the list of status
        ListView statusList = (ListView) mView.findViewById(R.id.status_list);
        statusListAdapter = new StatusListAdapter(getActivity(), this);
        statusListAdapter.registerDataSetObserver(new DataSetObserver() {
                                                      public void onChanged() {
                                                          FrameLayout progressBar = (FrameLayout) mView.findViewById(R.id.status_list_progressbar);
                                                          progressBar.setVisibility(View.GONE);
                                                      }
                                                  }
        );
        statusList.setAdapter(statusListAdapter);
        getStatuses();
        getSubscriptions();
        EventBus.getDefault().register(this);

        return mView;
    }

    @Override
    public void onDestroy() {
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

    // todo: only get last 50 statuses and request it as the user browse it
    public void getStatuses() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.VISIBLE);
                if (filterListAdapter.getCount() == 0) {
                    DatabaseFactory.getStatusDatabase(getActivity())
                            .getStatuses(onStatusesLoaded);
                } else {
                    DatabaseFactory.getStatusDatabase(getActivity())
                            .getFilteredStatuses(filterListAdapter.getFilterList(), onStatusesLoaded);
                }
            }
        });
    }
    StatusDatabase.StatusQueryCallback onStatusesLoaded = new StatusDatabase.StatusQueryCallback() {
        @Override
        public void onStatusQueryFinished(ArrayList<StatusMessage> array) {
            if (getActivity() == null)
                return;
            final ArrayList<StatusMessage> answer = array;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusListAdapter.swap(answer);
                    progressBar.setVisibility(View.GONE);
                    statusListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

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
            /*
            if(composeTextView.getText().toString().toLowerCase().contains(filter.toLowerCase()))
                composeTextView.setText(TextUtils.replace(composeTextView.getText(),
                        new String[] {" "+filter.toLowerCase()},
                        new CharSequence[]{""}));
                        */

            filterListAdapter.deleteFilter(filter);
            filterListAdapter.notifyDataSetChanged();
            getStatuses();
            if(filterListAdapter.getCount() == 0)
                filters.setVisibility(View.GONE);

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


    /*
     * Events that come from outside the activity
     */
    public void onEvent(StatusInsertedEvent event) {
        getStatuses();
    }
    public void onEvent(StatusDeletedEvent event) {
        getStatuses();
    }

}
