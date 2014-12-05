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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.adapter.FilterListAdapter;
import org.disrupted.rumble.adapter.StatusListAdapter;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
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
public class FragmentStatusList extends Fragment implements DatabaseExecutor.WritableQueryCallback, DatabaseExecutor.ReadableQueryCallback {

    private static final String TAG = "FragmentStatusList";

    public static final int REQUEST_IMAGE_CAPTURE = 42;

    private View mView;
    private StatusListAdapter statusListAdapter;
    private FilterListAdapter filterListAdapter;
    private TextView textView;
    private ImageButton sendButton;
    private GridView filters;
    private ProgressBar progressBar;
    private ImageButton plusButton;
    private LinearLayout attachedMenu;
    private boolean menuOpen;

    private Bitmap imageBitmap;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        mView = inflater.inflate(R.layout.status_list, container, false);

        // the filters
        filters = (GridView) (mView.findViewById(R.id.filter_list));
        filterListAdapter = new FilterListAdapter(getActivity());
        filters.setAdapter(filterListAdapter);

        // progress bar
        progressBar = (ProgressBar) mView.findViewById(R.id.loadingStatusList);
        filters.setOnItemClickListener(new OnFilterClick());

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
        getStatuses();
        statusList.setAdapter(statusListAdapter);
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
        this.textView = (TextView) mView.findViewById(R.id.user_status);

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

    @Override
    public void onWritableQueryFinished(boolean success) {
        getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            textView.setText("");
                                            sendButton.setImageResource(R.drawable.ic_send_black_36dp);
                                            sendButton.setClickable(true);
                                        }
                                    }
        );
    }

    public void getStatuses() {
        progressBar.setVisibility(View.VISIBLE);
        if (filterListAdapter.getCount() == 0) {
            DatabaseFactory.getStatusDatabase(getActivity())
                    .getStatuses(this);
        } else {
            DatabaseFactory.getStatusDatabase(getActivity())
                    .getFilteredStatuses(filterListAdapter.getFilterList(), this);
        }
    }

    @Override
    public void onReadableQueryFinished(Cursor answer) {
        if (getActivity() == null) {
            if (answer != null)
                answer.close();
            return;
        }
        statusListAdapter.swap(answer);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
                statusListAdapter.notifyDataSetChanged();
                filters.setClickable(true);
            }
        });
    }

    private class OnFilterClick implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            if(filterListAdapter.getCount() == 0)
                filters.setVisibility(View.GONE);
            filterListAdapter.deleteFilter((String) adapterView.getItemAtPosition(i));
            filterListAdapter.notifyDataSetChanged();
            filters.setClickable(false);
            getStatuses();
        }
    }

    public void addFilter(String filter) {
        if(filterListAdapter.getCount() == 0)
            filters.setVisibility(View.VISIBLE);
        filterListAdapter.addFilter(filter);
        filterListAdapter.notifyDataSetChanged();
        getStatuses();
    }


    private class OnClickSend implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            if (textView == null)
                return;

            String message = textView.getText().toString();
            if ((message == "") && (imageBitmap == null))
                return;


            StatusMessage statusMessage = new StatusMessage(message, "test");

            try {
                String filename = saveImageOnDisk();
                statusMessage.setFileName(filename);
            }
            catch (IOException ignore) {
            }

            sendButton.setClickable(false);
            sendButton.setImageResource(R.drawable.ic_send_grey600_36dp);
            DatabaseFactory.getStatusDatabase(getActivity()).insertStatus(statusMessage, FragmentStatusList.this);
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

    public void onEvent(NewStatusEvent status) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getStatuses();
            }
        });
    }

}
