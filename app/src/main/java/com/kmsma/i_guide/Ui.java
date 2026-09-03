package com.kmsma.i_guide;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Small view-construction helpers. The i-Guide screens are built in code rather than
 * layout XML because nearly every surface needs a multi-stop gradient that XML
 * drawables cannot express (see {@link Gradients}).
 */
public final class Ui {

    /** The 0 1px 1px rgba(0,0,0,0.5) text shadow used throughout the design. */
    private static final int TEXT_SHADOW = 0x80000000;

    /** Width of the design reference's canvas, in its own units. */
    private static final float DESIGN_WIDTH = 1280f;

    private Ui() {
    }

    /**
     * How many real pixels one design unit is worth on this panel. The reference was
     * drawn on a 1280-wide canvas, so scaling by screen width reproduces its
     * proportions exactly at 720p, 1080p or 4K rather than letting density round them
     * into something chunkier.
     */
    public static float scale(Context ctx) {
        return ctx.getResources().getDisplayMetrics().widthPixels / DESIGN_WIDTH;
    }

    /** Converts a measurement taken from the design reference into device pixels. */
    public static int px(Context ctx, float designUnits) {
        return Math.round(designUnits * scale(ctx));
    }

    public static int color(Context ctx, @ColorRes int res) {
        return ContextCompat.getColor(ctx, res);
    }

    // ---- Containers --------------------------------------------------------

    public static LinearLayout row(Context ctx) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context ctx) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    // ---- Layout params -----------------------------------------------------

    public static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Weighted child that fills the remaining space along the parent's axis. */
    public static LinearLayout.LayoutParams weight(int orientation, float w) {
        if (orientation == LinearLayout.HORIZONTAL) {
            return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w);
        }
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, w);
    }

    // ---- Text --------------------------------------------------------------

    /** Body text in the design's default white with the standard drop shadow. */
    public static TextView text(Context ctx, CharSequence s, float designSize, boolean bold,
                                int color) {
        TextView tv = new TextView(ctx);
        tv.setText(s);
        setTextSize(tv, designSize);
        tv.setTypeface(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL);
        tv.setTextColor(color);
        tv.setIncludeFontPadding(false);
        shadow(tv, true);
        return tv;
    }

    /** Sets a type size expressed in design units. */
    public static void setTextSize(TextView tv, float designSize) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, designSize * scale(tv.getContext()));
    }

    /** Toggles the design's 1px drop shadow; selected (dark-on-yellow) text has none. */
    public static void shadow(TextView tv, boolean on) {
        if (on) {
            tv.setShadowLayer(1f, 0f, 1f, TEXT_SHADOW);
        } else {
            tv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        }
    }

    /** Single line, ellipsised at the end: the standard treatment for cell titles. */
    public static TextView ellipsize(TextView tv) {
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return tv;
    }

    // ---- Icons -------------------------------------------------------------

    public static ImageView icon(Context ctx, @DrawableRes int res, int designSize) {
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(ContextCompat.getDrawable(ctx, res));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = px(ctx, designSize);
        iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return iv;
    }

    /**
     * An empty, fixed-size box for artwork fetched at runtime. Sized in design units so
     * the slot holds its width whether or not the image ever arrives.
     */
    public static ImageView imageSlot(Context ctx, float designWidth, float designHeight) {
        ImageView iv = new ImageView(ctx);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setLayoutParams(new LinearLayout.LayoutParams(px(ctx, designWidth),
                px(ctx, designHeight)));
        return iv;
    }

    // ---- Misc --------------------------------------------------------------

    @SuppressWarnings("deprecation")
    public static void setBackground(@NonNull View v, Drawable d) {
        v.setBackground(d);
    }

    public static void padding(View v, Context ctx, float l, float t, float r, float b) {
        v.setPadding(px(ctx, l), px(ctx, t), px(ctx, r), px(ctx, b));
    }

    /** A flexible spacer that eats the remaining space in a horizontal row. */
    public static View spacer(Context ctx) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }
}
