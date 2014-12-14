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

import android.content.Intent;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.adapter.FilterListAdapter;
import org.disrupted.rumble.adapter.StatusListAdapter;
import org.disrupted.rumble.contact.Contact;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.HashtagDatabase;
import org.disrupted.rumble.database.events.NewStatusEvent;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FragmentStatusList extends Fragment {

    private static final String TAG = "FragmentStatusList";

    public static final int REQUEST_IMAGE_CAPTURE = 42;

    private View mView;
    private StatusListAdapter statusListAdapter;
    private FilterListAdapter filterListAdapter;
    private TextView composeTextView;
    private ImageButton sendButton;

    private ListView filters;
    private List<String> subscriptionList;
    private ProgressBar progressBar;
    private ImageButton plusButton;
    private LinearLayout attachedMenu;
    private boolean menuOpen;
    private boolean loadingStatuses;
    private boolean loadingSubscriptions;

    private Bitmap imageBitmap;


    public interface OnFilterClick {
        public void onClick(String filter);
    }
    public interface OnSubscriptionClick {
        public void onClick(String filter);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        mView = inflater.inflate(R.layout.status_list, container, false);

        // the filters
        filters = (ListView) (mView.findViewById(R.id.filter_list));
        filterListAdapter = new FilterListAdapter(getActivity());
        filters.setAdapter(filterListAdapter);
        filters.setClickable(false);
        subscriptionList = new LinkedList<String>();

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

        // the additional menu to attach file
        plusButton = (ImageButton) mView.findViewById(R.id.attached_button);
        plusButton.setOnClickListener(new OnAttachedMenuClick());
        attachedMenu = (LinearLayout) mView.findViewById(R.id.attached_menu);
        menuOpen = false;
        ImageButton takePicture = (ImageButton) mView.findViewById(R.id.take_picture);
        takePicture.setOnClickListener(new OnTakePictureClick());
        ImageButton attachPicture = (ImageButton) mView.findViewById(R.id.attache_image);
        attachPicture.setOnClickListener(new OnAttachPictureClick());
        imageBitmap = null;

        // the text and button to submit
        sendButton = (ImageButton) mView.findViewById(R.id.button_send);
        sendButton.setOnClickListener(new OnClickSend());
        this.composeTextView = (TextView) mView.findViewById(R.id.user_status);

        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        statusListAdapter.clean();
        super.onDestroy();
    }


    public void getStatuses() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.VISIBLE);
                loadingStatuses = true;
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
    DatabaseExecutor.ReadableQueryCallback onStatusesLoaded = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(Cursor cursor) {
            final Cursor answer = cursor;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (getActivity() == null) {
                        if (answer != null)
                            answer.close();
                        return;
                    }
                    statusListAdapter.swap(answer);

                    progressBar.setVisibility(View.GONE);
                    statusListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    public void getSubscriptions() {
        loadingSubscriptions = true;
        DatabaseFactory.getSubscriptionDatabase(getActivity())
                .getLocalUserSubscriptions(onSubscriptionsLoaded);

    }
    DatabaseExecutor.ReadableQueryCallback onSubscriptionsLoaded = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(Cursor cursor) {
            final Cursor answer = cursor;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    loadingSubscriptions = false;
                    if (getActivity() == null) {
                        if (answer != null)
                            answer.close();
                        return;
                    }
                    List<String> subscriptions = new LinkedList<String>();
                    if (answer.moveToFirst()) {
                        do {
                            subscriptions.add(answer.getString(answer.getColumnIndexOrThrow(HashtagDatabase.HASHTAG)));
                        } while (answer.moveToNext());
                    }
                    answer.close();
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

            if(composeTextView.getText().toString().toLowerCase().contains(filter.toLowerCase()))
                composeTextView.setText(TextUtils.replace(composeTextView.getText(),
                        new String[] {" "+filter.toLowerCase()},
                        new CharSequence[]{""}));

            filterListAdapter.deleteFilter(filter);
            filterListAdapter.notifyDataSetChanged();
            getStatuses();
            if(filterListAdapter.getCount() == 0)
                filters.setVisibility(View.GONE);

        }
    };

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

        if(!composeTextView.getText().toString().toLowerCase().contains(filter.toLowerCase()))
            composeTextView.setText(composeTextView.getText().toString() +" "+filter.toLowerCase());

        FilterListAdapter.FilterEntry entry = new FilterListAdapter.FilterEntry();
        entry.filter = filter;
        entry.filterClick = onFilterClick;
        entry.subscriptionClick = onSubscriptionClick;

        if(filterListAdapter.addFilter(entry)) {
            filterListAdapter.notifyDataSetChanged();
            getStatuses();
        }
    }

    private class OnAttachedMenuClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            if(menuOpen) {
                attachedMenu.setVisibility(View.GONE);
                menuOpen = false;
            } else {
                attachedMenu.setVisibility(View.VISIBLE);
                menuOpen = true;
            }
        }
    }

    private class OnTakePictureClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                getActivity().startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == getActivity().RESULT_OK) {
            Bundle extras = data.getExtras();
            imageBitmap = (Bitmap) extras.get("data");
        }
    }

    //todo review this code, probably a bit dirty
    public String saveImageOnDisk() throws IOException{
        String filename = null;
        if(imageBitmap == null)
            throw new IOException();

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        filename = "JPEG_" + timeStamp + ".jpeg";
        File storageDir = FileUtil.getWritableAlbumStorageDir(imageBitmap.getByteCount());
        String path = storageDir.toString() + File.separator + filename;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (Exception e) {
            imageBitmap = null;
            return null;
        } finally {
            try {
                if (out != null)
                    out.close();
            } catch (IOException ignore) { }
        }
        imageBitmap = null;

        return filename;
    }

    private class OnAttachPictureClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
        }
    }



    private class OnClickSend implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            if (composeTextView == null)
                return;

            String message = composeTextView.getText().toString();
            if ((message == "") && (imageBitmap == null))
                return;

            Contact localContact = DatabaseFactory.getContactDatabase(getActivity()).getLocalContact();
            long now = (System.currentTimeMillis() / 1000L);
            StatusMessage statusMessage = new StatusMessage(message, localContact.getName(), now);

            try {
                String filename = saveImageOnDisk();
                statusMessage.setFileName(filename);
            }
            catch (IOException ignore) {
            }

            DatabaseFactory.getStatusDatabase(getActivity()).insertStatus(statusMessage, null);

            composeTextView.setText("");
            for(String filter : filterListAdapter.getFilterList()) {
                if(!composeTextView.getText().toString().toLowerCase().contains(filter.toLowerCase()))
                    composeTextView.setText(composeTextView.getText().toString() +" "+filter.toLowerCase());
            }

        }
    }

    /*
     * Events that come from outside the activity (like new status for instance)
     */
    public void onEvent(NewStatusEvent status) {
        getStatuses();
    }

}
