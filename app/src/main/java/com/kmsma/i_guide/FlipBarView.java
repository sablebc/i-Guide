package com.kmsma.i_guide;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Bottom-anchored overlay showing what's currently on the tuned channel.
 * Slides up on show, auto-dismisses after a period of inactivity.
 */
public class FlipBarView extends FrameLayout {

    private static final long AUTO_DISMISS_DELAY_MS = 5000L;
    private static final long ANIM_DURATION_MS = 200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable dismissRunnable = this::hide;

    private TextView programTitleView;
    private TextView channelInfoView;
    private TextView programDescriptionView;
    private TextView hdBadgeView;

    private boolean showing = false;

    public FlipBarView(Context context) {
        super(context);
        init();
    }

    public FlipBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FlipBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.view_flip_bar, this, true);
        programTitleView = findViewById(R.id.flip_bar_program_title);
        channelInfoView = findViewById(R.id.flip_bar_channel_info);
        programDescriptionView = findViewById(R.id.flip_bar_program_description);
        hdBadgeView = findViewById(R.id.flip_bar_hd_badge);

        setVisibility(GONE);
        setAlpha(0f);
    }

    /** Populates and shows the flip bar, (re)starting the auto-dismiss timer. */
    public void show(Channel channel, String programDescription, boolean isHd) {
        if (channel != null) {
            programTitleView.setText(channel.getName());
            String number = String.valueOf(channel.getNumber());
            channelInfoView.setText(number);
        }
        programDescriptionView.setText(programDescription);
        hdBadgeView.setVisibility(isHd ? VISIBLE : GONE);

        if (!showing) {
            showing = true;
            setVisibility(VISIBLE);
            setAlpha(0f);
            setTranslationY(getHeight() > 0 ? getHeight() : 200f);
            animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(ANIM_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        resetAutoDismissTimer();
    }

    /**
     * Shows the flip bar for a channel using its EPG entry: program title on the left,
     * "5 CTV 8-8:30p" on the right, synopsis underneath. Falls back to the channel name
     * when the guide has nothing for this slot.
     */
    public void show(Channel channel, @Nullable Program program) {
        if (channel == null) {
            return;
        }
        String channelLabel = channel.getNumber() + " " + channel.getName();
        if (program == null) {
            programTitleView.setText(channel.getName());
            channelInfoView.setText(channelLabel);
            programDescriptionView.setText(
                    getContext().getString(R.string.no_information));
            hdBadgeView.setVisibility(GONE);
        } else {
            programTitleView.setText(program.getTitle());
            channelInfoView.setText(channelLabel + "   " + program.formatTimeRange());
            programDescriptionView.setText(program.formatSynopsis());
            hdBadgeView.setVisibility(program.isHd() ? VISIBLE : GONE);
        }
        reveal();
        resetAutoDismissTimer();
    }

    /** Slides the bar up if it is not already on screen. */
    private void reveal() {
        if (showing) {
            return;
        }
        showing = true;
        setVisibility(VISIBLE);
        setAlpha(0f);
        setTranslationY(getHeight() > 0 ? getHeight() : 200f);
        animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(ANIM_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /** Restarts the 5-second inactivity timer without changing visibility. */
    public void resetAutoDismissTimer() {
        handler.removeCallbacks(dismissRunnable);
        if (showing) {
            handler.postDelayed(dismissRunnable, AUTO_DISMISS_DELAY_MS);
        }
    }

    public void hide() {
        handler.removeCallbacks(dismissRunnable);
        if (!showing) {
            return;
        }
        showing = false;
        animate()
                .alpha(0f)
                .setDuration(ANIM_DURATION_MS)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> setVisibility(GONE))
                .start();
    }

    public boolean isShowing() {
        return showing;
    }
}
