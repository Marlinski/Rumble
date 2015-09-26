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

package org.disrupted.rumble.userinterface.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.squareup.picasso.Picasso;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.userinterface.activity.ContactDetailActivity;
import org.disrupted.rumble.userinterface.events.UserDeleteContact;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class ContactListAdapter extends BaseAdapter {

    public static final String TAG = "ContactListAdapter";

    private Activity activity;
    private LayoutInflater inflater;
    private ArrayList<Contact> contactList;
    private static final TextDrawable.IBuilder builder = TextDrawable.builder().rect();


    public ContactListAdapter(Activity activity) {
        this.activity = activity;
        this.inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.contactList = null;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        LinearLayout layout = (LinearLayout)inflater.inflate(R.layout.contact_list_item, null, true);

        ImageView contact_avatar = (ImageView)    layout.findViewById(R.id.contact_avatar);
        TextView  contact_name   = (TextView)     layout.findViewById(R.id.contact_name);
        TextView  contact_uid    = (TextView)     layout.findViewById(R.id.contact_uid);
        //ImageView contact_delete = (ImageView)    layout.findViewById(R.id.contact_delete);

        ColorGenerator generator = ColorGenerator.DEFAULT;
        contact_avatar.setImageDrawable(
                builder.build(contactList.get(i).getName().substring(0, 1),
                        generator.getColor(contactList.get(i).getUid())));
        contact_name.setText(contactList.get(i).getName());
        contact_uid.setText(contactList.get(i).getUid());

        /*
         * Manage click events
         */
        final String    uid  = contactList.get(i).getUid();
        final String    name = contactList.get(i).getName();
        layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent contactStatusActivity = new Intent(activity, ContactDetailActivity.class );
                contactStatusActivity.putExtra("ContactID", uid);
                contactStatusActivity.putExtra("ContactName",name);
                activity.startActivity(contactStatusActivity);
                activity.overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_open_exit);
            }
        });
        /*
        contact_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new UserDeleteContact(uid));
            }
        });
        */

        return layout;
    }

    @Override
    public long getItemId(int i) {
        if(contactList == null)
            return 0;
        if(contactList.isEmpty())
            return 0;
        return i;
    }

    @Override
    public Object getItem(int i) {
        return contactList.get(i);
    }

    @Override
    public int getCount() {
        if(contactList == null)
            return 0;
        else
            return contactList.size();
    }

    public void swap(ArrayList<Contact> contactList) {
        if(this.contactList != null)
            this.contactList.clear();
        this.contactList = contactList;
        notifyDataSetChanged();
    }

}
