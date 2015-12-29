/*
 * Copyright (C) 2014 Lucien Loiseau
 * This file is part of Rumble.
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
 * You should have received a copy of the GNU General Public License along
 * with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.userinterface.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import org.disrupted.rumble.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.squareup.picasso.Picasso;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.util.FileUtil;
import org.disrupted.rumble.util.TimeUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author Lucien Loiseau
 */
public class DisplayStatusActivity extends AppCompatActivity {

    private static final String TAG = "DisplayStatusActivity";

    private static final TextDrawable.IBuilder builder = TextDrawable.builder().rect();

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_status);
        setTitle(R.string.status_viewer);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        Bundle args = getIntent().getExtras();
        String statusID = args.getString("StatusID");
        renderStatus(statusID);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
        }
        return false;
    }

    private void renderStatus(String statusID) {

        PushStatus status = DatabaseFactory.getPushStatusDatabase(this).getStatus(statusID);
        if(status == null)
            return;

        ImageView avatarView    = (ImageView)findViewById(R.id.status_item_avatar);
        TextView authorView     = (TextView) findViewById(R.id.status_item_author);
        TextView textView       = (TextView) findViewById(R.id.status_item_body);
        TextView tocView        = (TextView) findViewById(R.id.status_item_created);
        TextView toaView        = (TextView) findViewById(R.id.status_item_received);
        TextView groupNameView  = (TextView) findViewById(R.id.status_item_group_name);
        ImageView attachedView  = (ImageView)findViewById(R.id.status_item_attached_image);
        ImageView moreView      = (ImageView)findViewById(R.id.status_item_more_options);
        LinearLayout box        = (LinearLayout)findViewById(R.id.status_item_box);

        final String uid = status.getAuthor().getUid();
        final String name= status.getAuthor().getName();

        // we draw the avatar
        ColorGenerator generator = ColorGenerator.DEFAULT;
        avatarView.setImageDrawable(
                builder.build(status.getAuthor().getName().substring(0, 1),
                        generator.getColor(status.getAuthor().getUid())));

        // we draw the author field
        authorView.setText(status.getAuthor().getName());
        tocView.setText(TimeUtil.timeElapsed(status.getTimeOfCreation()));
        toaView.setText(TimeUtil.timeElapsed(status.getTimeOfArrival()));
        groupNameView.setText(status.getGroup().getName());
        groupNameView.setTextColor(generator.getColor(status.getGroup().getGid()));

        // we draw the status (with clickable links)
        textView.setText(status.getPost());
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setTextIsSelectable(true);

        Linkify.addLinks(textView, Linkify.ALL);

        /* todo: clickable hashtags */

        /* we draw the attached file (if any) */
        if (status.hasAttachedFile()) {
            attachedView.setVisibility(View.VISIBLE);
            try {
                File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(), status.getFileName());

                if (!attachedFile.isFile() || !attachedFile.exists())
                    throw new IOException("file does not exists");

                Picasso.with(DisplayStatusActivity.this)
                        .load("file://"+attachedFile.getAbsolutePath())
                        .resize(96, 96)
                        .centerCrop()
                        .into(attachedView);

                final String filename =  status.getFileName();
                attachedView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "trying to open: " + filename);
                        Intent intent = new Intent(DisplayStatusActivity.this, DisplayImage.class);
                        intent.putExtra("IMAGE_NAME", filename);
                        startActivity(intent);
                    }
                });
            } catch (IOException ignore) {
                Picasso.with(DisplayStatusActivity.this)
                        .load(R.drawable.ic_close_black_48dp)
                        .resize(96, 96)
                        .centerCrop()
                        .into(attachedView);
            }
        } else {
            attachedView.setVisibility(View.GONE);
        }
        /*
        moreView.setOnClickListener(new PopupMenuListener());
        if (!status.hasUserReadAlready() || ((System.currentTimeMillis() - status.getTimeOfArrival()) < 60000)) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                box.setBackgroundDrawable(activity.getResources().getDrawable(R.drawable.status_shape_unread));
            } else {
                box.setBackground(activity.getResources().getDrawable(R.drawable.status_shape_unread));
            }
            if (!status.hasUserReadAlready()) {
                status.setUserRead(true);
                EventBus.getDefault().post(new UserReadStatus(status));
            }
        } else {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                box.setBackgroundDrawable(activity.getResources().getDrawable(R.drawable.status_shape_read));
            } else {
                box.setBackground(activity.getResources().getDrawable(R.drawable.status_shape_read));
            }
        }
        */
    }

}
