package com.kmsma.i_guide;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** Data model for a Tunarr channel, as returned by GET /api/channels. */
public class Channel {

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("number")
    private int number;

    @SerializedName("icon")
    private Icon icon;

    @SerializedName("streamMode")
    private String streamMode;

    @SerializedName("programCount")
    private int programCount;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getNumber() {
        return number;
    }

    public Icon getIcon() {
        return icon;
    }

    /** Absolute URL for this channel's logo, or null when it has none. */
    @Nullable
    public String getIconUrl() {
        return TunarrApiClient.buildIconUrl(icon != null ? icon.getPath() : null);
    }

    public String getStreamMode() {
        return streamMode;
    }

    public int getProgramCount() {
        return programCount;
    }

    /** Nested icon metadata from the channel payload. */
    public static class Icon {
        @SerializedName("path")
        private String path;

        public String getPath() {
            return path;
        }
    }
}
