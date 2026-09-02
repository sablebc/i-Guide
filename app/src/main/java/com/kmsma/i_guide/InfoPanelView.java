package com.kmsma.i_guide;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The purple program-detail panel that caps every guide page, paired with the boxed
 * live-video preview on its right. Screens either use {@link #setTimeAndDescription}
 * for the standard two-line body or add their own views to {@link #getBodyContainer()}.
 */
public class InfoPanelView extends LinearLayout {

    public static final int HEIGHT_GUIDE = 132;
    public static final int HEIGHT_MENU = 158;
    public static final int PREVIEW_WIDTH_GUIDE = 236;
    public static final int PREVIEW_WIDTH_MENU = 286;

    private TextView titleView;
    private TextView titleTrailingView;
    private LinearLayout bodyContainer;
    private TextView timeView;
    private TextView descriptionView;
    private FrameLayout previewSlot;
    private TextView previewPlaceholder;

    public InfoPanelView(Context context) {
        super(context);
        init();
    }

    public InfoPanelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public InfoPanelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        Context ctx = getContext();
        setOrientation(HORIZONTAL);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(ctx, HEIGHT_GUIDE)));

        addView(buildPanel(ctx));
        addView(buildPreview(ctx));
        setTimeAndDescription(null, null);
    }

    private View buildPanel(Context ctx) {
        LinearLayout panel = Ui.column(ctx);
        panel.setLayoutParams(new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        // The 2dp navy edge on the right separates the panel from the preview box.
        Ui.setBackground(panel, Gradients.purplePanel(ctx));

        LinearLayout titleBar = Ui.row(ctx);
        titleBar.setLayoutParams(Ui.matchWrap());
        Ui.setBackground(titleBar, Gradients.withEdges(ctx, Gradients.titleBar(ctx),
                Color.TRANSPARENT, Ui.color(ctx, R.color.ig_title_bar_edge), Color.TRANSPARENT,
                0, Ui.px(ctx, 1)));
        Ui.padding(titleBar, ctx, 14, 5, 14, 5);

        titleView = Ui.text(ctx, "", 14f, true, Ui.color(ctx, R.color.ig_white));
        Ui.ellipsize(titleView);
        titleView.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        titleBar.addView(titleView);

        titleTrailingView = Ui.text(ctx, "", 12f, true, Ui.color(ctx, R.color.ig_white));
        LayoutParams trailingLp = Ui.wrapWrap();
        trailingLp.setMarginStart(Ui.px(ctx, 10));
        titleTrailingView.setLayoutParams(trailingLp);
        titleTrailingView.setVisibility(GONE);
        titleBar.addView(titleTrailingView);

        panel.addView(titleBar);

        bodyContainer = Ui.column(ctx);
        bodyContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        Ui.padding(bodyContainer, ctx, 14, 8, 14, 8);
        bodyContainer.setClipChildren(true);

        timeView = Ui.text(ctx, "", 12f, false, Ui.color(ctx, R.color.ig_text_panel));
        bodyContainer.addView(timeView);

        descriptionView = Ui.text(ctx, "", 12f, false, Ui.color(ctx, R.color.ig_white));
        descriptionView.setLineSpacing(0f, 1.4f);
        LayoutParams descLp = Ui.matchWrap();
        descLp.topMargin = Ui.px(ctx, 4);
        descriptionView.setLayoutParams(descLp);
        bodyContainer.addView(descriptionView);

        panel.addView(bodyContainer);
        return panel;
    }

    private View buildPreview(Context ctx) {
        previewSlot = new FrameLayout(ctx);
        LayoutParams lp = new LayoutParams(Ui.px(ctx, PREVIEW_WIDTH_GUIDE),
                LayoutParams.MATCH_PARENT);
        lp.setMarginStart(Ui.px(ctx, 2));
        previewSlot.setLayoutParams(lp);
        Ui.setBackground(previewSlot, Gradients.withEdges(ctx, Gradients.previewBox(ctx),
                Color.TRANSPARENT, Ui.color(ctx, R.color.ig_base_navy), Color.TRANSPARENT,
                0, Ui.px(ctx, 2)));

        previewPlaceholder = Ui.text(ctx, getContext().getString(R.string.live_preview), 12f,
                false, Ui.color(ctx, R.color.ig_preview_text));
        previewPlaceholder.setTypeface(Typeface.MONOSPACE);
        FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        textLp.gravity = Gravity.CENTER;
        previewPlaceholder.setLayoutParams(textLp);
        previewSlot.addView(previewPlaceholder);
        return previewSlot;
    }

    // ---- Configuration -----------------------------------------------------

    /** Switches to the taller Main Menu proportions (158 tall, 286 wide preview). */
    public void useMenuProportions() {
        Context ctx = getContext();
        getLayoutParams().height = Ui.px(ctx, HEIGHT_MENU);
        previewSlot.getLayoutParams().width = Ui.px(ctx, PREVIEW_WIDTH_MENU);
        requestLayout();
    }

    public void setTitle(@Nullable CharSequence title) {
        titleView.setText(title != null ? title : "");
    }

    /** Larger title weight used by the Program Information screen. */
    public void setTitleSize(float designSize) {
        Ui.setTextSize(titleView, designSize);
    }

    /** Right-aligned text in the title bar, e.g. the channel number on Listings By Time. */
    public void setTitleTrailing(@Nullable CharSequence text) {
        if (text == null || text.length() == 0) {
            titleTrailingView.setVisibility(GONE);
        } else {
            titleTrailingView.setText(text);
            titleTrailingView.setVisibility(VISIBLE);
        }
    }

    public void setTimeAndDescription(@Nullable CharSequence time, @Nullable CharSequence desc) {
        timeView.setText(time != null ? time : "");
        timeView.setVisibility(time == null || time.length() == 0 ? GONE : VISIBLE);
        descriptionView.setText(desc != null ? desc : "");
        descriptionView.setVisibility(desc == null || desc.length() == 0 ? GONE : VISIBLE);
    }

    /** The panel body, for screens that need their own stack of lines. */
    @NonNull
    public LinearLayout getBodyContainer() {
        return bodyContainer;
    }

    /** Removes the default time/description pair so a screen can supply its own body. */
    public void clearDefaultBody() {
        bodyContainer.removeAllViews();
    }

    /** The rectangle the live video is scaled into while a guide page is on screen. */
    @NonNull
    public View getPreviewSlot() {
        return previewSlot;
    }

    /** Hides the "live preview" placeholder once real video is behind the slot. */
    public void setPreviewPlaceholderVisible(boolean visible) {
        previewPlaceholder.setVisibility(visible ? VISIBLE : GONE);
    }
}
