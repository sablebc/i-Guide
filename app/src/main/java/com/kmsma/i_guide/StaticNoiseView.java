package com.kmsma.i_guide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * The burst of analog snow that covers the picture while the tuner changes channels.
 *
 * <p>A handful of low-resolution noise bitmaps are generated once and then upscaled
 * with filtering off, so the grain lands as chunky nearest-neighbour blocks the way a
 * CRT renders it rather than as a smooth blur. On top of that go the two artefacts
 * that sell it: a soft roll bar drifting up the screen and a scanline grille darkening
 * every third line.
 *
 * <p>The burst is open-ended by design. {@link #burst()} starts it when a new channel
 * is tuned and it holds — hiding the black frame while the stream buffers — until
 * {@link #settle()} reports that the new picture is on screen, subject to a floor of
 * {@link #MIN_HOLD_MS} so a fast tune still reads as a flip, and a ceiling of
 * {@link #MAX_HOLD_MS} so a stream that never arrives does not leave snow up forever.
 */
public class StaticNoiseView extends View {

    /** Resolution of the noise tiles; upscaled to the panel, this sets the grain size. */
    private static final int NOISE_WIDTH = 384;
    private static final int NOISE_HEIGHT = 216;

    /** Distinct noise frames cycled at random. Enough that the loop is not readable. */
    private static final int FRAME_COUNT = 5;

    /** Shortest a burst can last, so an instant tune still registers as a flip. */
    private static final long MIN_HOLD_MS = 340L;

    /** Longest the snow holds waiting for a picture that may never come. */
    private static final long MAX_HOLD_MS = 2200L;

    /** Fade from full snow back to picture. */
    private static final long FADE_MS = 260L;

    /** Roll bar travel, in fractions of the screen height per millisecond. */
    private static final float ROLL_SPEED = 0.0009f;

    private final Paint noisePaint = new Paint();
    private final Paint rollPaint = new Paint();
    private final Paint scanlinePaint = new Paint();
    private final Rect dst = new Rect();
    private final Random random = new Random();

    @Nullable
    private Bitmap[] frames;
    private int lastFrame = -1;

    private boolean running;
    private long startedAt;
    /** When the fade-out began, or 0 while the snow is still holding at full strength. */
    private long fadeStartedAt;
    /** Set by {@link #settle()}: the picture is ready, fade as soon as the floor allows. */
    private boolean settled;

    public StaticNoiseView(Context context) {
        this(context, null);
    }

    public StaticNoiseView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        // Nearest-neighbour upscale: the blocks are the point.
        noisePaint.setFilterBitmap(false);
        noisePaint.setAntiAlias(false);
        noisePaint.setDither(false);

        rollPaint.setColor(Color.WHITE);
        scanlinePaint.setColor(Color.BLACK);

        setVisibility(GONE);
    }

    // ---- Burst lifecycle ---------------------------------------------------

    /** Slams the snow up at full strength and holds it until {@link #settle()}. */
    public void burst() {
        ensureFrames();
        running = true;
        settled = false;
        startedAt = SystemClock.uptimeMillis();
        fadeStartedAt = 0L;
        setVisibility(VISIBLE);
        invalidate();
    }

    /** Reports that the new channel is on screen; the snow fades off after the floor. */
    public void settle() {
        if (running) {
            settled = true;
        }
    }

    /** Drops the snow immediately, e.g. when a guide page takes over the screen. */
    public void clear() {
        running = false;
        settled = false;
        fadeStartedAt = 0L;
        setVisibility(GONE);
    }

    public boolean isBursting() {
        return running;
    }

    // ---- Drawing -----------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        Bitmap[] tiles = frames;
        if (!running || tiles == null) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        long elapsed = now - startedAt;

        if (fadeStartedAt == 0L
                && ((settled && elapsed >= MIN_HOLD_MS) || elapsed >= MAX_HOLD_MS)) {
            fadeStartedAt = now;
        }

        float strength = 1f;
        if (fadeStartedAt != 0L) {
            float t = (now - fadeStartedAt) / (float) FADE_MS;
            if (t >= 1f) {
                clear();
                return;
            }
            strength = 1f - t;
        }

        int width = getWidth();
        int height = getHeight();

        // Vertical jitter of a few source rows reads as an unlocked picture.
        int jitter = random.nextInt(9) - 4;
        dst.set(0, jitter, width, height + jitter);

        noisePaint.setAlpha(Math.round(255 * strength));
        canvas.drawBitmap(tiles[pickFrame(tiles.length)], null, dst, noisePaint);

        drawRollBar(canvas, width, height, elapsed, strength);
        drawScanlines(canvas, width, height, strength);

        postInvalidateOnAnimation();
    }

    /** Never repeats the previous tile, so the cycle cannot be read as a loop. */
    private int pickFrame(int count) {
        int next = random.nextInt(count);
        if (next == lastFrame) {
            next = (next + 1) % count;
        }
        lastFrame = next;
        return next;
    }

    /** The soft bright band that drifts up the screen on an unlocked analog signal. */
    private void drawRollBar(Canvas canvas, int width, int height, long elapsed, float strength) {
        float bandHeight = height * 0.14f;
        float travel = height + bandHeight;
        float top = travel - ((elapsed * ROLL_SPEED * height) % travel) - bandHeight;

        rollPaint.setAlpha(Math.round(38 * strength));
        canvas.drawRect(0, top, width, top + bandHeight, rollPaint);
    }

    /** Darkens every third line: the phosphor grille the noise is being drawn onto. */
    private void drawScanlines(Canvas canvas, int width, int height, float strength) {
        scanlinePaint.setAlpha(Math.round(46 * strength));
        float lineHeight = Math.max(1f, Ui.px(getContext(), 1f));
        float pitch = lineHeight * 3f;
        for (float y = 0; y < height; y += pitch) {
            canvas.drawRect(0, y, width, y + lineHeight, scanlinePaint);
        }
    }

    // ---- Noise generation --------------------------------------------------

    /**
     * Builds the noise tiles once. The samples are pushed towards the extremes rather
     * than left uniform because analog snow is mostly hard black and hard white with
     * comparatively little mid grey.
     */
    private void ensureFrames() {
        if (frames != null) {
            return;
        }
        int pixelCount = NOISE_WIDTH * NOISE_HEIGHT;
        int[] pixels = new int[pixelCount];
        Bitmap[] tiles = new Bitmap[FRAME_COUNT];

        for (int f = 0; f < FRAME_COUNT; f++) {
            for (int i = 0; i < pixelCount; i++) {
                int v = random.nextInt(256);
                v = v < 128 ? v >> 1 : 255 - ((255 - v) >> 1);
                pixels[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
            }
            Bitmap tile = Bitmap.createBitmap(NOISE_WIDTH, NOISE_HEIGHT, Bitmap.Config.ARGB_8888);
            tile.setPixels(pixels, 0, NOISE_WIDTH, 0, 0, NOISE_WIDTH, NOISE_HEIGHT);
            tiles[f] = tile;
        }
        frames = tiles;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
    }
}
