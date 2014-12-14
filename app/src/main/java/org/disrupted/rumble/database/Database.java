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

package org.disrupted.rumble.database;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;

import org.disrupted.rumble.contact.Contact;
import org.disrupted.rumble.database.events.NewContactEvent;
import org.disrupted.rumble.database.events.NewHashtagEvent;
import org.disrupted.rumble.database.events.NewStatusEvent;
import org.disrupted.rumble.message.StatusMessage;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public abstract class Database {

    private static final String TAG = "Database";


    protected static final String ID_WHERE            = "_id = ?";

    protected SQLiteOpenHelper databaseHelper;
    protected final Context context;

    public Database(Context context, SQLiteOpenHelper databaseHelper) {
        this.context        = context;
        this.databaseHelper = databaseHelper;
    }

    public void reset(SQLiteOpenHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }

}
