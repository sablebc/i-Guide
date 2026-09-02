package com.kmsma.i_guide;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Base for the full-screen guide pages. Supplies the shared shell — a 42px header, a
 * content area, and the navigation-hint footer — and routes D-pad keys, since these
 * screens paint their own yellow selection rather than relying on view focus.
 */
public abstract class GuideScreen extends LinearLayout implements EpgRepository.Listener {

    protected final ScreenHost host;
    protected final EpgRepository epg = EpgRepository.get();

    private final HeaderBarView headerBar;
    private final FooterBarView footerBar;
    private final LinearLayout contentContainer;

    public GuideScreen(@NonNull Context context, @NonNull ScreenHost host) {
        super(context);
        this.host = host;

        setOrientation(VERTICAL);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        Ui.setBackground(this, Gradients.screenBackground(context));
        // Guide pages paint their own selection, so nothing inside takes focus and
        // every key reaches the activity, which routes it to onScreenKeyDown.
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);

        headerBar = new HeaderBarView(context);
        addView(headerBar);

        contentContainer = Ui.column(context);
        contentContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        addView(contentContainer);

        footerBar = new FooterBarView(context);
        addView(footerBar);
    }

    /** Adds a view to the area between the header and the footer. */
    protected void addContent(View view) {
        contentContainer.addView(view);
    }

    @NonNull
    protected LinearLayout getContentContainer() {
        return contentContainer;
    }

    @NonNull
    protected FooterBarView getFooterBar() {
        return footerBar;
    }

    @NonNull
    protected HeaderBarView getHeaderBar() {
        return headerBar;
    }

    /** Hides the header on overlay-style pages that sit over live video. */
    protected void setHeaderVisible(boolean visible) {
        headerBar.setVisibility(visible ? VISIBLE : GONE);
    }

    // ---- Lifecycle ---------------------------------------------------------

    /** Called after the screen is attached and has become the active page. */
    public void onShown() {
        epg.addListener(this);
    }

    /** Called before the screen is detached. */
    public void onHidden() {
        epg.removeListener(this);
    }

    @Override
    public void onEpgUpdated() {
    }

    @Override
    public void onEpgFailed(Exception e) {
    }

    /**
     * The live-preview rectangle for this page, or {@code null} if it has none. The host
     * scales the video into it while the page is showing.
     */
    @Nullable
    public View getLivePreviewSlot() {
        return null;
    }

    // ---- Input -------------------------------------------------------------

    /**
     * Handles a D-pad or remote key. Return {@code true} to consume it; unhandled keys
     * fall through to the host, which treats BACK as "pop this screen".
     */
    public abstract boolean onScreenKeyDown(int keyCode, KeyEvent event);
}
