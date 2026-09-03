package com.kmsma.i_guide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Loads channel logos into {@link ImageView}s, backed by an in-memory bitmap cache and
 * OkHttp's own disk cache.
 *
 * <p>The guide grid tears down and rebuilds every row on each D-pad press, so the same
 * handful of logos is asked for over and over: a memory hit has to be synchronous, which
 * is why {@link #load} paints straight from the cache before considering a fetch. Logos
 * are decoded downsampled to roughly the slot they are drawn into, so a 900px source
 * costs a few kilobytes rather than three megabytes.
 *
 * <p>Not every {@code icon.path} Tunarr hands back is something Android can decode —
 * channels pointed at an SVG on the web are common — so a URL that fails once is
 * remembered and its slot collapses, giving the call sign the space instead.
 */
public final class ChannelIconLoader {

    private static final int MEMORY_CACHE_BYTES = 4 * 1024 * 1024;
    private static final long DISK_CACHE_BYTES = 32L * 1024 * 1024;
    private static final String DISK_CACHE_DIR = "channel-icons";

    /**
     * Channel icons routinely point at public logo sites, and several of them refuse
     * OkHttp's default {@code okhttp/x.y.z} agent outright: Wikimedia's user-agent policy
     * wants a client that identifies itself, and seeklogo's bot filter 403s it. Naming
     * ourselves satisfies both without pretending to be a browser.
     */
    private static final String USER_AGENT = "i-Guide/1.0 (Android TV channel guide)";

    private static ChannelIconLoader instance;

    private final OkHttpClient httpClient;
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(
            MEMORY_CACHE_BYTES) {
        @Override
        protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
            return value.getByteCount();
        }
    };

    /** URLs that came back unusable; never requested a second time. */
    private final Set<String> failed = Collections.synchronizedSet(new HashSet<>());
    /** URLs with a request already out, so rebuilt rows do not pile on duplicates. */
    private final Set<String> inFlight = Collections.synchronizedSet(new HashSet<>());
    /** Slots waiting on a fetch. Main-thread only; entries are dropped as they go stale. */
    private final List<WeakReference<ImageView>> pending = new ArrayList<>();

    private ChannelIconLoader(Context ctx) {
        Cache cache = new Cache(new File(ctx.getCacheDir(), DISK_CACHE_DIR), DISK_CACHE_BYTES);
        this.httpClient = new OkHttpClient.Builder().cache(cache).build();
    }

    public static synchronized ChannelIconLoader get(@NonNull Context ctx) {
        if (instance == null) {
            instance = new ChannelIconLoader(ctx.getApplicationContext());
        }
        return instance;
    }

    /**
     * Points {@code view} at {@code url}, fetching it if it is not already cached.
     *
     * <p>The view holds its URL as its tag, which is what makes a late-arriving bitmap
     * safe: by the time a fetch completes the grid may have rebuilt and handed that view
     * to a different channel, and the tag check drops the stale result.
     *
     * @param targetPx roughly the size the logo is drawn at, used to pick a sample size
     */
    public void load(@NonNull ImageView view, @Nullable String url, int targetPx) {
        view.setTag(url);

        if (url == null || url.isEmpty() || failed.contains(url)) {
            view.setImageDrawable(null);
            view.setVisibility(View.GONE);
            return;
        }

        Bitmap cached = memory.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            view.setVisibility(View.VISIBLE);
            return;
        }

        // Invisible rather than gone: the slot keeps its width so the row does not
        // reflow underneath the user when the logo lands.
        view.setImageDrawable(null);
        view.setVisibility(View.INVISIBLE);
        pending.add(new WeakReference<>(view));
        fetch(url, targetPx);
    }

    private void fetch(@NonNull final String url, final int targetPx) {
        if (!inFlight.add(url)) {
            return;
        }
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                inFlight.remove(url);
                markFailed(url);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                Bitmap bitmap = null;
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        bitmap = decode(resp.body().bytes(), targetPx);
                    }
                } catch (Exception ignored) {
                    // Falls through to the failure path below.
                } finally {
                    inFlight.remove(url);
                }

                if (bitmap == null) {
                    markFailed(url);
                    return;
                }
                memory.put(url, bitmap);
                final Bitmap loaded = bitmap;
                mainThread.post(() -> deliver(url, loaded));
            }
        });
    }

    private void markFailed(@NonNull final String url) {
        failed.add(url);
        mainThread.post(() -> deliver(url, null));
    }

    /** Hands a finished load to every waiting slot still asking for that URL. */
    private void deliver(@NonNull String url, @Nullable Bitmap bitmap) {
        for (Iterator<WeakReference<ImageView>> it = pending.iterator(); it.hasNext(); ) {
            ImageView view = it.next().get();
            if (view == null) {
                it.remove();
                continue;
            }
            if (!url.equals(view.getTag())) {
                continue;
            }
            it.remove();
            if (bitmap != null) {
                view.setImageBitmap(bitmap);
                view.setVisibility(View.VISIBLE);
            } else {
                view.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Decodes downsampled to the smallest power-of-two step that still covers
     * {@code targetPx}. Returns null for anything Android cannot decode, SVGs included.
     */
    @Nullable
    private static Bitmap decode(@NonNull byte[] data, int targetPx) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);

        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        if (targetPx > 0) {
            while (largest / (opts.inSampleSize * 2) >= targetPx) {
                opts.inSampleSize *= 2;
            }
        }
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }
}
