package com.kmsma.i_guide;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/** Full-screen vertical menu listing everything the guide can open. */
public class MainMenuScreen extends GuideScreen {

    /** One menu row and where it goes. Deferred entries report themselves as such. */
    private enum MenuItem {
        BY_TIME("TV Listings by Time"),
        BY_CHANNEL("TV Listings by Channel"),
        SEARCH("Search"),
        JELLYFIN("Jellyfin Library"),
        FAVOURITES("Favourites"),
        RECORDINGS("Recordings (DVR)"),
        MUSIC("Music Channels"),
        SETUP("Setup");

        final String label;

        MenuItem(String label) {
            this.label = label;
        }
    }

    private static final List<MenuItem> ITEMS = Arrays.asList(MenuItem.values());

    private final InfoPanelView infoPanel;
    private final SelectionListView<MenuItem> list;

    public MainMenuScreen(@NonNull Context context, @NonNull ScreenHost host) {
        super(context, host);

        infoPanel = new InfoPanelView(context);
        infoPanel.useMenuProportions();
        infoPanel.setTitle(context.getString(R.string.title_main_menu));
        buildPanelBody(context);
        addContent(infoPanel);

        SectionBarView sectionBar = new SectionBarView(context);
        sectionBar.setTitle(context.getString(R.string.title_menu));
        addContent(sectionBar);

        list = new SelectionListView<>(context);
        list.setRowHeight(30);
        list.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        list.setBinder(this::buildRow);
        list.setOnItemChosenListener((item, index) -> open(item));
        list.setItems(ITEMS);
        addContent(list);

        getFooterBar().setHint(context.getString(R.string.hint_main_menu));
    }

    private void buildPanelBody(Context ctx) {
        infoPanel.clearDefaultBody();
        LinearLayout body = infoPanel.getBodyContainer();

        TextView blurb = Ui.text(ctx, ctx.getString(R.string.main_menu_blurb), 13f, false,
                Ui.color(ctx, R.color.ig_white));
        blurb.setLineSpacing(0f, 1.45f);
        blurb.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        body.addView(blurb);

        TextView nowPlaying = Ui.text(ctx, "", 12f, false,
                Ui.color(ctx, R.color.ig_text_menu_desc));
        Ui.ellipsize(nowPlaying);
        body.addView(nowPlaying);
        updateNowPlaying(nowPlaying);
    }

    private void updateNowPlaying(TextView view) {
        Channel tuned = host.getTunedChannel();
        if (tuned == null) {
            view.setText("");
            return;
        }
        view.setText(getContext().getString(R.string.now_playing_fmt,
                tuned.getName(), String.valueOf(tuned.getNumber())));
    }

    private View buildRow(Context ctx, MenuItem item, int index, boolean selected) {
        LinearLayout row = RowViews.bodyCell(ctx, selected);
        row.setPadding(Ui.px(ctx, 16), 0, Ui.px(ctx, 16), 0);
        TextView label = Ui.text(ctx, item.label, 13f, selected,
                Ui.color(ctx, selected ? R.color.ig_selected_text : R.color.ig_white));
        Ui.shadow(label, !selected);
        row.addView(label);
        return row;
    }

    private void open(MenuItem item) {
        Context ctx = getContext();
        switch (item) {
            case BY_TIME:
                host.pushScreen(new ListingsByTimeScreen(ctx, host));
                break;
            case BY_CHANNEL:
                host.pushScreen(new ListingsByChannelScreen(ctx, host, host.getTunedChannel()));
                break;
            case FAVOURITES:
                host.pushScreen(new FavouritesScreen(ctx, host));
                break;
            case MUSIC:
                host.pushScreen(new ListingsByTimeScreen(ctx, host, ProgramCategory.MUSIC));
                break;
            case SEARCH:
            case JELLYFIN:
            case RECORDINGS:
            case SETUP:
            default:
                // Search, DVR and Setup are out of scope for this build.
                Toast.makeText(ctx, R.string.not_available_yet, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Nullable
    @Override
    public View getLivePreviewSlot() {
        return infoPanel.getPreviewSlot();
    }

    @Override
    public boolean onScreenKeyDown(int keyCode, KeyEvent event) {
        return list.handleKey(keyCode);
    }
}
