package com.kmsma.i_guide;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * The dark strip that separates the purple info panel from the listings below it:
 * a yellow caret, the section title, and optional trailing controls (the channel
 * stepper on Listings By Channel, the category tabs on Favourites).
 */
public class SectionBarView extends LinearLayout {

    private TextView titleView;

    public SectionBarView(Context context) {
        super(context);
        init();
    }

    public SectionBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SectionBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        Context ctx = getContext();
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        Ui.setBackground(this, Gradients.withEdges(ctx, Gradients.sectionBar(ctx),
                Ui.color(ctx, R.color.ig_section_top_edge),
                Ui.color(ctx, R.color.ig_section_bottom_edge),
                android.graphics.Color.TRANSPARENT,
                Ui.px(ctx, 1), Ui.px(ctx, 1)));
        Ui.padding(this, ctx, 14, 4, 14, 4);

        TextView caret = Ui.text(ctx, "▸", 11f, true, Ui.color(ctx, R.color.ig_yellow_accent));
        LayoutParams caretLp = Ui.wrapWrap();
        caretLp.setMarginEnd(Ui.px(ctx, 8));
        caret.setLayoutParams(caretLp);
        addView(caret);

        titleView = Ui.text(ctx, "", 13f, true, Ui.color(ctx, R.color.ig_white));
        Ui.ellipsize(titleView);
        titleView.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        addView(titleView);
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
    }

    /** Appends a trailing control, right-aligned after the title. */
    public void addTrailing(View view) {
        addView(view);
    }
}
