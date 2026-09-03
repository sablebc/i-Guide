package com.kmsma.i_guide;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Full-screen live TV player and host for the whole guide UI.
 *
 * <p>Everything runs in this one activity so the video surface is never torn down: the
 * guide pages stack in {@code screen_container} above the video, and while one is
 * showing the {@link PlayerView} is scaled into that page's preview slot and raised
 * above it. That way "live preview" really is the live stream.
 */
public class PlayerActivity extends AppCompatActivity
        implements ScreenHost, EpgRepository.Listener {

    private FrameLayout root;
    private PlayerView playerView;
    private FrameLayout screenContainer;
    private FrameLayout overlayContainer;
    private FlipBarView flipBarView;
    private QuickMenuView quickMenuView;
    private MiniGuideView miniGuideView;
    private StaticNoiseView staticNoiseView;

    private final EpgRepository epg = EpgRepository.get();
    private final ChannelManager channelManager = new ChannelManager();
    private final Deque<GuideScreen> screenStack = new ArrayDeque<>();

    private FavouritesStore favourites;

    /** How many times a single tune will retry a stream that ExoPlayer reports as failed. */
    private static final int MAX_PLAYBACK_ATTEMPTS = 3;
    private static final long PLAYBACK_RETRY_DELAY_MS = 2000L;

    private final Handler retryHandler = new Handler(Looper.getMainLooper());

    @Nullable
    private ExoPlayer player;
    @Nullable
    private Channel tunedChannel;
    /** What was tuned before the current channel, so a dead stream can fall back to it. */
    @Nullable
    private Channel previousChannel;
    /** Attempts made so far for {@link #tunedChannel}'s current stream, including the first. */
    private int playbackAttempt;
    @Nullable
    private Runnable pendingRetry;

    /** Keeps the video glued to the active page's preview slot across re-layouts. */
    @Nullable
    private ViewTreeObserver.OnGlobalLayoutListener previewLayoutListener;
    @Nullable
    private View previewSlot;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        root = findViewById(R.id.player_root);
        playerView = findViewById(R.id.player_view);
        screenContainer = findViewById(R.id.screen_container);
        overlayContainer = findViewById(R.id.overlay_container);
        flipBarView = findViewById(R.id.flip_bar);
        quickMenuView = findViewById(R.id.quick_menu);
        miniGuideView = findViewById(R.id.mini_guide);
        staticNoiseView = findViewById(R.id.static_noise);

        favourites = new FavouritesStore(this);

        quickMenuView.setOnItemChosenListener(this::onQuickMenuItem);
        miniGuideView.setListener(this::tuneTo);

        // On API 23 and below, wait until onResume to acquire the player;
        // on API 24+ (multi-window aware) it happens in onStart instead.
        if (Build.VERSION.SDK_INT <= 23) {
            initializePlayer();
        }

        epg.addListener(this);
        epg.load();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!handleBack()) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        epg.removeListener(this);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT <= 23 || player == null) {
            initializePlayer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT > 23) {
            releasePlayer();
        }
    }

    // ---- EPG ---------------------------------------------------------------

    @Override
    public void onEpgUpdated() {
        List<Channel> channels = epg.getChannels();
        channelManager.setChannels(channels);
        if (tunedChannel != null) {
            // A refresh must not knock the cursor back to the top of the line-up.
            channelManager.selectById(tunedChannel.getId());
        } else if (!channels.isEmpty()) {
            tuneToCurrentChannel();
        }
    }

    @Override
    public void onEpgFailed(Exception e) {
        if (!epg.hasChannels()) {
            Toast.makeText(this,
                    getString(R.string.no_channels_available) + ": " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ---- Playback ----------------------------------------------------------

    private void initializePlayer() {
        if (player != null) {
            return;
        }
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setPlayWhenReady(true);
        player.addListener(new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                // The new channel's picture is up; the snow can clear off it now.
                staticNoiseView.settle();
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                handlePlaybackError();
            }
        });

        Channel current = channelManager.getCurrentChannel();
        if (current != null) {
            playChannel(current);
        }
    }

    private void releasePlayer() {
        cancelPendingRetry();
        staticNoiseView.clear();
        if (player != null) {
            player.release();
            player = null;
            playerView.setPlayer(null);
        }
    }

    private void tuneToCurrentChannel() {
        Channel current = channelManager.getCurrentChannel();
        if (current == null) {
            return;
        }
        playChannel(current);
        showFlipBar();
    }

    private void playChannel(Channel channel) {
        cancelPendingRetry();
        if (tunedChannel != null && !isSameChannel(tunedChannel, channel)) {
            previousChannel = tunedChannel;
        }
        tunedChannel = channel;
        playbackAttempt = 1;
        if (player == null) {
            return;
        }
        // Snow covers the swap, and stays up over the black frame while the new
        // stream buffers; it clears itself once ExoPlayer renders a frame of it.
        staticNoiseView.burst();
        startPlayback(channel);
    }

    private void startPlayback(Channel channel) {
        String streamUrl = TunarrApiClient.buildStreamUrl(channel.getId());
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(streamUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build();
        player.setMediaItem(mediaItem);
        player.prepare();
    }

    /**
     * ExoPlayer reported a playback failure for the current channel. Retries the same
     * stream a few times before giving up, since a cold Tunarr transcode session can
     * take several seconds to come up and briefly looks identical to a dead one.
     */
    private void handlePlaybackError() {
        Channel failedChannel = tunedChannel;
        if (failedChannel == null || player == null) {
            return;
        }
        if (playbackAttempt < MAX_PLAYBACK_ATTEMPTS) {
            playbackAttempt++;
            pendingRetry = () -> {
                pendingRetry = null;
                if (player != null && isSameChannel(tunedChannel, failedChannel)) {
                    startPlayback(failedChannel);
                }
            };
            retryHandler.postDelayed(pendingRetry, PLAYBACK_RETRY_DELAY_MS);
        } else {
            Toast.makeText(this, R.string.channel_unavailable, Toast.LENGTH_LONG).show();
            returnToPreviousChannel();
        }
    }

    /** Falls back to whatever was tuned before the channel that just failed for good. */
    private void returnToPreviousChannel() {
        Channel fallback = previousChannel;
        if (fallback == null) {
            staticNoiseView.clear();
            return;
        }
        channelManager.selectById(fallback.getId());
        playChannel(fallback);
        showFlipBar();
    }

    private void cancelPendingRetry() {
        if (pendingRetry != null) {
            retryHandler.removeCallbacks(pendingRetry);
            pendingRetry = null;
        }
    }

    private static boolean isSameChannel(@Nullable Channel a, @Nullable Channel b) {
        return a != null && b != null && a.getId() != null && a.getId().equals(b.getId());
    }

    private void changeChannel(boolean up) {
        if (!channelManager.hasChannels()) {
            return;
        }
        Channel next = up ? channelManager.nextChannel() : channelManager.previousChannel();
        if (next == null) {
            return;
        }
        playChannel(next);
        showFlipBar();
    }

    private void showFlipBar() {
        if (tunedChannel == null) {
            return;
        }
        flipBarView.show(tunedChannel, epg.currentProgram(tunedChannel));
    }

    // ---- ScreenHost --------------------------------------------------------

    @Override
    public void pushScreen(@NonNull GuideScreen screen) {
        hideOverlays();
        // A guide page takes the screen and the video shrinks into its preview slot,
        // so a full-screen burst has nothing left to cover.
        staticNoiseView.clear();
        GuideScreen previous = screenStack.peek();
        if (previous != null) {
            previous.onHidden();
            previous.setVisibility(View.GONE);
        }
        screenStack.push(screen);
        screenContainer.addView(screen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        screenContainer.setVisibility(View.VISIBLE);
        screen.onShown();
        attachLivePreview(screen);
    }

    @Override
    public void popScreen() {
        GuideScreen top = screenStack.poll();
        if (top == null) {
            return;
        }
        top.onHidden();
        screenContainer.removeView(top);

        GuideScreen next = screenStack.peek();
        if (next == null) {
            returnToLiveVideo();
            return;
        }
        next.setVisibility(View.VISIBLE);
        next.onShown();
        attachLivePreview(next);
    }

    @Override
    public void returnToLiveVideo() {
        for (GuideScreen screen : screenStack) {
            screen.onHidden();
        }
        screenStack.clear();
        screenContainer.removeAllViews();
        screenContainer.setVisibility(View.GONE);
        detachLivePreview();
    }

    @Override
    public void tuneTo(@Nullable Channel channel) {
        if (channel != null) {
            channelManager.selectById(channel.getId());
            playChannel(channel);
        }
        returnToLiveVideo();
        hideOverlays();
        showFlipBar();
    }

    @Nullable
    @Override
    public Channel getTunedChannel() {
        return tunedChannel;
    }

    @NonNull
    @Override
    public FavouritesStore getFavourites() {
        return favourites;
    }

    @Override
    public void bindLivePreview(@Nullable View slot) {
        if (slot == null) {
            detachLivePreview();
        } else {
            attachPreviewSlot(slot);
        }
    }

    // ---- Live preview placement -------------------------------------------

    private void attachLivePreview(GuideScreen screen) {
        View slot = screen.getLivePreviewSlot();
        if (slot == null) {
            detachLivePreview();
        } else {
            attachPreviewSlot(slot);
        }
    }

    /**
     * Moves the video into the given slot and keeps it there. A global layout listener
     * re-measures on every pass, which covers the first frame (when the slot has no size
     * yet) as well as later page changes.
     */
    private void attachPreviewSlot(@NonNull View slot) {
        previewSlot = slot;
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        playerView.bringToFront();

        if (previewLayoutListener == null) {
            previewLayoutListener = this::syncPreviewBounds;
            root.getViewTreeObserver().addOnGlobalLayoutListener(previewLayoutListener);
        }
        syncPreviewBounds();
    }

    private void syncPreviewBounds() {
        View slot = previewSlot;
        if (slot == null || slot.getWidth() == 0 || slot.getHeight() == 0) {
            return;
        }
        int[] rootLocation = new int[2];
        int[] slotLocation = new int[2];
        root.getLocationInWindow(rootLocation);
        slot.getLocationInWindow(slotLocation);

        FrameLayout.LayoutParams lp =
                new FrameLayout.LayoutParams(slot.getWidth(), slot.getHeight());
        lp.leftMargin = slotLocation[0] - rootLocation[0];
        lp.topMargin = slotLocation[1] - rootLocation[1];

        FrameLayout.LayoutParams existing =
                (FrameLayout.LayoutParams) playerView.getLayoutParams();
        if (existing.width == lp.width && existing.height == lp.height
                && existing.leftMargin == lp.leftMargin && existing.topMargin == lp.topMargin) {
            return;
        }
        playerView.setLayoutParams(lp);
    }

    private void detachLivePreview() {
        previewSlot = null;
        if (previewLayoutListener != null) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(previewLayoutListener);
            previewLayoutListener = null;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        playerView.setLayoutParams(lp);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        // Put the bottom overlays back above the video now that it fills the screen.
        overlayContainer.bringToFront();
    }

    // ---- Quick Menu --------------------------------------------------------

    private void onQuickMenuItem(@NonNull QuickMenuView.Item item) {
        quickMenuView.hide();
        switch (item) {
            case MAIN_MENU:
                pushScreen(new MainMenuScreen(this, this));
                break;
            case GUIDE:
                pushScreen(new ListingsByTimeScreen(this, this));
                break;
            case FAVOURITES:
                pushScreen(new FavouritesScreen(this, this));
                break;
            case MOVIES:
                pushScreen(new ListingsByTimeScreen(this, this, ProgramCategory.MOVIES));
                break;
            case KIDS:
                pushScreen(new ListingsByTimeScreen(this, this, ProgramCategory.KIDS));
                break;
            case SPORTS:
                pushScreen(new ListingsByTimeScreen(this, this, ProgramCategory.SPORTS));
                break;
            case MUSIC:
                pushScreen(new ListingsByTimeScreen(this, this, ProgramCategory.MUSIC));
                break;
            case JELLYFIN:
            case HDTV:
            case SEARCH:
            case SETTINGS:
            default:
                // Search, the Jellyfin browser and Settings are out of scope for this build.
                Toast.makeText(this, R.string.not_available_yet, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void hideOverlays() {
        flipBarView.hide();
        quickMenuView.hide();
        miniGuideView.hide();
    }

    // ---- Input -------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        GuideScreen top = screenStack.peek();
        if (top != null) {
            if (top.onScreenKeyDown(keyCode, event)) {
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        if (quickMenuView.isShowing() && quickMenuView.handleKey(keyCode)) {
            return true;
        }
        if (miniGuideView.isShowing() && miniGuideView.handleKey(keyCode)) {
            return true;
        }
        return handleLiveTvKey(keyCode) || super.onKeyDown(keyCode, event);
    }

    private boolean handleLiveTvKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                changeChannel(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                changeChannel(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_INFO:
                showFlipBar();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                flipBarView.hide();
                miniGuideView.show(tunedChannel);
                return true;
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_TV_CONTENTS_MENU:
                flipBarView.hide();
                miniGuideView.hide();
                quickMenuView.show();
                return true;
            case KeyEvent.KEYCODE_BOOKMARK:
            case KeyEvent.KEYCODE_PROG_YELLOW:
                if (tunedChannel != null) {
                    boolean added = favourites.toggle(tunedChannel);
                    Toast.makeText(this, added
                                    ? R.string.added_to_favourites
                                    : R.string.removed_from_favourites,
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            default:
                return false;
        }
    }

    /** BACK: dismiss the top overlay, else pop a screen, else fall through to exit. */
    private boolean handleBack() {
        if (!screenStack.isEmpty()) {
            popScreen();
            return true;
        }
        if (quickMenuView.isShowing()) {
            quickMenuView.hide();
            return true;
        }
        if (miniGuideView.isShowing()) {
            miniGuideView.hide();
            return true;
        }
        if (flipBarView.isShowing()) {
            flipBarView.hide();
            return true;
        }
        return false;
    }
}
