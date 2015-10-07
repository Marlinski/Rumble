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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.disrupted.rumble.database.events.ContactTagInterestUpdatedEvent;
import org.disrupted.rumble.userinterface.activity.HomeActivity;
import org.disrupted.rumble.R;
import org.disrupted.rumble.database.PushStatusDatabase;
import org.disrupted.rumble.database.events.GroupDeletedEvent;
import org.disrupted.rumble.database.events.StatusDeletedEvent;
import org.disrupted.rumble.database.events.StatusUpdatedEvent;
import org.disrupted.rumble.database.events.StatusWipedEvent;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.userinterface.activity.PopupComposeStatus;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.userinterface.adapter.FilterListAdapter;
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
    private FilterListAdapter filterListAdapter;
    private ListView filters;
    private FloatingActionButton composeFAB;
    private boolean noCoordinatorLayout;
    private boolean loadingMore;
    private boolean noMoreStatusToLoad;

    private String   filter_gid = null;
    private String   filter_uid = null;

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

        /*
         * This fragment is shown in three activities: the HomeActivity, the GroupDetail activity
         * and the ContactDetail activity. For HomeActivity and GroupDetail, I need the floating
         * action button to compose message and I need it to disappear when I scroll down so I need
         * this fragment to embeds it in a CoordinatorLayout to enable this effect.
         *
         * However for ContactDetail activity, I need a CoordinatorLayout for the whole activity
         * in order to hide the collapsingtoolbar whenever I scroll down. Unfortunately it conflicts
         * with the coordinatorlayout I use for this very fragmentStatusList. Because I don't need
         * the compose button to display the status of a specific contact, I created two different
         * layout to avoid conflicts and use the argument noCoordinatorLayout to decide which one.
         */
        if(noCoordinatorLayout) {
            mView = inflater.inflate(R.layout.fragment_status_list_no_coordinatorlayout, container, false);
        } else {
            mView = inflater.inflate(R.layout.fragment_status_list, container, false);
        }

        // the filters
        filters = (ListView) (mView.findViewById(R.id.filter_list));
        filterListAdapter = new FilterListAdapter(getActivity(), this);
        filters.setAdapter(filterListAdapter);
        filters.setClickable(false);
        filters.setVisibility(View.GONE);

        // refreshing the list of status by pulling down
        swipeLayout = (SwipeRefreshLayout) mView.findViewById(R.id.swipe_container);
        swipeLayout.setOnRefreshListener(this);

        /*
        final float density = getResources().getDisplayMetrics().density;
        final int swipeDistance = Math.round(64 * density);
        swipeLayout.setProgressViewOffset(true, 10, 10+swipeDistance);
        */

        // the list of status
        mRecyclerView = (RecyclerView) mView.findViewById(R.id.status_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        statusRecyclerAdapter = new StatusRecyclerAdapter(getActivity(), this);
        mRecyclerView.setAdapter(statusRecyclerAdapter);
        mRecyclerView.addOnScrollListener(loadMore);

        // the compose button
        composeFAB = (FloatingActionButton) mView.findViewById(R.id.compose_fab);
        composeFAB.setOnClickListener(onFabClicked);

        // now get the latest status
        loadingMore = false;
        noMoreStatusToLoad = false;
        refreshStatuses(-1,-1);

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
    public void onRefresh() {
        PushStatus status = statusRecyclerAdapter.getFirstItem();
        refreshStatuses(-1,-1);
    }

    public void refreshStatuses(long before_toa, long after_toa) {
        if(loadingMore)
            return;
        swipeLayout.setRefreshing(true);
        loadingMore = true;

        PushStatusDatabase.StatusQueryOption options = new PushStatusDatabase.StatusQueryOption();
        options.answerLimit = 10;
        options.query_result = PushStatusDatabase.StatusQueryOption.QUERY_RESULT.LIST_OF_MESSAGE;
        options.order_by = PushStatusDatabase.StatusQueryOption.ORDER_BY.TIME_OF_ARRIVAL;

        if(before_toa > 0) {
            options.filterFlags |= PushStatusDatabase.StatusQueryOption.FILTER_BEFORE_TOA;
            options.before_toa = before_toa;
        }
        if(after_toa > 0) {
            options.filterFlags |= PushStatusDatabase.StatusQueryOption.FILTER_AFTER_TOA;
            options.after_toa = after_toa;
        }
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
        if(before_toa <= 0) {
            DatabaseFactory.getPushStatusDatabase(getActivity())
                    .getStatuses(options, onStatusesRefreshed);
        } else {
            DatabaseFactory.getPushStatusDatabase(getActivity())
                    .getStatuses(options, onStatusesLoaded);
        }
    }
    DatabaseExecutor.ReadableQueryCallback onStatusesRefreshed = new DatabaseExecutor.ReadableQueryCallback() {
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
                    loadingMore = false;
                    noMoreStatusToLoad = false;

                    if (getActivity() != null) {
                        if(getActivity() instanceof HomeActivity)
                            ((HomeActivity) getActivity()).refreshChatNotifications();
                    }
                }
            });
        }
    };
    DatabaseExecutor.ReadableQueryCallback onStatusesLoaded = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(final Object result) {
            final ArrayList<PushStatus> answer = (ArrayList<PushStatus>)result;
            if (getActivity() == null)
                return;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(answer.size() > 0) {
                        statusRecyclerAdapter.addStatusAtBottom(answer);
                        statusRecyclerAdapter.notifyItemRangeInserted(
                                statusRecyclerAdapter.getItemCount()-answer.size(),
                                statusRecyclerAdapter.getItemCount()-1
                        );
                    } else {
                        noMoreStatusToLoad = true;
                    }
                    swipeLayout.setRefreshing(false);
                    loadingMore = false;
                }
            });
        }
    };

    /*
     * Endless scrolling. Whenever we reach the last item, we load for more
     */
    RecyclerView.OnScrollListener loadMore = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            LinearLayoutManager mLayoutManager = (LinearLayoutManager)recyclerView.getLayoutManager();
            int visibleItemCount = mLayoutManager.getChildCount();
            int totalItemCount = mLayoutManager.getItemCount();
            int pastVisiblesItems = mLayoutManager.findFirstVisibleItemPosition();
            if ( (visibleItemCount + pastVisiblesItems) >= totalItemCount) {
                if((!loadingMore) && (!noMoreStatusToLoad)) {
                    PushStatus status = statusRecyclerAdapter.getLastItem();
                    if(status == null)
                        return;
                    refreshStatuses(status.getTimeOfArrival(),-1);
                }
            }
        }
    };

    /*
     * Hashtag List
     */
    public void onFilterClickCallback(String filter) {
        filterListAdapter.deleteFilter(filter);
        if(filterListAdapter.getCount() == 0)
            filters.setVisibility(View.GONE);
        refreshStatuses(-1,-1);
    }
    public void addFilter(String filter) {
        if(filterListAdapter.getCount() == 0)
            filters.setVisibility(View.VISIBLE);

        if(filterListAdapter.addFilter(filter)) {
            refreshStatuses(-1,-1);
        }
    }

    /*
     * Status Events
     */
    public void onEvent(GroupDeletedEvent event) {
        refreshStatuses(-1,-1);
    }
    public void onEvent(StatusWipedEvent event) {
        refreshStatuses(-1,-1);
    }
    public void onEvent(UserComposeStatus event) {
        final PushStatus message = event.status;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusRecyclerAdapter.addStatusOnTop(message);
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
    public void onEvent(ContactTagInterestUpdatedEvent event) {
        if(event.contact.isLocal()) {
            filterListAdapter.notifyDataSetChanged();
        }
    }
}
