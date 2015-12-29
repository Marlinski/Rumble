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

package org.disrupted.rumble.userinterface.activity.settings;

import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.userinterface.events.UserWipeChatMessages;
import org.disrupted.rumble.userinterface.events.UserWipeData;
import org.disrupted.rumble.userinterface.events.UserWipeFiles;
import org.disrupted.rumble.userinterface.events.UserWipeStatuses;
import org.disrupted.rumble.database.events.ChatWipedEvent;
import org.disrupted.rumble.database.events.StatusWipedEvent;
import org.disrupted.rumble.userinterface.views.SimpleHistogram;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;
import java.io.IOException;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class StorageActivity extends AppCompatActivity {

    private static final String TAG = "StorageActivity";

    private TextView totalData;
    private SimpleHistogram appSizeHistogram;
    private SimpleHistogram dbSizeHistogram;
    private SimpleHistogram fileSizeHistogram;
    private TextView appDetailText;
    private TextView dbDetailText;
    private TextView fileDetailText;

    @Override
    protected void onStart() {
	super.onStart();
	if(!EventBus.getDefault().isRegistered(this))
	    EventBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStop() {
	super.onStop();
	if(EventBus.getDefault().isRegistered(this))
	    EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_storage);
        setTitle(R.string.settings_storage);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);

        totalData = (TextView)findViewById(R.id.storage_total_value);
        //combinedHistogram = (CombinedHistogram)findViewById(R.id.combined_histogram);

        appDetailText = (TextView)findViewById(R.id.app_detail_text);
        appSizeHistogram = (SimpleHistogram)findViewById(R.id.usage_detail_app);

        dbDetailText = (TextView)findViewById(R.id.db_detail_text);
        dbSizeHistogram = (SimpleHistogram)findViewById(R.id.usage_detail_db);

        fileDetailText = (TextView)findViewById(R.id.file_detail_text);
        fileSizeHistogram = (SimpleHistogram)findViewById(R.id.usage_detail_file);

        // memory cleaning buttons
        Button clearStatus = (Button)findViewById(R.id.clear_statuses);
        clearStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            EventBus.getDefault().post(new UserWipeStatuses());
                        }
                    }
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(StorageActivity.this);
                builder.setMessage("Are you sure?").setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();
            }
        });

        Button clearChat   = (Button)findViewById(R.id.clear_chat);
        clearChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            EventBus.getDefault().post(new UserWipeChatMessages());
                        }
                    }
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(StorageActivity.this);
                builder.setMessage("Are you sure?").setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();
            }
        });

        Button clearFiles   = (Button)findViewById(R.id.clear_files);
        clearFiles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            EventBus.getDefault().post(new UserWipeFiles());
                        }
                    }
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(StorageActivity.this);
                builder.setMessage("Are you sure?").setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();
            }
        });

        Button clearData  = (Button)findViewById(R.id.clear_data);
        clearData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            EventBus.getDefault().post(new UserWipeData());
                        }
                    }
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(StorageActivity.this);
                builder.setMessage("Are you sure?").setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();
            }
        });

        computeDataUsage();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();
        if (id==android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
    }

    private void computeDataUsage() {
        long appSize = 0;
        try {
            final PackageManager pm = getPackageManager();
            ApplicationInfo applicationInfo = pm.getApplicationInfo(getApplicationContext().getPackageName(), 0);
            File file = new File(applicationInfo.publicSourceDir);
            if(file != null)
                appSize = file.length();
        } catch(PackageManager.NameNotFoundException e) {}
        appDetailText.setText(appDetailText.getText() + " (" + humanReadableByteCount(appSize, false) + ")");

        long dbSize = 0;
        File database = getDatabasePath(DatabaseFactory.getDatabaseName());
        if(database != null)
            dbSize = database.length();
        dbDetailText.setText(dbDetailText.getText()+" ("+humanReadableByteCount(dbSize,false)+")");

        long fileSize = 0;
        long freespace = 0;
        try {
            File dir = FileUtil.getReadableAlbumStorageDir();
            if(dir != null) {
                File files[] = dir.listFiles();
                if(files != null) {
                    for (File file : files) {
                        fileSize += file.length();
                    }
                }
                freespace = dir.getFreeSpace();
            }
            fileDetailText.setText(fileDetailText.getText() + " (" + humanReadableByteCount(fileSize, false) + ")");
        } catch(IOException ie) {}

        long total = appSize+dbSize+fileSize;
        //combinedHistogram.setSize(appSize, dbSize, fileSize);
        totalData.setText(humanReadableByteCount(total, false) + " / " +
                humanReadableByteCount(freespace, false));

        appSizeHistogram.setSize(appSize, total);
        appSizeHistogram.setColor(R.color.app_size);
        dbSizeHistogram.setSize(dbSize, total);
        dbSizeHistogram.setColor(R.color.db_size);
        fileSizeHistogram.setSize(fileSize, total);
        fileSizeHistogram.setColor(R.color.file_size);
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    /** user feedback after clearing chats **/
    public void onEvent(ChatWipedEvent event) {
	this.runOnUiThread(new Runnable () {
	    public void run() {
		Toast.makeText(getApplicationContext(), "Chats Cleared", Toast.LENGTH_LONG).show();
	    }
	});
    }

    /** user feedback after clearing statuses **/
    public void onEvent(StatusWipedEvent event) {
	this.runOnUiThread(new Runnable () {
	    public void run() {
	        Toast.makeText(getApplicationContext(), "All Status Cleared", Toast.LENGTH_LONG).show();
	    }
	});
    }
}
