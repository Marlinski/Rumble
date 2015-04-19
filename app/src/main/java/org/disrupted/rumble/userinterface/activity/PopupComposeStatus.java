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

package org.disrupted.rumble.userinterface.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.squareup.picasso.Picasso;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.userinterface.events.UserComposeStatus;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class PopupComposeStatus extends Activity {

    private static final String TAG = "PopupCompose";
    public static final int REQUEST_IMAGE_CAPTURE = 42;

    private LinearLayout dismiss;
    private EditText    compose;
    private ImageView   compose_background;
    private ImageButton takePicture;
    private ImageButton choosePicture;
    private ImageButton send;
    private Bitmap imageBitmap;
    private ImageView groupLock;

    private Spinner spinner;
    private GroupSpinnerAdapter spinnerArrayAdapter;

    private String mCurrentPhotoFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.popup_compose_status);

        imageBitmap = null;
        dismiss = (LinearLayout)(findViewById(R.id.popup_dismiss));
        compose = (EditText)(findViewById(R.id.popup_user_status));
        compose_background = (ImageView)(findViewById(R.id.popup_user_attached_photo));
        takePicture = (ImageButton)(findViewById(R.id.popup_take_picture));
        choosePicture = (ImageButton)(findViewById(R.id.popup_choose_image));
        send = (ImageButton)(findViewById(R.id.popup_button_send));
        spinner = (Spinner)(findViewById(R.id.group_list_spinner));
        groupLock = (ImageView)(findViewById(R.id.group_lock_image));


        groupLock.setBackgroundResource(R.drawable.ic_lock_outline_white_24dp);

        spinnerArrayAdapter = new GroupSpinnerAdapter();
        spinner.setAdapter(spinnerArrayAdapter);
        spinner.setOnItemSelectedListener(spinnerArrayAdapter);
        getGroupList();

        dismiss.setOnClickListener(onDiscardClick);
        takePicture.setOnClickListener(onTakePictureClick);
        choosePicture.setOnClickListener(onAttachPictureClick);
        send.setOnClickListener(onClickSend);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    public void getGroupList() {
        DatabaseFactory.getGroupDatabase(this).getGroups(onGroupsLoaded);
    }
    private DatabaseExecutor.ReadableQueryCallback onGroupsLoaded = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(final Object result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ArrayList<Group> answer = (ArrayList<Group>) (result);
                    spinnerArrayAdapter.swap(answer);
                    spinnerArrayAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    @Override
    protected void onDestroy() {
        if(imageBitmap != null) {
            imageBitmap.recycle();
            imageBitmap = null;
        }
        super.onDestroy();
    }

    View.OnClickListener onDiscardClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(compose.getWindowToken(), 0);
            finish();
        }
    };

    View.OnClickListener onTakePictureClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            final Activity activity = PopupComposeStatus.this;
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
                File photoFile;
                try {
                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    File storageDir = FileUtil.getWritableAlbumStorageDir();
                    String imageFileName = "JPEG_" + timeStamp + "_";
                    String suffix = ".jpg";
                    photoFile = File.createTempFile(
                            imageFileName,  /* prefix */
                            suffix,         /* suffix */
                            storageDir      /* directory */
                    );
                    mCurrentPhotoFile = photoFile.getName();
                } catch (IOException error) {
                    Log.e(TAG, "[!] cannot create photo file "+error.getMessage());
                    return;
                }
                if(photoFile != null) {
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                    activity.startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            }
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {

            try {
                File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(),mCurrentPhotoFile);
                Picasso.with(this)
                        .load("file://"+attachedFile.getAbsolutePath())
                        .fit()
                        .centerCrop()
                        .into(compose_background);
            } catch(IOException ignore){
            }
        }
    }

    View.OnClickListener onAttachPictureClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
        }
    };

    View.OnClickListener onClickSend = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            try {
                String message = compose.getText().toString();
                if (message.equals(""))
                    return;

                Group group = spinnerArrayAdapter.getSelected();
                if(group == null)
                    return;

                Contact localContact = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getLocalContact();
                long now = (System.currentTimeMillis() / 1000L);
                PushStatus pushStatus = new PushStatus(localContact, group, message, now);
                pushStatus.setUserRead(true);

                if (mCurrentPhotoFile != null) {
                    pushStatus.setFileName(mCurrentPhotoFile);

                    // add the photo to the media library
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    File f = new File(FileUtil.getReadableAlbumStorageDir(), mCurrentPhotoFile);
                    Uri contentUri = Uri.fromFile(f);
                    mediaScanIntent.setData(contentUri);
                    sendBroadcast(mediaScanIntent);

                    mCurrentPhotoFile = null;
                }

                EventBus.getDefault().post(new UserComposeStatus(pushStatus));
                pushStatus.discard();
            } catch (Exception e) {
                Log.e(TAG,"[!] "+e.getMessage());
            } finally {
                compose.setText("");
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(compose.getWindowToken(), 0);
                finish();
            }
        }
    };

    public class GroupSpinnerAdapter extends ArrayAdapter<String> implements AdapterView.OnItemSelectedListener{

        private ArrayList<Group> groupList;
        private Group selectedItem;

        public GroupSpinnerAdapter() {
            super(PopupComposeStatus.this, android.R.layout.simple_spinner_item);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            groupList = new ArrayList<Group>();
            selectedItem = null;
        }

        public Group getSelected() {
            return selectedItem;
        }

        @Override
        public String getItem(int position) {
            return groupList.get(position).getName();
        }

        @Override
        public int getCount() {
            return groupList.size();
        }

        public void swap(ArrayList<Group> array) {
            this.groupList = array;
            if(array != null)
                selectedItem = array.get(0);
            else
                selectedItem = null;
        }

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            Group clicked = groupList.get(i);
            if(!clicked.isIsprivate())
                groupLock.setBackgroundResource(R.drawable.ic_lock_open_white_24dp);
            else
                groupLock.setBackgroundResource(R.drawable.ic_lock_white_24dp);
            selectedItem = clicked;
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
        }

    }

}
