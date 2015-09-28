/*
 * Copyright (C) 2014 Disrupted Systems
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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.disrupted.rumble.R;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author Marlinski
 */
public class DisplayImage extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.display_image);

        ImageView image = (ImageView)findViewById(R.id.image);
        Intent intent = getIntent();
        String name   = (String) intent.getStringExtra("IMAGE_NAME");

        setTitle(name);
        getSupportActionBar().hide();

        try {
            File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(), name);
            if (!attachedFile.isFile() || !attachedFile.exists())
                throw new IOException("file does not exists");

            Picasso.with(this)
                    .load("file://" + attachedFile.getAbsolutePath())
                    .fit()
                    .centerInside()
                    .into(image);

        } catch(Exception ignore) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}