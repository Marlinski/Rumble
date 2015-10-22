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
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.userinterface.activity.ContactDetailActivity;
import org.disrupted.rumble.util.TimeUtil;

import java.util.ArrayList;

/**
 * @author Marlinski
 */
public class ContactRecyclerAdapter extends RecyclerView.Adapter<ContactRecyclerAdapter.ContactHolder> {

    public static final String TAG = "ContactRecyclerAdapter";

    public class ContactHolder extends RecyclerView.ViewHolder {

        ImageView contact_avatar;
        TextView  contact_name;
        TextView  contact_last_met;

        public ContactHolder(View itemView) {
            super(itemView);
            contact_avatar   = (ImageView) itemView.findViewById(R.id.contact_avatar);
            contact_name     = (TextView) itemView.findViewById(R.id.contact_name);
            contact_last_met = (TextView) itemView.findViewById(R.id.contact_last_met);
        }

        public void bindContact(Contact contact) {
            ColorGenerator generator = ColorGenerator.DEFAULT;
            contact_avatar.setImageDrawable(
                    builder.build(contact.getName().substring(0, 1),
                            generator.getColor(contact.getUid())));
            contact_name.setText(contact.getName());
            if (contact.isLocal()) {
                contact_last_met.setText(R.string.contact_is_local);
            } else {
                if(contact.lastMet() == 0)
                    contact_last_met.setText(R.string.contact_has_never_been_met);
                else
                    contact_last_met.setText(activity.getResources()
                            .getString(R.string.contact_last_met)
                            + " " + TimeUtil.timeElapsed(contact.lastMet()));
            }

            /*
             * Manage click events
             */
            final String    uid  = contact.getUid();
            final String    name = contact.getName();
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent contactDetailActivity = new Intent(activity, ContactDetailActivity.class );
                    contactDetailActivity.putExtra("ContactID", uid);
                    contactDetailActivity.putExtra("ContactName",name);
                    activity.startActivity(contactDetailActivity);
                    activity.overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_open_exit);
                }
            });

        }
    }

    private Activity activity;
    private ArrayList<Contact> contactList;
    private static final TextDrawable.IBuilder builder = TextDrawable.builder().rect();


    public ContactRecyclerAdapter(Activity activity) {
        this.activity = activity;
        this.contactList = null;
    }

    @Override
    public ContactHolder onCreateViewHolder(ViewGroup parent, int i) {
        LinearLayout layout = (LinearLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact_list, parent, false);
        return new ContactHolder(layout);
    }

    @Override
    public void onBindViewHolder(ContactHolder contactHolder, int i) {
        Contact contact = contactList.get(i);
        contactHolder.bindContact(contact);
    }

    @Override
    public int getItemCount() {
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
