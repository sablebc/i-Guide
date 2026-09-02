package com.kmsma.i_guide;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What a {@link GuideScreen} may ask of its host activity. Implemented by
 * {@link PlayerActivity}, which owns the video surface and the screen back-stack.
 */
public interface ScreenHost {

    /** Pushes a screen on top of the current one. */
    void pushScreen(@NonNull GuideScreen screen);

    /** Pops the top screen, returning to whatever was underneath (or live video). */
    void popScreen();

    /** Dismisses the whole guide stack and returns to full-screen live video. */
    void returnToLiveVideo();

    /** Tunes to a channel and returns to full-screen live video. */
    void tuneTo(@Nullable Channel channel);

    /** The channel currently playing, which may differ from the one being browsed. */
    @Nullable
    Channel getTunedChannel();

    @NonNull
    FavouritesStore getFavourites();

    /**
     * Scales the live video into the given slot, or restores it to full screen when
     * {@code slot} is {@code null}.
     */
    void bindLivePreview(@Nullable View slot);
}
