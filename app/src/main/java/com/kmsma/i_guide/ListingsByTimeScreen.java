package com.kmsma.i_guide;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The grid guide: channels down the left, 30-minute time slots across the top, and
 * program cells sized to their duration. The purple panel above reports whatever cell
 * the cursor is on.
 */
public class ListingsByTimeScreen extends GuideScreen {

    private final InfoPanelView infoPanel;
    private final GuideGridView grid;

    @Nullable
    private final ProgramCategory categoryFilter;

    public ListingsByTimeScreen(@NonNull Context context, @NonNull ScreenHost host) {
        this(context, host, null);
    }

    /**
     * @param categoryFilter when set, only channels currently showing that category are
     *                       listed — this is what the Movies / Sports / Kids / Music
     *                       tiles in the Quick Menu open.
     */
    public ListingsByTimeScreen(@NonNull Context context, @NonNull ScreenHost host,
                                @Nullable ProgramCategory categoryFilter) {
        super(context, host);
        this.categoryFilter = categoryFilter;

        infoPanel = new InfoPanelView(context);
        addContent(infoPanel);

        grid = new GuideGridView(context);
        grid.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        grid.setOnSelectionChangedListener(this::showInPanel);
        grid.setOnProgramChosenListener((channel, program) ->
                host.pushScreen(new ProgramInfoScreen(getContext(), host, channel, program)));
        addContent(grid);

        getFooterBar().setHints(
                context.getString(R.string.hint_by_time_left),
                context.getString(R.string.hint_by_time_right));

        bindChannels();
    }

    @Override
    public void onShown() {
        super.onShown();
        bindChannels();
        grid.selectChannel(host.getTunedChannel());
    }

    @Override
    public void onEpgUpdated() {
        bindChannels();
    }

    private void bindChannels() {
        grid.setChannels(visibleChannels());
    }

    private List<Channel> visibleChannels() {
        List<Channel> all = epg.getChannels();
        if (categoryFilter == null) {
            return all;
        }
        List<Channel> filtered = new ArrayList<>();
        for (Channel c : all) {
            if (epg.categoryOf(c) == categoryFilter) {
                filtered.add(c);
            }
        }
        // An empty filter result would leave a blank grid; fall back to the full line-up.
        return filtered.isEmpty() ? all : filtered;
    }

    private void showInPanel(@Nullable Channel channel, @Nullable Program program) {
        if (channel == null) {
            infoPanel.setTitle(getContext().getString(R.string.loading_guide));
            infoPanel.setTitleTrailing(null);
            infoPanel.setTimeAndDescription(null, null);
            return;
        }
        infoPanel.setTitleTrailing(channel.getNumber() + " " + channel.getName());
        if (program == null) {
            infoPanel.setTitle(channel.getName());
            infoPanel.setTimeAndDescription(null,
                    getContext().getString(R.string.no_information));
            return;
        }
        infoPanel.setTitle(program.getTitle());
        infoPanel.setTimeAndDescription(program.formatTimeRange(), program.formatSynopsis());
    }

    @Nullable
    @Override
    public View getLivePreviewSlot() {
        return infoPanel.getPreviewSlot();
    }

    @Override
    public boolean onScreenKeyDown(int keyCode, KeyEvent event) {
        // INFO tunes straight to the highlighted channel without a detour through
        // Program Information.
        if (keyCode == KeyEvent.KEYCODE_INFO) {
            host.tuneTo(grid.getSelectedChannel());
            return true;
        }
        return grid.handleKey(keyCode);
    }
}
