package com.kmsma.i_guide;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** Holds the fetched channel list and tracks which channel is currently tuned. */
public class ChannelManager {

    private List<Channel> channels = Collections.emptyList();
    private int currentIndex = 0;

    public void setChannels(List<Channel> channels) {
        this.channels = channels != null ? channels : Collections.emptyList();
        this.currentIndex = 0;
    }

    public boolean hasChannels() {
        return !channels.isEmpty();
    }

    @Nullable
    public Channel getCurrentChannel() {
        if (channels.isEmpty()) {
            return null;
        }
        return channels.get(currentIndex);
    }

    /**
     * Points the cursor at the channel with the given id so that subsequent
     * next/previous calls continue from there. Returns whether it was found.
     */
    public boolean selectById(@Nullable String id) {
        if (id == null) {
            return false;
        }
        for (int i = 0; i < channels.size(); i++) {
            if (id.equals(channels.get(i).getId())) {
                currentIndex = i;
                return true;
            }
        }
        return false;
    }

    /** Moves to the next channel (wraps around) and returns it. */
    @Nullable
    public Channel nextChannel() {
        if (channels.isEmpty()) {
            return null;
        }
        currentIndex = (currentIndex + 1) % channels.size();
        return getCurrentChannel();
    }

    /** Moves to the previous channel (wraps around) and returns it. */
    @Nullable
    public Channel previousChannel() {
        if (channels.isEmpty()) {
            return null;
        }
        currentIndex = (currentIndex - 1 + channels.size()) % channels.size();
        return getCurrentChannel();
    }
}
