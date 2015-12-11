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

package org.disrupted.rumble.util;

import android.content.res.Resources;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;

/**
 * @author Lucien Loiseau
 */
public class TimeUtil {

    private static final long ONE_MINUTE_IN_SECONDS = 60;
    private static final long ONE_HOUR_IN_SECONDS = 60 * ONE_MINUTE_IN_SECONDS;
    private static final long ONE_DAY_IN_SECONDS = 24 * ONE_HOUR_IN_SECONDS;
    private static final long ONE_MONTH_IN_SECONDS = 30 * ONE_DAY_IN_SECONDS;
    private static final long ONE_YEAR_IN_SECONDS = 365 * ONE_DAY_IN_SECONDS;

    public static String timeElapsed(long timeInMilliSecondsSinceEpoch) {
        long time = ((System.currentTimeMillis() - timeInMilliSecondsSinceEpoch)/1000L);
        Resources res = RumbleApplication.getContext().getResources();

        if(time < ONE_MINUTE_IN_SECONDS)
            return getTimeInSeconds(time)+" "+res.getString(R.string.seconds_ago);

        if(time < 2*ONE_MINUTE_IN_SECONDS)
            return res.getString(R.string.minute_ago);
        if(time < ONE_HOUR_IN_SECONDS)
            return getTimeInMinutes(time)+" "+res.getString(R.string.minutes_ago);

        if(time < 2*ONE_HOUR_IN_SECONDS)
            return res.getString(R.string.hour_ago);
        if(time < ONE_DAY_IN_SECONDS)
            return getTimeInHours(time)+" "+res.getString(R.string.hours_ago);

        if(time < 2*ONE_DAY_IN_SECONDS)
            return res.getString(R.string.day_ago);
        if(time < ONE_MONTH_IN_SECONDS)
            return getTimeInDays(time)+" "+res.getString(R.string.days_ago);

        if(time < 2*ONE_MONTH_IN_SECONDS)
            return res.getString(R.string.month_ago);
        if(time < ONE_YEAR_IN_SECONDS)
            return getTimeInMonths(time)+" "+res.getString(R.string.months_ago);

        if(time < 2*ONE_YEAR_IN_SECONDS)
            return res.getString(R.string.year_ago);
        if(time < 10*ONE_YEAR_IN_SECONDS)
            return getTimeInYears(time)+" "+res.getString(R.string.years_ago);

        return res.getString(R.string.too_old);
    }

    private static String getTimeInSeconds(long time){
        return Long.toString(time);
    }
    private static String getTimeInMinutes(long time){
        return Long.toString(time/ONE_MINUTE_IN_SECONDS);
    }
    private static String getTimeInHours(long time){
        return Long.toString(time/ONE_HOUR_IN_SECONDS);
    }
    private static String getTimeInDays(long time){
        return Long.toString(time/ONE_DAY_IN_SECONDS);
    }
    private static String getTimeInMonths(long time){
        return Long.toString(time/ONE_MONTH_IN_SECONDS);
    }
    private static String getTimeInYears(long time){
        return Long.toString(time/ONE_YEAR_IN_SECONDS);
    }

}
