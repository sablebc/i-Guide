package com.kmsma.i_guide;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * Row parts shared by the listing screens: the fixed left column and the wide
 * selectable body cell, both carrying the design's hairline borders and top highlight.
 */
public final class RowViews {

    public static final int LEFT_COLUMN_WIDTH = 74;

    private RowViews() {
    }

    /** The dark blue column that holds a channel number or a start time. */
    @NonNull
    public static LinearLayout leftColumn(@NonNull Context ctx) {
        LinearLayout cell = Ui.row(ctx);
        cell.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.px(ctx, LEFT_COLUMN_WIDTH), LinearLayout.LayoutParams.MATCH_PARENT));
        cell.setPadding(Ui.px(ctx, 6), 0, Ui.px(ctx, 6), 0);
        Ui.setBackground(cell, Gradients.withEdges(ctx, Gradients.channelColumn(ctx),
                Ui.color(ctx, R.color.ig_cell_highlight),
                Ui.color(ctx, R.color.ig_grid_border),
                Color.TRANSPARENT, 0, 0));
        return cell;
    }

    /** Channel number plus call sign, as they appear in the left column. */
    @NonNull
    public static LinearLayout channelColumn(@NonNull Context ctx, @NonNull Channel channel) {
        LinearLayout cell = leftColumn(ctx);

        TextView number = Ui.text(ctx, String.valueOf(channel.getNumber()), 12f, true,
                Ui.color(ctx, R.color.ig_white));
        number.setGravity(Gravity.END);
        number.setMinWidth(Ui.px(ctx, 18));
        cell.addView(number);

        TextView call = Ui.text(ctx, channel.getName() != null ? channel.getName() : "", 11f,
                false, Ui.color(ctx, R.color.ig_text_channel));
        Ui.ellipsize(call);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(Ui.px(ctx, 5));
        call.setLayoutParams(lp);
        cell.addView(call);
        return cell;
    }

    /** A single left-column label, used for the time column on Listings By Channel. */
    @NonNull
    public static LinearLayout labelColumn(@NonNull Context ctx, @NonNull CharSequence label) {
        LinearLayout cell = leftColumn(ctx);
        cell.setPadding(Ui.px(ctx, 8), 0, Ui.px(ctx, 8), 0);
        cell.addView(Ui.text(ctx, label, 12f, true, Ui.color(ctx, R.color.ig_white)));
        return cell;
    }

    /**
     * The wide body cell: category blue normally, the yellow selection gradient when
     * highlighted. Weighted so it fills whatever the left column leaves behind.
     */
    @NonNull
    public static LinearLayout bodyCell(@NonNull Context ctx, boolean selected) {
        return bodyCell(ctx, selected, ProgramCategory.REGULAR);
    }

    @NonNull
    public static LinearLayout bodyCell(@NonNull Context ctx, boolean selected,
                                        @NonNull ProgramCategory category) {
        LinearLayout cell = Ui.row(ctx);
        cell.setLayoutParams(
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        cell.setPadding(Ui.px(ctx, 10), 0, Ui.px(ctx, 10), 0);
        Ui.setBackground(cell, Gradients.withEdges(ctx,
                selected ? Gradients.selectedCell(ctx) : Gradients.categoryCell(ctx, category),
                Ui.color(ctx, selected
                        ? R.color.ig_cell_highlight_selected : R.color.ig_cell_highlight),
                Ui.color(ctx, R.color.ig_grid_border),
                Ui.color(ctx, R.color.ig_grid_border), 0, 0));
        return cell;
    }

    /** Primary cell text: white with a drop shadow, or bold near-black when selected. */
    @NonNull
    public static TextView cellText(@NonNull Context ctx, CharSequence text, boolean selected) {
        TextView tv = Ui.text(ctx, text, 12f, selected,
                Ui.color(ctx, selected ? R.color.ig_selected_text : R.color.ig_white));
        Ui.ellipsize(tv);
        Ui.shadow(tv, !selected);
        return tv;
    }

    /** Secondary trailing text inside a body cell, e.g. the air time on Favourites. */
    @NonNull
    public static TextView cellSubText(@NonNull Context ctx, CharSequence text, boolean selected) {
        TextView tv = Ui.text(ctx, text, 11f, false, Ui.color(ctx,
                selected ? R.color.ig_text_selected_sub : R.color.ig_text_fav_time));
        Ui.shadow(tv, !selected);
        LinearLayout.LayoutParams lp = Ui.wrapWrap();
        lp.setMarginStart(Ui.px(ctx, 10));
        tv.setLayoutParams(lp);
        return tv;
    }
}
