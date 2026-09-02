package com.kmsma.i_guide;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Favourite channels, persisted by Tunarr channel id in SharedPreferences. */
public final class FavouritesStore {

    private static final String PREFS = "iguide_favourites";
    private static final String KEY_IDS = "channel_ids";

    private final SharedPreferences prefs;
    private final Set<String> ids;

    public FavouritesStore(@NonNull Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Defensive copy: the Set returned by getStringSet must not be mutated.
        ids = new HashSet<>(prefs.getStringSet(KEY_IDS, new HashSet<>()));
    }

    public boolean isFavourite(@Nullable Channel channel) {
        return channel != null && ids.contains(channel.getId());
    }

    /** Adds or removes the channel; returns true if it is a favourite afterwards. */
    public boolean toggle(@Nullable Channel channel) {
        if (channel == null || channel.getId() == null) {
            return false;
        }
        boolean added;
        if (ids.contains(channel.getId())) {
            ids.remove(channel.getId());
            added = false;
        } else {
            ids.add(channel.getId());
            added = true;
        }
        prefs.edit().putStringSet(KEY_IDS, new HashSet<>(ids)).apply();
        return added;
    }

    /** Favourite channels in line-up order. */
    @NonNull
    public List<Channel> filter(@NonNull List<Channel> channels) {
        List<Channel> out = new ArrayList<>();
        for (Channel c : channels) {
            if (ids.contains(c.getId())) {
                out.add(c);
            }
        }
        return out;
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }
}
