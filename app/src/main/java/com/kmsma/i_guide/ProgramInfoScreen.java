package com.kmsma.i_guide;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Full program detail: synopsis, Jellyfin library counts when the server answers, and a
 * row of round action buttons whose description appears in the footer as focus moves.
 */
public class ProgramInfoScreen extends GuideScreen {

    private static final int ICON_SIZE = 38;
    private static final int GLYPH_SIZE = 19;

    /** The actions this build supports. DVR, reminders and locks are out of scope. */
    private enum Action {
        BACK(R.drawable.ic_glyph_back, "Return to the previous screen"),
        WATCH(R.drawable.ic_glyph_eye, "Watch this program now"),
        FAVOURITE(R.drawable.ic_glyph_heart, "Save channel to Favourites"),
        SHOWTIMES(R.drawable.ic_glyph_clock, "View other show times");

        @DrawableRes
        final int glyph;
        final String description;

        Action(@DrawableRes int glyph, String description) {
            this.glyph = glyph;
            this.description = description;
        }
    }

    private static final List<Action> ACTIONS = Arrays.asList(Action.values());

    private final Handler main = new Handler(Looper.getMainLooper());
    private final JellyfinClient jellyfin = new JellyfinClient();

    @Nullable
    private final Channel channel;
    private final Program program;

    private final InfoPanelView infoPanel;
    private final LinearLayout actionRow;
    private final TextView jellyfinLine;

    private int focusedAction = 1; // Watch is the default action.
    private boolean detached;

    public ProgramInfoScreen(@NonNull Context context, @NonNull ScreenHost host,
                             @Nullable Channel channel, @NonNull Program program) {
        super(context, host);
        this.channel = channel;
        this.program = program;

        infoPanel = new InfoPanelView(context);
        infoPanel.setTitleSize(15f);
        infoPanel.setTitle(program.getTitle());
        buildPanelBody(context);
        addContent(infoPanel);

        LinearLayout body = Ui.column(context);
        body.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        Ui.padding(body, context, 20, 14, 20, 14);

        TextView synopsis = Ui.text(context, program.formatSynopsis(), 14f, false,
                Ui.color(context, R.color.ig_white));
        synopsis.setLineSpacing(0f, 1.5f);
        synopsis.setMaxWidth(Ui.px(context, 620));
        // Width must be WRAP_CONTENT for maxWidth to constrain the measure pass.
        synopsis.setLayoutParams(Ui.wrapWrap());
        body.addView(synopsis);

        jellyfinLine = Ui.text(context, "", 12f, false,
                Ui.color(context, R.color.ig_text_jellyfin));
        LayoutParams jellyfinLp = Ui.matchWrap();
        jellyfinLp.topMargin = Ui.px(context, 10);
        jellyfinLine.setLayoutParams(jellyfinLp);
        jellyfinLine.setVisibility(GONE);
        body.addView(jellyfinLine);

        addContent(body);

        actionRow = Ui.row(context);
        actionRow.setLayoutParams(Ui.matchWrap());
        Ui.padding(actionRow, context, 20, 8, 20, 8);
        Ui.setBackground(actionRow, Gradients.withEdges(context,
                new android.graphics.drawable.ColorDrawable(
                        Ui.color(context, R.color.ig_qm_label_bg_1)),
                Ui.color(context, R.color.ig_section_top_edge), Color.TRANSPARENT,
                Color.TRANSPARENT, Ui.px(context, 1), 0));
        addContent(actionRow);

        getFooterBar().setDescriptionMode(true);
        buildActions();
        loadJellyfinInfo();
    }

    private void buildPanelBody(Context ctx) {
        infoPanel.clearDefaultBody();
        LinearLayout body = infoPanel.getBodyContainer();

        body.addView(panelLine(ctx, program.formatTimeRange(), 13f, R.color.ig_white));
        if (channel != null) {
            body.addView(panelLine(ctx, channel.getNumber() + " " + channel.getName(),
                    13f, R.color.ig_white));
        }
        String rating = program.formatRatingLine();
        if (!rating.isEmpty()) {
            body.addView(panelLine(ctx, rating, 12f, R.color.ig_text_menu_desc));
        }
    }

