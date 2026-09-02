package com.kmsma.i_guide;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * One channel's whole schedule as a vertical list, with a left/right stepper in the
 * section bar for moving between channels.
 */
public class ListingsByChannelScreen extends GuideScreen {

    private final InfoPanelView infoPanel;
    private final SectionBarView sectionBar;
    private final SelectionListView<Program> list;
    private final TextView stepperLabel;

    private int channelIndex;

    public ListingsByChannelScreen(@NonNull Context context, @NonNull ScreenHost host,
                                   @Nullable Channel initialChannel) {
        super(context, host);

        channelIndex = Math.max(0, epg.indexOfChannel(
                initialChannel != null ? initialChannel.getId() : null));

        infoPanel = new InfoPanelView(context);
        addContent(infoPanel);

        sectionBar = new SectionBarView(context);
        sectionBar.setTitle(context.getString(R.string.title_listings_by_channel));
        stepperLabel = Ui.text(context, "", 12f, true, Ui.color(context, R.color.ig_white));
        sectionBar.addTrailing(buildStepper(context));
        addContent(sectionBar);

        list = new SelectionListView<>(context);
        list.setRowHeight(28);
        list.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        list.setBinder(this::buildRow);
        list.setOnSelectionChangedListener((program, index) -> showInPanel(program));
        list.setOnItemChosenListener((program, index) -> host.pushScreen(
                new ProgramInfoScreen(getContext(), host, currentChannel(), program)));
        addContent(list);

        getFooterBar().setHint(context.getString(R.string.hint_by_channel));
        bindChannel();
    }

    /** The {@code ◄ 5 CTV ►} control at the right of the section bar. */
    private View buildStepper(Context ctx) {
        LinearLayout stepper = Ui.row(ctx);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(stepper, ctx, 10, 2, 10, 2);
        Ui.setBackground(stepper, Gradients.withEdges(ctx,
                new android.graphics.drawable.ColorDrawable(Ui.color(ctx, R.color.ig_tab_bg)),
                Ui.color(ctx, R.color.ig_tab_border), Ui.color(ctx, R.color.ig_tab_border),
                Ui.color(ctx, R.color.ig_tab_border), 0, 0));

        stepper.addView(arrow(ctx, "◄", 0, 10));
        stepper.addView(stepperLabel);
        stepper.addView(arrow(ctx, "►", 10, 0));
        return stepper;
    }

    private TextView arrow(Context ctx, String glyph, int marginStart, int marginEnd) {
        TextView tv = Ui.text(ctx, glyph, 12f, true, Ui.color(ctx, R.color.ig_yellow_accent));
        LinearLayout.LayoutParams lp = Ui.wrapWrap();
        lp.setMarginStart(Ui.px(ctx, marginStart));
        lp.setMarginEnd(Ui.px(ctx, marginEnd));
        tv.setLayoutParams(lp);
        return tv;
    }

    @Override
    public void onShown() {
        super.onShown();
        bindChannel();
    }

    @Override
    public void onEpgUpdated() {
        bindChannel();
    }

    @Nullable
    private Channel currentChannel() {
        List<Channel> channels = epg.getChannels();
        if (channels.isEmpty()) {
            return null;
        }
        return channels.get(Math.min(channelIndex, channels.size() - 1));
    }

    private void bindChannel() {
        Channel channel = currentChannel();
        if (channel == null) {
            stepperLabel.setText("");
            list.setItems(new ArrayList<>());
            return;
        }
        stepperLabel.setText(channel.getNumber() + " " + channel.getName());
        list.setItems(upcomingPrograms(channel));
        list.setSelectedIndex(indexOfCurrentProgram());
    }

    /** The channel's schedule from the top of the current hour onward. */
    private List<Program> upcomingPrograms(Channel channel) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long from = cal.getTimeInMillis();

        List<Program> out = new ArrayList<>();
        for (Program p : epg.programsFor(channel)) {
            if (p.getStopMs() > from) {
                out.add(p);
            }
        }
        return out;
    }

    private int indexOfCurrentProgram() {
        long now = System.currentTimeMillis();
        List<Program> items = list.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isLiveAt(now)) {
                return i;
            }
        }
        return 0;
    }

    private View buildRow(Context ctx, Program program, int index, boolean selected) {
        LinearLayout row = Ui.row(ctx);
        row.setBaselineAligned(false);
        row.addView(RowViews.labelColumn(ctx,
                Program.formatSlotHeader(program.getStartMs())));

        LinearLayout body = RowViews.bodyCell(ctx, selected, program.getCategory());
        TextView title = RowViews.cellText(ctx, program.getTitle(), selected);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(title);
        row.addView(body);
        return row;
    }

    private void showInPanel(@Nullable Program program) {
        Channel channel = currentChannel();
        if (program == null) {
            infoPanel.setTitle(channel != null ? channel.getName() : "");
            infoPanel.setTimeAndDescription(null,
                    getContext().getString(R.string.no_information));
            return;
        }
        infoPanel.setTitle(program.getTitle());
        infoPanel.setTimeAndDescription(program.formatTimeRange(), program.formatSynopsis());
    }

    private void stepChannel(int delta) {
        List<Channel> channels = epg.getChannels();
        if (channels.isEmpty()) {
            return;
        }
        channelIndex = (channelIndex + delta + channels.size()) % channels.size();
        bindChannel();
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
                stepChannel(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                stepChannel(1);
                return true;
            case KeyEvent.KEYCODE_INFO:
                host.tuneTo(currentChannel());
                return true;
            default:
                return list.handleKey(keyCode);
        }
    }
}
