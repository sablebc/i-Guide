package com.kmsma.i_guide;

import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.List;

/**
 * Full-screen live TV player. Fetches the Tunarr channel list, plays the first
 * channel's HLS stream, and shows the Flip Bar overlay on channel change / OK press.
 */
public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private FlipBarView flipBarView;

    private final TunarrApiClient apiClient = new TunarrApiClient();
    private final ChannelManager channelManager = new ChannelManager();

    @Nullable
    private ExoPlayer player;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        flipBarView = findViewById(R.id.flip_bar);

        // On API 23 and below, wait until onResume to acquire the player;
        // on API 24+ (multi-window aware) it happens in onStart instead.
        if (Build.VERSION.SDK_INT <= 23) {
            initializePlayer();
        }

        loadChannels();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (flipBarView.isShowing()) {
                    flipBarView.hide();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
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

    private void loadChannels() {
        apiClient.fetchChannels(new TunarrApiClient.ChannelListCallback() {
            @Override
            public void onSuccess(List<Channel> channels) {
                runOnUiThread(() -> {
                    channelManager.setChannels(channels);
                    if (channelManager.hasChannels()) {
                        tuneToCurrentChannel();
                    } else {
                        Toast.makeText(PlayerActivity.this,
                                R.string.no_channels_available, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> Toast.makeText(PlayerActivity.this,
                        getString(R.string.no_channels_available) + ": " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void initializePlayer() {
        if (player != null) {
            return;
        }
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setPlayWhenReady(true);

        Channel current = channelManager.getCurrentChannel();
        if (current != null) {
            playChannel(current);
        }
    }

    private void releasePlayer() {
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
        flipBarView.show(current, current.getName(), false);
    }

    private void playChannel(Channel channel) {
        if (player == null) {
            return;
        }
        String streamUrl = TunarrApiClient.buildStreamUrl(channel.getId());
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(streamUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build();
        player.setMediaItem(mediaItem);
        player.prepare();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                changeChannel(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                changeChannel(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                showFlipBar();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // Reserved for future use; still resets the flip bar's dismiss timer.
                if (flipBarView.isShowing()) {
                    flipBarView.resetAutoDismissTimer();
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
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
        flipBarView.show(next, next.getName(), false);
    }

    private void showFlipBar() {
        Channel current = channelManager.getCurrentChannel();
        if (current == null) {
            return;
        }
        flipBarView.show(current, current.getName(), false);
    }
}
