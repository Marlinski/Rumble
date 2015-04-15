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
import android.media.Image;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.disrupted.rumble.R;

/**
 * @author Marlinski
 */
public class DisplayQRCode extends Activity {

    Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.display_qrcode_layout);

        Intent intent = getIntent();
        bitmap = (Bitmap) intent.getParcelableExtra("EXTRA_QRCODE");
        String name   = (String) intent.getStringExtra("EXTRA_GROUP_NAME");
        String buffer = (String) intent.getStringExtra("EXTRA_BUFFER");

        setTitle(name);

        ImageView qrView = (ImageView)findViewById(R.id.qrcode);
        TextView  bufView = (TextView)findViewById(R.id.buffer);

        Drawable drawable = new BitmapDrawable(getResources(), bitmap);
        qrView.setImageDrawable(drawable);
        bufView.setText(buffer);

    }

    @Override
    protected void onDestroy() {
        bitmap.recycle();
        bitmap = null;
        super.onDestroy();
    }
}
