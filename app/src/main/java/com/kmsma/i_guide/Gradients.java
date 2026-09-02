package com.kmsma.i_guide;

import android.content.Context;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.drawable.shapes.RoundRectShape;
import android.view.Gravity;

import androidx.core.content.ContextCompat;

/**
 * Every background in the i-Guide look is a multi-stop gradient with a hard "gloss"
 * break part-way down (for example #4a74c0 at 45% jumping to #2f54a0 at 52%). The
 * android:gradient XML tag only supports evenly spaced start/center/end colours, so
 * these are built from {@link LinearGradient} shaders with the explicit stop
 * positions taken straight from the design reference.
 */
public final class Gradients {

    private Gradients() {
    }

    // ---- Primitives --------------------------------------------------------

    /** Vertical (top to bottom) gradient with explicit stop positions. */
    public static Drawable vertical(final int[] colors, final float[] stops) {
        ShapeDrawable d = new ShapeDrawable(new RectShape());
        d.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                return new LinearGradient(0, 0, 0, Math.max(height, 1),
                        colors, stops, Shader.TileMode.CLAMP);
            }
        });
        return d;
    }

    /** Vertical gradient clipped to a rounded rectangle. */
    public static Drawable verticalRounded(final int[] colors, final float[] stops, float[] radiiPx) {
        ShapeDrawable d = new ShapeDrawable(new RoundRectShape(radiiPx, null, null));
        d.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                return new LinearGradient(0, 0, 0, Math.max(height, 1),
                        colors, stops, Shader.TileMode.CLAMP);
            }
        });
        return d;
    }

    /** Diagonal (top-left to bottom-right) gradient, the CSS 135deg equivalent. */
    public static Drawable diagonal(final int[] colors, final float[] stops) {
        ShapeDrawable d = new ShapeDrawable(new RectShape());
        d.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                return new LinearGradient(0, 0, Math.max(width, 1), Math.max(height, 1),
                        colors, stops, Shader.TileMode.CLAMP);
            }
        });
        return d;
    }

    /** Radial highlight offset toward the upper-left, used for the round action icons. */
    public static Drawable radialOval(final int[] colors, final float[] stops,
                                      final float focusX, final float focusY) {
        ShapeDrawable d = new ShapeDrawable(new OvalShape());
        d.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                int w = Math.max(width, 1);
                int h = Math.max(height, 1);
                return new RadialGradient(w * focusX, h * focusY, Math.max(w, h),
                        colors, stops, Shader.TileMode.CLAMP);
            }
        });
        return d;
    }

    /**
     * Stacks hairline edges on top of a background, reproducing the CSS border and
     * "inset 0 1px 0" highlights used all over the design. Any edge colour may be
     * {@link Color#TRANSPARENT} to skip that edge.
     */
    public static Drawable withEdges(Context ctx, Drawable base,
                                     int topColor, int bottomColor, int leftColor,
                                     int topPx, int bottomPx) {
        int count = 1;
        if (topColor != Color.TRANSPARENT) count++;
        if (bottomColor != Color.TRANSPARENT) count++;
        if (leftColor != Color.TRANSPARENT) count++;

        Drawable[] layers = new Drawable[count];
        int[] gravities = new int[count];
        int[] widths = new int[count];
        int[] heights = new int[count];

        int i = 0;
        layers[i] = base;
        gravities[i] = Gravity.FILL;
        widths[i] = -1;
        heights[i] = -1;
        i++;

        int hair = Ui.px(ctx, 1);
        if (topColor != Color.TRANSPARENT) {
            layers[i] = new ColorDrawable(topColor);
            gravities[i] = Gravity.TOP | Gravity.FILL_HORIZONTAL;
            widths[i] = -1;
            heights[i] = Math.max(topPx, hair);
            i++;
        }
        if (bottomColor != Color.TRANSPARENT) {
            layers[i] = new ColorDrawable(bottomColor);
            gravities[i] = Gravity.BOTTOM | Gravity.FILL_HORIZONTAL;
            widths[i] = -1;
            heights[i] = Math.max(bottomPx, hair);
            i++;
        }
        if (leftColor != Color.TRANSPARENT) {
            layers[i] = new ColorDrawable(leftColor);
            gravities[i] = Gravity.LEFT | Gravity.FILL_VERTICAL;
            widths[i] = hair;
            heights[i] = -1;
        }

        LayerDrawable ld = new LayerDrawable(layers);
        for (int n = 0; n < count; n++) {
            ld.setLayerGravity(n, gravities[n]);
            if (widths[n] > 0) {
                ld.setLayerWidth(n, widths[n]);
            }
            if (heights[n] > 0) {
                ld.setLayerHeight(n, heights[n]);
            }
        }
        return ld;
    }

    private static int c(Context ctx, int res) {
        return ContextCompat.getColor(ctx, res);
    }

    // ---- Named surfaces from the design reference --------------------------

    /** Full-screen guide background: #1c2c74 to #101c54 at 40% to #0a1440. */
    public static Drawable screenBackground(Context ctx) {
        return vertical(new int[]{
                        c(ctx, R.color.ig_screen_bg_1),
                        c(ctx, R.color.ig_screen_bg_2),
                        c(ctx, R.color.ig_screen_bg_3)},
                new float[]{0f, 0.40f, 1f});
    }

    /** 42px glossy blue header bar. */
    public static Drawable headerBar(Context ctx) {
        return vertical(new int[]{
                        c(ctx, R.color.ig_header_1),
                        c(ctx, R.color.ig_header_2),
                        c(ctx, R.color.ig_header_3),
                        c(ctx, R.color.ig_header_4)},
                new float[]{0f, 0.45f, 0.52f, 1f});
    }

    /** Darker DIGITAL CABLE block at the left edge of the header. */
    public static Drawable headerBrand(Context ctx) {
        return vertical(new int[]{
                        c(ctx, R.color.ig_header_brand_1),
                        c(ctx, R.color.ig_header_brand_2)},
                new float[]{0f, 1f});
    }

    /** Round "tv" badge at the right edge of the header. */
    public static Drawable headerBadge(Context ctx) {
        return radialOval(new int[]{
                        c(ctx, R.color.ig_header_badge_1),
                        c(ctx, R.color.ig_header_badge_2)},
                new float[]{0f, 0.70f}, 0.35f, 0.30f);
    }

    /** Purple program-info panel. */
    public static Drawable purplePanel(Context ctx) {
        return vertical(new int[]{
                        c(ctx, R.color.ig_purple_panel_1),
                        c(ctx, R.color.ig_purple_panel_2),
                        c(ctx, R.color.ig_purple_panel_3)},
                new float[]{0f, 0.55f, 1f});
    }

    /** Light-blue title strip that caps the purple panel. */
    public static Drawable titleBar(Context ctx) {
        return vertical(new int[]{
                        c(ctx, R.color.ig_title_bar_1),
                        c(ctx, R.color.ig_title_bar_2),
                        c(ctx, R.color.ig_title_bar_3)},
                new float[]{0f, 0.5f, 1f});
    }

    /** Dark section bar (time header and the "Menu" strip). */
    public static Drawable sectionBar(Context ctx) {
        return vertical(new int[]{
                        c(ctx, R.color.ig_section_bar_1),
                        c(ctx, R.color.ig_section_bar_2)},
                new float[]{0f, 1f});
    }

    /** Blue navigation-hint footer. */
    public static Drawable footerBar(Context ctx) {
        return vertical(new int[]{
                        c(ctx, R.color.ig_footer_1),
                        c(ctx, R.color.ig_footer_2),
                        c(ctx, R.color.ig_footer_3)},
                new float[]{0f, 0.5f, 1f});
    }

    /** Channel-number column down the left edge of every listing grid. */
    public static Drawable channelColumn(Context ctx) {
        return vertical(new int[]{
                        c(ctx, R.color.ig_channel_number_1),
                        c(ctx, R.color.ig_channel_number_2),
                        c(ctx, R.color.ig_channel_number_3)},
                new float[]{0f, 0.5f, 1f});
    }

    /** Yellow selection fill: the focus state for every list and grid cell. */
    public static Drawable selectedCell(Context ctx) {
        return vertical(new int[]{
                        c(ctx, R.color.ig_selected_1),
                        c(ctx, R.color.ig_selected_2),
                        c(ctx, R.color.ig_selected_3),
                        c(ctx, R.color.ig_selected_4)},
                new float[]{0f, 0.45f, 0.55f, 1f});
    }

    /** Category-coded program cell. */
    public static Drawable categoryCell(Context ctx, ProgramCategory category) {
        int[] res = category.cellColors();
        return vertical(new int[]{c(ctx, res[0]), c(ctx, res[1]), c(ctx, res[2]), c(ctx, res[3])},
                new float[]{0f, 0.48f, 0.52f, 1f});
    }

    /** Diagonal placeholder fill behind the live-video preview slot. */
    public static Drawable previewBox(Context ctx) {
        return diagonal(new int[]{
                        c(ctx, R.color.ig_preview_1),
                        c(ctx, R.color.ig_preview_2),
                        c(ctx, R.color.ig_preview_3)},
                new float[]{0f, 0.70f, 1f});
    }

    /** Backdrop shown behind overlays when no video is playing yet. */
    public static Drawable liveBackdrop(Context ctx) {
        return diagonal(new int[]{
                        c(ctx, R.color.ig_live_backdrop_1),
                        c(ctx, R.color.ig_live_backdrop_2),
                        c(ctx, R.color.ig_live_backdrop_3)},
                new float[]{0f, 0.55f, 1f});
    }

    /** Quick Menu icon strip. */
    public static Drawable quickMenuBar(Context ctx) {
        return vertical(new int[]{
                        c(ctx, R.color.ig_qm_bar_1),
                        c(ctx, R.color.ig_qm_bar_2),
                        c(ctx, R.color.ig_qm_bar_3),
                        c(ctx, R.color.ig_qm_bar_4)},
                new float[]{0f, 0.45f, 0.55f, 1f});
    }

    /** Rounded glossy Quick Menu tile. */
    public static Drawable quickMenuTile(Context ctx) {
        float r = Ui.px(ctx, 6);
        return verticalRounded(new int[]{
                        c(ctx, R.color.ig_qm_tile_1),
                        c(ctx, R.color.ig_qm_tile_2),
                        c(ctx, R.color.ig_qm_tile_3),
                        c(ctx, R.color.ig_qm_tile_4)},
                new float[]{0f, 0.48f, 0.52f, 1f},
                new float[]{r, r, r, r, r, r, r, r});
    }

    /** Purple "Quick Menu" tab above the icon strip: rounded top-right corner only. */
    public static Drawable quickMenuTab(Context ctx) {
        float r = Ui.px(ctx, 8);
        return verticalRounded(new int[]{
                        c(ctx, R.color.ig_flip_bar_purple_1),
                        c(ctx, R.color.ig_flip_bar_purple_2),
                        c(ctx, R.color.ig_flip_bar_purple_3)},
                new float[]{0f, 0.5f, 1f},
                new float[]{0f, 0f, r, r, 0f, 0f, 0f, 0f});
    }

    /** Translucent strip under the Quick Menu holding the focused item's description. */
    public static Drawable quickMenuLabelStrip(Context ctx) {
        return vertical(new int[]{
                        c(ctx, R.color.ig_qm_label_bg_1),
                        c(ctx, R.color.ig_qm_label_bg_2)},
                new float[]{0f, 1f});
    }

    /** Round, radially lit action button on the Program Information screen. */
    public static Drawable actionIcon(Context ctx) {
        return radialOval(new int[]{
                        c(ctx, R.color.ig_action_icon_1),
                        c(ctx, R.color.ig_action_icon_2),
                        c(ctx, R.color.ig_action_icon_3)},
                new float[]{0f, 0.55f, 1f}, 0.35f, 0.28f);
    }

    /** Transparent to navy scrim that blends live video into a bottom overlay. */
    public static Drawable topFade(Context ctx, boolean strong) {
        return vertical(new int[]{
                        Color.TRANSPARENT,
                        c(ctx, strong ? R.color.ig_scrim_strong : R.color.ig_scrim_soft)},
                new float[]{0f, 1f});
    }
}