    private TextView panelLine(Context ctx, CharSequence text, float sizeSp, int colorRes) {
        TextView tv = Ui.text(ctx, text, sizeSp, false, Ui.color(ctx, colorRes));
        Ui.ellipsize(tv);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.px(ctx, 3);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void buildActions() {
        Context ctx = getContext();
        actionRow.removeAllViews();
        for (int i = 0; i < ACTIONS.size(); i++) {
            actionRow.addView(buildActionIcon(ctx, ACTIONS.get(i), i == focusedAction, i));
        }
        getFooterBar().setHint(describeAction(ACTIONS.get(focusedAction)));
    }

    private View buildActionIcon(Context ctx, Action action, boolean focused, int index) {
        FrameLayout button = new FrameLayout(ctx);
        int size = Ui.px(ctx, ICON_SIZE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginStart(Ui.px(ctx, index == 0 ? 0 : 10));
        button.setLayoutParams(lp);
        Ui.setBackground(button, actionIconBackground(ctx, focused));

        ImageView glyph = new ImageView(ctx);
        glyph.setImageResource(action.glyph);
        glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int glyphSize = Ui.px(ctx, GLYPH_SIZE);
        FrameLayout.LayoutParams glyphLp = new FrameLayout.LayoutParams(glyphSize, glyphSize);
        glyphLp.gravity = Gravity.CENTER;
        glyph.setLayoutParams(glyphLp);
        button.addView(glyph);
        return button;
    }

    /** Radial blue button with a 2px ring — yellow when focused, near-navy otherwise. */
    private Drawable actionIconBackground(Context ctx, boolean focused) {
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(Ui.px(ctx, 2), Ui.color(ctx,
                focused ? R.color.ig_yellow_accent : R.color.ig_action_icon_border));
        return new LayerDrawable(new Drawable[]{Gradients.actionIcon(ctx), ring});
    }

    /** The Favourite button's label flips to "Remove" once the channel is saved. */
    private String describeAction(Action action) {
        if (action == Action.FAVOURITE && host.getFavourites().isFavourite(channel)) {
            return "Remove channel from Favourites";
        }
        return action.description;
    }

    private void loadJellyfinInfo() {
        jellyfin.fetchLibraryInfo(program.getTitle(), info -> main.post(() -> {
            if (detached || info == null) {
                return;
            }
            String summary = info.summary();
            if (summary.isEmpty()) {
                return;
            }
            jellyfinLine.setText("Jellyfin: " + summary);
            jellyfinLine.setVisibility(VISIBLE);
        }));
    }

    private void invokeAction(Action action) {
        switch (action) {
            case BACK:
                host.popScreen();
                break;
            case WATCH:
                host.tuneTo(channel);
                break;
            case FAVOURITE:
                boolean added = host.getFavourites().toggle(channel);
                Toast.makeText(getContext(), added
                                ? R.string.added_to_favourites
                                : R.string.removed_from_favourites,
                        Toast.LENGTH_SHORT).show();
                buildActions();
                break;
            case SHOWTIMES:
                host.pushScreen(new ListingsByChannelScreen(getContext(), host, channel));
                break;
            default:
                break;
        }
    }

    @Nullable
    @Override
    public View getLivePreviewSlot() {
        return infoPanel.getPreviewSlot();
    }

    @Override
    public void onHidden() {
        detached = true;
        super.onHidden();
    }

    @Override
    public boolean onScreenKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                focusedAction = (focusedAction - 1 + ACTIONS.size()) % ACTIONS.size();
                buildActions();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                focusedAction = (focusedAction + 1) % ACTIONS.size();
                buildActions();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                invokeAction(ACTIONS.get(focusedAction));
                return true;
            default:
                return false;
        }
    }
}
