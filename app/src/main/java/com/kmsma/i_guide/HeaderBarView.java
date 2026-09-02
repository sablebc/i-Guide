package com.kmsma.i_guide;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.Locale;

/**
 * The 42px glossy blue bar across the top of every full-screen guide page: the italic
 * DIGITAL CABLE brand block on the left, a live clock and the round "tv" badge on the
 * right.
 */
public class HeaderBarView extends LinearLayout {

    private static final int HEIGHT = 42;
    private static final long CLOCK_TICK_MS = 10_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, CLOCK_TICK_MS);
        }
    };

    private TextView clockView;

    public HeaderBarView(Context context) {
        super(context);
        init();
    }

    public HeaderBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HeaderBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        Context ctx = getContext();
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(ctx, HEIGHT)));
        Ui.setBackground(this, Gradients.withEdges(ctx, Gradients.headerBar(ctx),
                Color.TRANSPARENT, Ui.color(ctx, R.color.ig_base_navy), Color.TRANSPARENT,
                0, Ui.px(ctx, 2)));

        addView(buildBrandBlock(ctx));
        addView(divider(ctx, R.color.ig_header_divider));
        addView(Ui.spacer(ctx));

        clockView = Ui.text(ctx, "", 15f, true, Ui.color(ctx, R.color.ig_white));
        LayoutParams clockLp = Ui.wrapWrap();
        clockLp.setMarginEnd(Ui.px(ctx, 14));
        clockView.setLayoutParams(clockLp);
        addView(clockView);

        addView(buildBadge(ctx));
        updateClock();
    }

    private View buildBrandBlock(Context ctx) {
        LinearLayout brand = Ui.column(ctx);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        Ui.setBackground(brand, Gradients.headerBrand(ctx));
        brand.setPadding(Ui.px(ctx, 10), Ui.px(ctx, 4), Ui.px(ctx, 14), Ui.px(ctx, 4));

        brand.addView(brandLine(ctx, getContext().getString(R.string.brand_line_1)));
        brand.addView(brandLine(ctx, getContext().getString(R.string.brand_line_2)));
        return brand;
    }

    private TextView brandLine(Context ctx, String label) {
        TextView tv = Ui.text(ctx, label, 13f, true, Ui.color(ctx, R.color.ig_white));
        tv.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC);
        tv.setLetterSpacing(0.02f);
        tv.setLineSpacing(0f, 1.05f);
        return tv;
    }

    private View buildBadge(Context ctx) {
        int size = Ui.px(ctx, 30);
        TextView badge = Ui.text(ctx, getContext().getString(R.string.brand_badge), 13f, true,
                Ui.color(ctx, R.color.ig_white));
        badge.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC);
        badge.setGravity(Gravity.CENTER);
        Ui.setBackground(badge, Gradients.headerBadge(ctx));

        LayoutParams lp = new LayoutParams(size, size);
        lp.setMarginEnd(Ui.px(ctx, 12));
        badge.setLayoutParams(lp);
        return badge;
    }

    private View divider(Context ctx, int colorRes) {
        View v = new View(ctx);
        v.setLayoutParams(new LayoutParams(Ui.px(ctx, 1), LayoutParams.MATCH_PARENT));
        v.setBackgroundColor(Ui.color(ctx, colorRes));
        return v;
    }

    /** {@code 8:04pm}, matching the reference's lowercase, space-free clock. */
    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR);
        if (hour == 0) {
            hour = 12;
        }
        clockView.setText(String.format(Locale.US, "%d:%02d%s",
                hour, now.get(Calendar.MINUTE),
                now.get(Calendar.AM_PM) == Calendar.AM ? "am" : "pm"));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.postDelayed(clockTick, CLOCK_TICK_MS);
        updateClock();
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(clockTick);
        super.onDetachedFromWindow();
    }
}
