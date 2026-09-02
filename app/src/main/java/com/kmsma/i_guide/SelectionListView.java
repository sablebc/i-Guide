package com.kmsma.i_guide;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Vertical list with painted (rather than focused) selection, matching the guide's
 * yellow highlight. Only the rows that fit are inflated; moving past the edge scrolls
 * the viewport rather than the view itself, which keeps rows pixel-aligned with the
 * bars above and below.
 *
 * @param <T> the row's backing item
 */
public class SelectionListView<T> extends LinearLayout {

    /** Builds one row. Called again whenever selection or data changes. */
    public interface RowBinder<T> {
        View createRow(@NonNull Context context, @NonNull T item, int index, boolean selected);
    }

    public interface OnSelectionChangedListener<T> {
        void onSelectionChanged(@Nullable T item, int index);
    }

    public interface OnItemChosenListener<T> {
        void onItemChosen(@NonNull T item, int index);
    }

    private List<T> items = Collections.emptyList();
    private RowBinder<T> binder;
    private OnSelectionChangedListener<T> selectionListener;
    private OnItemChosenListener<T> chosenListener;

    private int rowHeight = 28;
    private int selectedIndex;
    private int firstVisibleRow;
    private int visibleRowCount = 1;

    public SelectionListView(Context context) {
        super(context);
        init();
    }

    public SelectionListView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SelectionListView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setClipChildren(true);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setRowHeight(int designUnits) {
        this.rowHeight = designUnits;
        recomputeVisibleRows();
    }

    public void setBinder(@NonNull RowBinder<T> binder) {
        this.binder = binder;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener<T> l) {
        this.selectionListener = l;
    }

    public void setOnItemChosenListener(OnItemChosenListener<T> l) {
        this.chosenListener = l;
    }

    public void setItems(@NonNull List<T> items) {
        this.items = items;
        if (selectedIndex >= items.size()) {
            selectedIndex = Math.max(0, items.size() - 1);
        }
        ensureRowVisible();
        rebuild();
    }

    @NonNull
    public List<T> getItems() {
        return items;
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        selectedIndex = index;
        ensureRowVisible();
        rebuild();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    @Nullable
    public T getSelectedItem() {
        if (selectedIndex < 0 || selectedIndex >= items.size()) {
            return null;
        }
        return items.get(selectedIndex);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recomputeVisibleRows();
    }

    private void recomputeVisibleRows() {
        int rowHeightPx = Ui.px(getContext(), rowHeight);
        int count = Math.max(1, getHeight() / rowHeightPx);
        if (count != visibleRowCount) {
            visibleRowCount = count;
            ensureRowVisible();
            // Posted, not called inline: this runs from onSizeChanged, and views added
            // during a layout pass never get measured.
            post(this::rebuild);
        }
    }

    private void ensureRowVisible() {
        if (selectedIndex < firstVisibleRow) {
            firstVisibleRow = selectedIndex;
        } else if (selectedIndex >= firstVisibleRow + visibleRowCount) {
            firstVisibleRow = selectedIndex - visibleRowCount + 1;
        }
        int maxFirst = Math.max(0, items.size() - visibleRowCount);
        firstVisibleRow = Math.max(0, Math.min(firstVisibleRow, maxFirst));
    }

    public void rebuild() {
        removeAllViews();
        if (binder == null) {
            return;
        }
        int last = Math.min(items.size(), firstVisibleRow + visibleRowCount);
        for (int i = firstVisibleRow; i < last; i++) {
            View row = binder.createRow(getContext(), items.get(i), i, i == selectedIndex);
            row.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    Ui.px(getContext(), rowHeight)));
            addView(row);
        }
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(getSelectedItem(), selectedIndex);
        }
    }

    /** Handles a D-pad key; returns whether it was consumed. */
    public boolean handleKey(int keyCode) {
        if (items.isEmpty()) {
            return false;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return move(-1);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return move(1);
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                T item = getSelectedItem();
                if (item != null && chosenListener != null) {
                    chosenListener.onItemChosen(item, selectedIndex);
                }
                return true;
            default:
                return false;
        }
    }

    private boolean move(int delta) {
        int next = selectedIndex + delta;
        if (next < 0 || next >= items.size()) {
            return true;
        }
        selectedIndex = next;
        ensureRowVisible();
        rebuild();
        return true;
    }
}
