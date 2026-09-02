package com.kmsma.i_guide;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Colour coding for guide cells. The four colours of each entry are the stops of the
 * cell's glossy vertical gradient (see {@link Gradients#categoryCell}).
 */
public enum ProgramCategory {

    REGULAR(R.color.ig_channel_cell_1, R.color.ig_channel_cell_2,
            R.color.ig_channel_cell_3, R.color.ig_channel_cell_4),

    MOVIES(R.color.ig_movies_1, R.color.ig_movies_2,
            R.color.ig_movies_3, R.color.ig_movies_4),

    SPORTS(R.color.ig_sports_1, R.color.ig_sports_2,
            R.color.ig_sports_3, R.color.ig_sports_4),

    KIDS(R.color.ig_kids_1, R.color.ig_kids_2,
            R.color.ig_kids_3, R.color.ig_kids_4),

    MUSIC(R.color.ig_music_1, R.color.ig_music_2,
            R.color.ig_music_3, R.color.ig_music_4),

    NEWS(R.color.ig_news_1, R.color.ig_news_2,
            R.color.ig_news_3, R.color.ig_news_4);

    private final int c1;
    private final int c2;
    private final int c3;
    private final int c4;

    ProgramCategory(int c1, int c2, int c3, int c4) {
        this.c1 = c1;
        this.c2 = c2;
        this.c3 = c3;
        this.c4 = c4;
    }

    public int[] cellColors() {
        return new int[]{c1, c2, c3, c4};
    }

    /** Human-readable label, used by the Favourites category tabs. */
    @NonNull
    public String label() {
        String n = name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.US);
    }

    /**
     * Maps free-form XMLTV {@code <category>} text onto the palette. XMLTV categories
     * are not standardised, so this matches on substrings and falls back to REGULAR.
     */
    @NonNull
    public static ProgramCategory fromXmltv(String... categories) {
        if (categories != null) {
            for (String raw : categories) {
                if (raw == null) {
                    continue;
                }
                String c = raw.toLowerCase(Locale.US);
                if (c.contains("movie") || c.contains("film") || c.contains("cinema")) {
                    return MOVIES;
                }
                if (c.contains("sport") || c.contains("football") || c.contains("hockey")
                        || c.contains("basketball") || c.contains("baseball")
                        || c.contains("soccer") || c.contains("racing")) {
                    return SPORTS;
                }
                if (c.contains("kid") || c.contains("child") || c.contains("cartoon")
                        || c.contains("animat") || c.contains("family")) {
                    return KIDS;
                }
                if (c.contains("music") || c.contains("concert")) {
                    return MUSIC;
                }
                if (c.contains("news") || c.contains("current affairs")
                        || c.contains("weather")) {
                    return NEWS;
                }
            }
        }
        return REGULAR;
    }
}
