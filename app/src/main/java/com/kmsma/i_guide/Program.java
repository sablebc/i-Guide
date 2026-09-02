package com.kmsma.i_guide;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.Locale;

/** A single EPG entry, parsed from the Tunarr XMLTV feed. */
public class Program {

    private final String channelId;
    private final String title;
    private final String episodeTitle;
    private final String description;
    private final long startMs;
    private final long stopMs;
    private final ProgramCategory category;
    private final String categoryLabel;
    private final String rating;
    private final String year;
    private final boolean isNew;
    private final boolean isHd;

    public Program(String channelId, String title, @Nullable String episodeTitle,
                   @Nullable String description, long startMs, long stopMs,
                   ProgramCategory category, @Nullable String categoryLabel,
                   @Nullable String rating, @Nullable String year, boolean isNew, boolean isHd) {
        this.channelId = channelId;
        this.title = title != null ? title : "";
        this.episodeTitle = episodeTitle;
        this.description = description;
        this.startMs = startMs;
        this.stopMs = stopMs;
        this.category = category != null ? category : ProgramCategory.REGULAR;
        this.categoryLabel = categoryLabel;
        this.rating = rating;
        this.year = year;
        this.isNew = isNew;
        this.isHd = isHd;
    }

    /** Placeholder cell for a stretch of time the EPG has no data for. */
    public static Program filler(String channelId, long startMs, long stopMs, String title) {
        return new Program(channelId, title, null, null, startMs, stopMs,
                ProgramCategory.REGULAR, null, null, null, false, false);
    }

    public String getChannelId() {
        return channelId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getEpisodeTitle() {
        return episodeTitle;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getStopMs() {
        return stopMs;
    }

    @NonNull
    public ProgramCategory getCategory() {
        return category;
    }

    @Nullable
    public String getCategoryLabel() {
        return categoryLabel;
    }

    @Nullable
    public String getRating() {
        return rating;
    }

    @Nullable
    public String getYear() {
        return year;
    }

    public boolean isNew() {
        return isNew;
    }

    public boolean isHd() {
        return isHd;
    }

    public boolean isLiveAt(long timeMs) {
        return timeMs >= startMs && timeMs < stopMs;
    }

    public boolean overlaps(long windowStart, long windowEnd) {
        return startMs < windowEnd && stopMs > windowStart;
    }

    /** Milliseconds of this program that fall inside the given window. */
    public long overlapMs(long windowStart, long windowEnd) {
        return Math.max(0L, Math.min(stopMs, windowEnd) - Math.max(startMs, windowStart));
    }

    /**
     * Compact time range in the guide's house style: {@code 8-8:30p}, {@code 8-10p},
     * {@code 11:30a-12:30p}. The am/pm suffix only appears on the start time when it
     * differs from the end time's.
     */
    @NonNull
    public String formatTimeRange() {
        return formatTimeRange(startMs, stopMs);
    }

    @NonNull
    public static String formatTimeRange(long startMs, long stopMs) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(startMs);
        Calendar stop = Calendar.getInstance();
        stop.setTimeInMillis(stopMs);

        boolean sameHalf = start.get(Calendar.AM_PM) == stop.get(Calendar.AM_PM);
        String startText = formatClock(start, !sameHalf);
        String stopText = formatClock(stop, true);
        return startText + "-" + stopText;
    }

    /** {@code 8}, {@code 8:30}, optionally suffixed {@code a} / {@code p}. */
    @NonNull
    public static String formatClock(Calendar cal, boolean withSuffix) {
        int hour = cal.get(Calendar.HOUR);
        if (hour == 0) {
            hour = 12;
        }
        int minute = cal.get(Calendar.MINUTE);
        String s = minute == 0
                ? String.valueOf(hour)
                : String.format(Locale.US, "%d:%02d", hour, minute);
        if (withSuffix) {
            s += cal.get(Calendar.AM_PM) == Calendar.AM ? "a" : "p";
        }
        return s;
    }

    /** {@code 8:00p} form used by the guide's time-slot column headers. */
    @NonNull
    public static String formatSlotHeader(long timeMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeMs);
        int hour = cal.get(Calendar.HOUR);
        if (hour == 0) {
            hour = 12;
        }
        return String.format(Locale.US, "%d:%02d%s", hour, cal.get(Calendar.MINUTE),
                cal.get(Calendar.AM_PM) == Calendar.AM ? "a" : "p");
    }

    /**
     * The one-line blurb shown in the purple info panel and the flip bar, matching the
     * reference: {@code "Episode Title", New, (2019), Description. (Comedy)}
     */
    @NonNull
    public String formatSynopsis() {
        StringBuilder sb = new StringBuilder();
        if (episodeTitle != null && !episodeTitle.isEmpty()) {
            sb.append('"').append(episodeTitle).append('"');
        }
        if (isNew) {
            appendPart(sb, "New");
        }
        if (year != null && !year.isEmpty()) {
            appendPart(sb, "(" + year + ")");
        }
        if (description != null && !description.isEmpty()) {
            appendPart(sb, description);
        }
        if (sb.length() == 0) {
            sb.append(title);
        }
        if (categoryLabel != null && !categoryLabel.isEmpty()) {
            sb.append(" (").append(categoryLabel).append(')');
        }
        return sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(part);
    }

    /** {@code TV-PG - HD} metadata line for the Program Information screen. */
    @NonNull
    public String formatRatingLine() {
        StringBuilder sb = new StringBuilder();
        if (rating != null && !rating.isEmpty()) {
            sb.append(rating);
        }
        if (isHd) {
            if (sb.length() > 0) {
                sb.append(" — ");
            }
            sb.append("HD");
        }
        return sb.toString();
    }
}
