package com.kmsma.i_guide;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Icon bar that slides up over live video, matching the Flip Bar's 200ms motion. The
 * focused tile carries the yellow ring and its description appears in the strip below.
 */
public class QuickMenuView extends LinearLayout {

    private static final long ANIM_DURATION_MS = 200L;
    private static final int TILE_WIDTH = 44;
    private static final int TILE_HEIGHT = 38;
    private static final int GLYPH_SIZE = 18;

    /** Tiles in the order they appear on the bar. */
    public enum Item {
        MAIN_MENU(R.drawable.ic_glyph_home, null, "Main Menu", "Browse all guide features"),
        GUIDE(R.drawable.ic_glyph_grid, null, "Guide", "Browse listings by time"),
        JELLYFIN(R.drawable.ic_glyph_film, null, "Jellyfin", "Browse your Jellyfin library"),
        HDTV(0, "HD", "HDTV", "Jump to HD channels"),
        FAVOURITES(R.drawable.ic_glyph_heart, null, "Favourites", "View your favourite channels"),
        SEARCH(R.drawable.ic_glyph_search, null, "Search", "Find shows by title"),
        MOVIES(R.drawable.ic_glyph_movie, null, "Movies", "Browse movie listings"),
        KIDS(R.drawable.ic_glyph_kid, null, "Kids", "Kids programs and channels"),
        SPORTS(R.drawable.ic_glyph_ball, null, "Sports", "Sports listings and scores"),
        MUSIC(R.drawable.ic_glyph_music, null, "Music", "Music channels"),
        SETTINGS(R.drawable.ic_glyph_lock, null, "Settings", "Guide setup and preferences");

        @DrawableRes
        final int glyph;
        @Nullable
        final String text;
        public final String label;
        final String description;

        Item(@DrawableRes int glyph, @Nullable String text, String label, String description) {
            this.glyph = glyph;
            this.text = text;
            this.label = label;
            this.description = description;
        }
    }

    private static final List<Item> ITEMS = Arrays.asList(Item.values());

    public interface OnItemChosenListener {
        void onItemChosen(@NonNull Item item);
    }

    private LinearLayout tileStrip;
    private TextView descriptionView;

    private int focusedIndex;
    private boolean showing;
    private OnItemChosenListener chosenListener;

    public QuickMenuView(Context context) {
        super(context);
        init();
    }

    public QuickMenuView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public QuickMenuView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        Context ctx = getContext();
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, Ui.px(ctx, 34));

        addView(buildFade(ctx));
        addView(buildTab(ctx));
        addView(buildTileStrip(ctx));
        addView(buildDescriptionStrip(ctx));

        setVisibility(GONE);
        rebuildTiles();
    }

    private View buildFade(Context ctx) {
        View fade = new View(ctx);
        fade.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(ctx, 44)));
        Ui.setBackground(fade, Gradients.topFade(ctx, true));
        return fade;
    }

    private View buildTab(Context ctx) {
        TextView tab = Ui.text(ctx, ctx.getString(R.string.title_quick_menu), 12f, true,
                Ui.color(ctx, R.color.ig_white));
        tab.setPadding(Ui.px(ctx, 10), Ui.px(ctx, 3), Ui.px(ctx, 16), Ui.px(ctx, 3));
        Ui.setBackground(tab, Gradients.quickMenuTab(ctx));

        LayoutParams lp = Ui.wrapWrap();
        lp.setMarginStart(Ui.px(ctx, 18));
        tab.setLayoutParams(lp);
        return tab;
    }

    private View buildTileStrip(Context ctx) {
        tileStrip = Ui.row(ctx);
        tileStrip.setLayoutParams(Ui.matchWrap());
        Ui.padding(tileStrip, ctx, 18, 8, 18, 8);
        Ui.setBackground(tileStrip, Gradients.withEdges(ctx, Gradients.quickMenuBar(ctx),
                Ui.color(ctx, R.color.ig_qm_bar_top_edge),
                Ui.color(ctx, R.color.ig_base_navy),
                Color.TRANSPARENT, 0, 0));
        return tileStrip;
    }

    private View buildDescriptionStrip(Context ctx) {
        descriptionView = Ui.text(ctx, "", 13f, true, Ui.color(ctx, R.color.ig_yellow_accent));
        Ui.ellipsize(descriptionView);
        Ui.padding(descriptionView, ctx, 20, 5, 20, 5);
        Ui.setBackground(descriptionView, Gradients.withEdges(ctx,
                Gradients.quickMenuLabelStrip(ctx),
                Color.TRANSPARENT, Ui.color(ctx, R.color.ig_section_top_edge),
                Color.TRANSPARENT, 0, 0));
        descriptionView.setLayoutParams(Ui.matchWrap());
        return descriptionView;
    }

    private void rebuildTiles() {
        Context ctx = getContext();
        tileStrip.removeAllViews();
        for (int i = 0; i < ITEMS.size(); i++) {
            tileStrip.addView(buildTile(ctx, ITEMS.get(i), i == focusedIndex, i));
        }
        Item focused = ITEMS.get(focusedIndex);
        descriptionView.setText(focused.label + " - " + focused.description);
    }

    private View buildTile(Context ctx, Item item, boolean focused, int index) {
        FrameLayout tile = new FrameLayout(ctx);
        LayoutParams lp = new LayoutParams(Ui.px(ctx, TILE_WIDTH), Ui.px(ctx, TILE_HEIGHT));
        lp.setMarginStart(Ui.px(ctx, index == 0 ? 0 : 8));
        tile.setLayoutParams(lp);
        Ui.setBackground(tile, tileBackground(ctx, focused));

        View content;
        if (item.text != null) {
            TextView label = Ui.text(ctx, item.text, 12f, true, Ui.color(ctx, R.color.ig_white));
            content = label;
        } else {
            ImageView glyph = new ImageView(ctx);
            glyph.setImageResource(item.glyph);
            glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
            content = glyph;
        }
        FrameLayout.LayoutParams contentLp = item.text != null
                ? new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT)
                : new FrameLayout.LayoutParams(Ui.px(ctx, GLYPH_SIZE),
                Ui.px(ctx, GLYPH_SIZE));
        contentLp.gravity = Gravity.CENTER;
        content.setLayoutParams(contentLp);
        tile.addView(content);
        return tile;
    }

    /** Rounded glossy tile with a 2px ring: yellow when focused. */
    private Drawable tileBackground(Context ctx, boolean focused) {
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.RECTANGLE);
        ring.setCornerRadius(Ui.px(ctx, 6));
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(Ui.px(ctx, 2), Ui.color(ctx,
                focused ? R.color.ig_yellow_accent : R.color.ig_qm_tile_border));
        return new LayerDrawable(new Drawable[]{Gradients.quickMenuTile(ctx), ring});
    }

    public void setOnItemChosenListener(OnItemChosenListener listener) {
        this.chosenListener = listener;
    }

    // ---- Visibility --------------------------------------------------------

    public void show() {
        if (showing) {
            return;
        }
        showing = true;
        focusedIndex = 0;
        rebuildTiles();
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

    // ---- Input -------------------------------------------------------------

    /** Handles a D-pad key; returns whether it was consumed. */
    public boolean handleKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                focusedIndex = (focusedIndex - 1 + ITEMS.size()) % ITEMS.size();
                rebuildTiles();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                focusedIndex = (focusedIndex + 1) % ITEMS.size();
                rebuildTiles();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (chosenListener != null) {
                    chosenListener.onItemChosen(ITEMS.get(focusedIndex));
                }
                return true;
            default:
                return false;
        }
    }
}
