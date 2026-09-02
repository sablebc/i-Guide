package com.kmsma.i_guide;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lower-third overlay showing three channels with what is on now and next, using the
 * same span grid as the full guide so cell widths stay consistent between the two.
 */
public class MiniGuideView extends LinearLayout {

    private static final long ANIM_DURATION_MS = 200L;
    private static final int VISIBLE_ROWS = 3;
    private static final int SLOT_COUNT = 3;
    private static final int ROW_HEIGHT = 30;
    private static final int TIME_HEADER_HEIGHT = 24;

    public interface Listener {
        /** OK on a row: tune to that channel. */
        void onChannelChosen(@NonNull Channel channel);
    }

    private final EpgRepository epg = EpgRepository.get();

    private GuideGridView grid;
    private boolean showing;
    private Listener listener;

    public MiniGuideView(Context context) {
        super(context);
        init();
    }

    public MiniGuideView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MiniGuideView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        Context ctx = getContext();
        setOrientation(VERTICAL);

        View fade = new View(ctx);
        fade.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(ctx, 40)));
        Ui.setBackground(fade, Gradients.topFade(ctx, false));
        addView(fade);

        grid = new GuideGridView(ctx);
        grid.setSlotCount(SLOT_COUNT);
        grid.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                gridHeightFor(VISIBLE_ROWS)));
        grid.setOnProgramChosenListener((channel, program) -> {
            if (listener != null) {
                listener.onChannelChosen(channel);
            }
        });
        addView(grid);

        addView(buildFooter(ctx));
        setVisibility(GONE);
    }

    private int gridHeightFor(int rows) {
        return Ui.px(getContext(), TIME_HEADER_HEIGHT + rows * ROW_HEIGHT);
    }

    private View buildFooter(Context ctx) {
        android.widget.TextView footer = Ui.text(ctx, ctx.getString(R.string.hint_mini_guide),
                12f, false, Ui.color(ctx, R.color.ig_white));
        Ui.padding(footer, ctx, 16, 4, 16, 4);
        Ui.setBackground(footer, Gradients.withEdges(ctx, Gradients.footerBar(ctx),
                Ui.color(ctx, R.color.ig_header_divider),
                Ui.color(ctx, R.color.ig_base_navy),
                Color.TRANSPARENT, Ui.px(ctx, 1), Ui.px(ctx, 2)));
        footer.setLayoutParams(Ui.matchWrap());
        return footer;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ---- Visibility --------------------------------------------------------

    public void show(@Nullable Channel tunedChannel) {
        // Up to three channels, but never taller than the line-up actually fills:
        // reserved-but-empty rows would punch a hole in the video.
        int rows = Math.max(1, Math.min(VISIBLE_ROWS, epg.getChannels().size()));
        grid.getLayoutParams().height = gridHeightFor(rows);
        grid.requestLayout();

        grid.setChannels(epg.getChannels());
        grid.selectChannel(tunedChannel);
        if (showing) {
            return;
        }
        showing = true;
        setVisibility(VISIBLE);
        setAlpha(0f);
        setTranslationY(getHeight() > 0 ? getHeight() : Ui.px(getContext(), 160));
        animate().translationY(0f).alpha(1f)
                .setDuration(ANIM_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    public void hide() {
        if (!showing) {
            return;
        }
        showing = false;
        animate().alpha(0f)
                .setDuration(ANIM_DURATION_MS)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> setVisibility(GONE))
                .start();
    }

    public boolean isShowing() {
        return showing;
    }

    /** Handles a D-pad key; returns whether it was consumed. */
    public boolean handleKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // Left/right would scroll the time window; the mini guide stays on "now".
            return true;
        }
        return grid.handleKey(keyCode);
    }
}
