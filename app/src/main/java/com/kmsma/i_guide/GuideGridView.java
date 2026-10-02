package com.kmsma.i_guide;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The listings grid: a fixed channel column on the left and, to its right, program
 * cells whose widths are proportional to how much of the visible time window each
 * program occupies.
 *
 * <p>Cell widths come from {@link LinearLayout} weights measured in minutes of overlap
 * with the window, against a {@code weightSum} of the window's total minutes. A
 * half-hour show therefore takes exactly one slot's width, a two-hour movie four, and a
 * program clipped by the window edge takes only the part that is actually visible.
 *
 * <p>Selection is painted rather than focused: the grid keeps its own row/column cursor
 * and renders the selected cell in yellow, because Android's focus search cannot
 * traverse rows whose cells do not line up.
 */
public class GuideGridView extends LinearLayout {

    private static final long SLOT_MS = 30L * 60L * 1000L;
    private static final int DEFAULT_SLOT_COUNT = 4;

    private static final int ROW_HEIGHT = 30;
    private static final int TIME_HEADER_HEIGHT = 24;
    private static final int CHANNEL_COL_WIDTH = 96;
    private static final int LOGO_WIDTH = 22;
    private static final int LOGO_HEIGHT = 20;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(@Nullable Channel channel, @Nullable Program program);
    }

    public interface OnProgramChosenListener {
        void onProgramChosen(@NonNull Channel channel, @NonNull Program program);
    }

    private final EpgRepository epg = EpgRepository.get();

    private LinearLayout timeHeader;
    private LinearLayout rowsContainer;

    private List<Channel> channels = Collections.emptyList();
    private long windowStart;
    private int slotCount = DEFAULT_SLOT_COUNT;

    private int selectedRow;
    private int firstVisibleRow;
    private int visibleRowCount = 1;
    /** Start of the selected program, used to keep the column steady across rows. */
    private long anchorMs;

    private OnSelectionChangedListener selectionListener;
    private OnProgramChosenListener chosenListener;

    public GuideGridView(Context context) {
        super(context);
        init();
    }

    public GuideGridView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GuideGridView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        Context ctx = getContext();
        setOrientation(VERTICAL);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        windowStart = alignToSlot(System.currentTimeMillis());
        anchorMs = windowStart;

        timeHeader = Ui.row(ctx);
        timeHeader.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                Ui.px(ctx, TIME_HEADER_HEIGHT)));
        Ui.setBackground(timeHeader, Gradients.withEdges(ctx, Gradients.sectionBar(ctx),
                Ui.color(ctx, R.color.ig_section_top_edge),
                Ui.color(ctx, R.color.ig_section_bottom_edge),
                Color.TRANSPARENT, Ui.px(ctx, 1), Ui.px(ctx, 1)));
        addView(timeHeader);

        rowsContainer = Ui.column(ctx);
        rowsContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        rowsContainer.setClipChildren(true);
        addView(rowsContainer);
    }

    // ---- Configuration -----------------------------------------------------

    public void setChannels(@NonNull List<Channel> channels) {
        this.channels = channels;
        if (selectedRow >= channels.size()) {
            selectedRow = Math.max(0, channels.size() - 1);
        }
        rebuild();
    }

    /** Number of 30-minute columns shown at once. */
    public void setSlotCount(int slotCount) {
        this.slotCount = Math.max(1, slotCount);
        rebuild();
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener l) {
        this.selectionListener = l;
    }

    public void setOnProgramChosenListener(OnProgramChosenListener l) {
        this.chosenListener = l;
    }

    /** Moves the cursor to a channel, scrolling it into view. */
    public void selectChannel(@Nullable Channel channel) {
        if (channel == null || channel.getId() == null) {
            return;
        }
        // Searched against this grid's own list, which may be a filtered subset.
        for (int i = 0; i < channels.size(); i++) {
            if (channel.getId().equals(channels.get(i).getId())) {
                selectedRow = i;
                ensureRowVisible();
                rebuild();
                return;
            }
        }
    }

    @Nullable
    public Channel getSelectedChannel() {
        if (selectedRow < 0 || selectedRow >= channels.size()) {
            return null;
        }
        return channels.get(selectedRow);
    }

    @Nullable
    public Program getSelectedProgram() {
        List<Program> cells = cellsFor(getSelectedChannel());
        return cellAt(cells, anchorMs);
    }

    public void refresh() {
        rebuild();
    }

    // ---- Geometry ----------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recomputeVisibleRows();
    }

    private void recomputeVisibleRows() {
        int available = rowsContainer.getHeight();
        if (available <= 0) {
            available = getHeight() - Ui.px(getContext(), TIME_HEADER_HEIGHT);
        }
        int rowHeight = Ui.px(getContext(), ROW_HEIGHT);
        int count = Math.max(1, available / rowHeight);
        if (count != visibleRowCount) {
            visibleRowCount = count;
            ensureRowVisible();
            // Posted, not called inline: this runs from onSizeChanged, and views added
            // during a layout pass never get measured.
            post(this::rebuild);
        }
    }

    private long windowEnd() {
        return windowStart + slotCount * SLOT_MS;
    }

    private static long alignToSlot(long timeMs) {
        return timeMs - (timeMs % SLOT_MS);
    }

    // ---- Rendering ---------------------------------------------------------

    private void rebuild() {
        if (getContext() == null) {
            return;
        }
        buildTimeHeader();
        buildRows();
        notifySelection();
    }

    private void buildTimeHeader() {
        Context ctx = getContext();
        timeHeader.removeAllViews();

        TextView caret = Ui.text(ctx, "▸", 11f, true, Ui.color(ctx, R.color.ig_yellow_accent));
        LayoutParams caretLp = new LayoutParams(Ui.px(ctx, CHANNEL_COL_WIDTH),
                LayoutParams.MATCH_PARENT);
        caret.setLayoutParams(caretLp);
        caret.setGravity(Gravity.CENTER_VERTICAL);
        caret.setPadding(Ui.px(ctx, 10), 0, 0, 0);
        timeHeader.addView(caret);

        for (int i = 0; i < slotCount; i++) {
            long slotStart = windowStart + i * SLOT_MS;
            TextView label = Ui.text(ctx, Program.formatSlotHeader(slotStart), 12f, true,
                    Ui.color(ctx, R.color.ig_white));
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setPadding(Ui.px(ctx, 8), 0, 0, 0);
            Ui.setBackground(label, Gradients.withEdges(ctx,
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),
                    Color.TRANSPARENT, Color.TRANSPARENT, Ui.color(ctx, R.color.ig_tab_border),
                    0, 0));
            label.setLayoutParams(new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            timeHeader.addView(label);
        }
    }

    private void buildRows() {
        Context ctx = getContext();
        rowsContainer.removeAllViews();

        int last = Math.min(channels.size(), firstVisibleRow + visibleRowCount);
        for (int i = firstVisibleRow; i < last; i++) {
            rowsContainer.addView(buildRow(ctx, channels.get(i), i == selectedRow));
        }
    }

    private View buildRow(Context ctx, Channel channel, boolean isSelectedRow) {
        LinearLayout row = Ui.row(ctx);
        row.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                Ui.px(ctx, ROW_HEIGHT)));
        row.setBaselineAligned(false);

        row.addView(buildChannelCell(ctx, channel));

        List<Program> cells = cellsFor(channel);
        Program selected = isSelectedRow ? cellAt(cells, anchorMs) : null;

        // Weights are minutes of visible airtime, so cell width tracks duration exactly.
        long windowEnd = windowEnd();
        row.setWeightSum(minutes(windowEnd - windowStart));

        for (Program program : cells) {
            float weight = minutes(program.overlapMs(windowStart, windowEnd));
            if (weight <= 0f) {
                continue;
            }
            row.addView(buildProgramCell(ctx, channel, program, weight, program == selected));
        }
        return row;
    }

    private View buildChannelCell(Context ctx, Channel channel) {
        LinearLayout cell = Ui.row(ctx);
        cell.setLayoutParams(new LayoutParams(Ui.px(ctx, CHANNEL_COL_WIDTH),
                LayoutParams.MATCH_PARENT));
        cell.setPadding(Ui.px(ctx, 6), 0, Ui.px(ctx, 6), 0);
        Ui.setBackground(cell, Gradients.withEdges(ctx, Gradients.channelColumn(ctx),
                Ui.color(ctx, R.color.ig_cell_highlight),
                Ui.color(ctx, R.color.ig_grid_border),
                Color.TRANSPARENT, 0, 0));

        TextView number = Ui.text(ctx, String.valueOf(channel.getNumber()), 12f, true,
                Ui.color(ctx, R.color.ig_white));
        number.setGravity(Gravity.END);
        number.setMinWidth(Ui.px(ctx, 18));
        cell.addView(number);

        cell.addView(buildLogo(ctx, channel));

        TextView call = Ui.text(ctx, callSign(channel), 11f, false,
                Ui.color(ctx, R.color.ig_text_channel));
        Ui.ellipsize(call);
        LayoutParams callLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        callLp.setMarginStart(Ui.px(ctx, 4));
        call.setLayoutParams(callLp);
        cell.addView(call);
        return cell;
    }

    /**
     * The channel's logo, sitting between its number and its call sign. Loads
     * asynchronously and collapses to nothing for channels whose icon is missing or is
     * a format Android cannot decode, so the call sign takes the space back.
     */
    private View buildLogo(Context ctx, Channel channel) {
        ImageView logo = Ui.imageSlot(ctx, LOGO_WIDTH, LOGO_HEIGHT);
        LayoutParams lp = (LayoutParams) logo.getLayoutParams();
        lp.setMarginStart(Ui.px(ctx, 4));
        // Decoded at twice the drawn size so it stays crisp on 4K panels.
        ChannelIconLoader.get(ctx).load(logo, channel.getIconUrl(),
                Ui.px(ctx, LOGO_WIDTH) * 2);
        return logo;
    }

    private View buildProgramCell(Context ctx, Channel channel, Program program,
                                  float weight, boolean selected) {
        TextView cell = Ui.text(ctx, cellLabel(program), 12f, selected,
                Ui.color(ctx, selected ? R.color.ig_selected_text : R.color.ig_white));
        Ui.ellipsize(cell);
        Ui.shadow(cell, !selected);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(Ui.px(ctx, 8), 0, Ui.px(ctx, 8), 0);

        Ui.setBackground(cell, Gradients.withEdges(ctx,
                selected ? Gradients.selectedCell(ctx)
                        : Gradients.categoryCell(ctx, categoryFor(channel, program)),
                Ui.color(ctx, selected
                        ? R.color.ig_cell_highlight_selected : R.color.ig_cell_highlight),
                Ui.color(ctx, R.color.ig_grid_border),
                Ui.color(ctx, R.color.ig_grid_border), 0, 0));

        LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
        cell.setLayoutParams(lp);
        return cell;
    }

    /** Programs that started before the window are marked so the cut is obvious. */
    private String cellLabel(Program program) {
        if (program.getStartMs() < windowStart) {
            return "◄ " + program.getTitle();
        }
        return program.getTitle();
    }

    private ProgramCategory categoryFor(Channel channel, Program program) {
        if (program.getCategoryLabel() != null) {
            return program.getCategory();
        }
        // Filler and uncategorised entries inherit the channel's overall colour.
        return epg.categoryOf(channel);
    }

    private static String callSign(Channel channel) {
        return channel.getName() != null ? channel.getName() : "";
    }

    private static float minutes(long durationMs) {
        return durationMs / 60000f;
    }

    // ---- Selection ---------------------------------------------------------

    private List<Program> cellsFor(@Nullable Channel channel) {
        if (channel == null) {
            return new ArrayList<>();
        }
        return epg.programsInWindow(channel, windowStart, windowEnd(),
                getContext().getString(R.string.no_information));
    }

    /** The cell covering {@code timeMs}, or the last one before it. */
    @Nullable
    private Program cellAt(List<Program> cells, long timeMs) {
        Program fallback = null;
        for (Program p : cells) {
            if (p.getStartMs() <= timeMs && p.getStopMs() > timeMs) {
                return p;
            }
            if (p.getStartMs() <= timeMs) {
                fallback = p;
            }
        }
        return fallback != null ? fallback : (cells.isEmpty() ? null : cells.get(0));
    }

    private void notifySelection() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(getSelectedChannel(), getSelectedProgram());
        }
    }

    private void ensureRowVisible() {
        if (selectedRow < firstVisibleRow) {
            firstVisibleRow = selectedRow;
        } else if (selectedRow >= firstVisibleRow + visibleRowCount) {
            firstVisibleRow = selectedRow - visibleRowCount + 1;
        }
        int maxFirst = Math.max(0, channels.size() - visibleRowCount);
        firstVisibleRow = Math.max(0, Math.min(firstVisibleRow, maxFirst));
    }

    // ---- Input -------------------------------------------------------------

    /** Handles a D-pad key; returns whether it was consumed. */
    public boolean handleKey(int keyCode) {
        if (channels.isEmpty()) {
            return false;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return moveRow(-1);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return moveRow(1);
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return moveColumn(-1);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return moveColumn(1);
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                return chooseSelected();
            default:
                return false;
        }
    }

    private boolean moveRow(int delta) {
        int next = selectedRow + delta;
        if (next < 0 || next >= channels.size()) {
            return true;
        }
        selectRow(next);
        return true;
    }

    /**
     * Moves the cursor a whole screenful of channels up ({@code -1}) or down ({@code 1}),
     * stopping at the first or last channel. Bound to REW/FF on the Fire TV remote.
     */
    public boolean pageRows(int direction) {
        if (channels.isEmpty()) {
            return false;
        }
        int next = selectedRow + direction * visibleRowCount;
        next = Math.max(0, Math.min(next, channels.size() - 1));
        if (next != selectedRow) {
            selectRow(next);
        }
        return true;
    }

    private void selectRow(int row) {
        int previousRow = selectedRow;
        int previousFirst = firstVisibleRow;
        selectedRow = row;
        ensureRowVisible();
        // Snap the anchor onto the new row's cell boundary so the cursor stays put
        // visually even when the two rows have completely different cell runs.
        Program landed = cellAt(cellsFor(getSelectedChannel()), anchorMs);
        if (landed != null) {
            anchorMs = Math.max(landed.getStartMs(), windowStart);
        }
        if (firstVisibleRow == previousFirst) {
            // Nothing scrolled, so only the two rows whose highlight changed need
            // redrawing — rebuilding the whole page per key press stutters on a Fire
            // TV Stick Lite when the D-pad is held down.
            replaceRow(previousRow);
            replaceRow(selectedRow);
            notifySelection();
        } else {
            rebuild();
        }
    }

    /** Rebuilds a single on-screen row in place; off-screen rows are ignored. */
    private void replaceRow(int row) {
        int index = row - firstVisibleRow;
        if (index < 0 || index >= rowsContainer.getChildCount() || row >= channels.size()) {
            return;
        }
        rowsContainer.removeViewAt(index);
        rowsContainer.addView(buildRow(getContext(), channels.get(row), row == selectedRow),
                index);
    }

    private boolean moveColumn(int delta) {
        List<Program> cells = cellsFor(getSelectedChannel());
        if (cells.isEmpty()) {
            return true;
        }
        Program current = cellAt(cells, anchorMs);
        int index = cells.indexOf(current);
        int next = index + delta;

        if (next < 0) {
            shiftWindow(-1);
            return true;
        }
        if (next >= cells.size()) {
            shiftWindow(1);
            return true;
        }
        anchorMs = Math.max(cells.get(next).getStartMs(), windowStart);
        // Same window, same row: only that row's highlight moved.
        replaceRow(selectedRow);
        notifySelection();
        return true;
    }

    /** Scrolls the visible window by one 30-minute slot, never before the current one. */
    private void shiftWindow(int slots) {
        long earliest = alignToSlot(System.currentTimeMillis());
        long next = windowStart + slots * SLOT_MS;
        if (next < earliest) {
            return;
        }
        windowStart = next;
        List<Program> cells = cellsFor(getSelectedChannel());
        if (!cells.isEmpty()) {
            Program edge = slots > 0 ? cells.get(cells.size() - 1) : cells.get(0);
            anchorMs = Math.max(edge.getStartMs(), windowStart);
        } else {
            anchorMs = windowStart;
        }
        rebuild();
    }

    private boolean chooseSelected() {
        Channel channel = getSelectedChannel();
        Program program = getSelectedProgram();
        if (channel != null && program != null && chosenListener != null) {
            chosenListener.onProgramChosen(channel, program);
        }
        return true;
    }
}
