package com.kmsma.i_guide;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Favourite channels with what is on each one now, filterable by category tabs in the
 * section bar. FAV (or the channel-up long press equivalent) removes the highlighted
 * entry.
 */
public class FavouritesScreen extends GuideScreen {

    /** {@code null} category means the "All" tab. */
    private static final List<ProgramCategory> TABS = Arrays.asList(
            null, ProgramCategory.SPORTS, ProgramCategory.MOVIES, ProgramCategory.KIDS);

    private final InfoPanelView infoPanel;
    private final SelectionListView<Channel> list;
    private final LinearLayout tabStrip;
    private final TextView emptyView;

    private int activeTab;

    public FavouritesScreen(@NonNull Context context, @NonNull ScreenHost host) {
        super(context, host);

        infoPanel = new InfoPanelView(context);
        addContent(infoPanel);

        SectionBarView sectionBar = new SectionBarView(context);
        sectionBar.setTitle(context.getString(R.string.title_favourites));
        tabStrip = Ui.row(context);
        sectionBar.addTrailing(tabStrip);
        addContent(sectionBar);

        list = new SelectionListView<>(context);
        list.setRowHeight(28);
        list.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        list.setBinder(this::buildRow);
        list.setOnSelectionChangedListener((channel, index) -> showInPanel(channel));
        list.setOnItemChosenListener((channel, index) -> host.tuneTo(channel));
        addContent(list);

        emptyView = Ui.text(context, context.getString(R.string.no_favourites), 13f, false,
                Ui.color(context, R.color.ig_text_channel));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        emptyView.setVisibility(GONE);
        addContent(emptyView);

        getFooterBar().setHint(context.getString(R.string.hint_favourites));

        buildTabs();
        bindList();
    }

    private void buildTabs() {
        Context ctx = getContext();
        tabStrip.removeAllViews();
        for (int i = 0; i < TABS.size(); i++) {
            ProgramCategory category = TABS.get(i);
            boolean active = i == activeTab;
            String label = category == null ? "All" : category.label();

            TextView tab = Ui.text(ctx, label, 11f, true, Ui.color(ctx,
                    active ? R.color.ig_selected_text : R.color.ig_white));
            Ui.shadow(tab, !active);
            Ui.padding(tab, ctx, 10, 1, 10, 1);
            Ui.setBackground(tab, Gradients.withEdges(ctx,
                    active ? Gradients.selectedCell(ctx)
                            : new android.graphics.drawable.ColorDrawable(
                                    Ui.color(ctx, R.color.ig_tab_bg)),
                    Ui.color(ctx, active
                            ? R.color.ig_tab_border_active : R.color.ig_tab_border),
                    Ui.color(ctx, active
                            ? R.color.ig_tab_border_active : R.color.ig_tab_border),
                    Ui.color(ctx, active
                            ? R.color.ig_tab_border_active : R.color.ig_tab_border),
                    0, 0));

            LinearLayout.LayoutParams lp = Ui.wrapWrap();
            lp.setMarginStart(Ui.px(ctx, i == 0 ? 0 : 4));
            tab.setLayoutParams(lp);
            tabStrip.addView(tab);
        }
    }

    @Override
    public void onShown() {
        super.onShown();
        bindList();
    }

    @Override
    public void onEpgUpdated() {
        bindList();
    }

    private void bindList() {
        List<Channel> favourites = host.getFavourites().filter(epg.getChannels());
        ProgramCategory filter = TABS.get(activeTab);
        List<Channel> shown = new ArrayList<>();
        for (Channel c : favourites) {
            if (filter == null || epg.categoryOf(c) == filter) {
                shown.add(c);
            }
        }
        list.setItems(shown);

        boolean empty = shown.isEmpty();
        emptyView.setVisibility(empty ? VISIBLE : GONE);
        list.setVisibility(empty ? GONE : VISIBLE);
        if (empty) {
            infoPanel.setTitle(getContext().getString(R.string.title_favourites));
            infoPanel.setTimeAndDescription(null, null);
        }
    }

    private View buildRow(Context ctx, Channel channel, int index, boolean selected) {
        LinearLayout row = Ui.row(ctx);
        row.setBaselineAligned(false);
        row.addView(RowViews.channelColumn(ctx, channel));

        Program now = epg.currentProgram(channel);
        LinearLayout body = RowViews.bodyCell(ctx, selected, epg.categoryOf(channel));

        TextView title = RowViews.cellText(ctx,
                now != null ? now.getTitle() : ctx.getString(R.string.no_information), selected);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(title);

        if (now != null) {
            body.addView(RowViews.cellSubText(ctx, now.formatTimeRange(), selected));
        }
        row.addView(body);
        return row;
    }

    private void showInPanel(@Nullable Channel channel) {
        if (channel == null) {
            return;
        }
        Program now = epg.currentProgram(channel);
        String channelLabel = channel.getNumber() + " " + channel.getName();
        if (now == null) {
            infoPanel.setTitle(channel.getName());
            infoPanel.setTimeAndDescription(channelLabel,
                    getContext().getString(R.string.no_information));
            return;
        }
        infoPanel.setTitle(now.getTitle());
        infoPanel.setTimeAndDescription(now.formatTimeRange() + " — " + channelLabel,
                now.formatSynopsis());
    }

    private void switchTab(int delta) {
        activeTab = (activeTab + delta + TABS.size()) % TABS.size();
        buildTabs();
        list.setSelectedIndex(0);
        bindList();
    }

    private void removeSelected() {
        Channel channel = list.getSelectedItem();
        if (channel == null) {
            return;
        }
        host.getFavourites().toggle(channel);
        Toast.makeText(getContext(), R.string.removed_from_favourites, Toast.LENGTH_SHORT).show();
        bindList();
    }

    @Nullable
    @Override
    public View getLivePreviewSlot() {
        return infoPanel.getPreviewSlot();
    }

    @Override
    public boolean onScreenKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                switchTab(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                switchTab(1);
                return true;
            case KeyEvent.KEYCODE_BOOKMARK:
            case KeyEvent.KEYCODE_PROG_YELLOW:
                removeSelected();
                return true;
            case KeyEvent.KEYCODE_INFO:
                Channel channel = list.getSelectedItem();
                Program now = epg.currentProgram(channel);
                if (channel != null && now != null) {
                    host.pushScreen(new ProgramInfoScreen(getContext(), host, channel, now));
                }
                return true;
            default:
                return list.handleKey(keyCode);
        }
    }
}
