package com.puddingkc.muses.media;

import org.lwjgl.BufferUtils;
import uk.co.caprica.vlcj.player.base.Equalizer;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class Decoder {
    private static final ExecutorService RELEASE_POOL = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "muses-vlc-release");
        thread.setDaemon(true);
        return thread;
    });

    private final EmbeddedMediaPlayer player;
    private final Sink sink = new Sink();

    private int width = 1, height = 1;
    private ByteBuffer front, back;

    private long mediaAt;
    private long wallAt;

    private volatile float luma;
    private long lastLuma;

    private BiConsumer<Integer, Integer> onSize = (w, h) -> {};
    private Runnable onFinish = () -> {};
    private Runnable onPlay = () -> {};

    private boolean muffled;

    public Decoder() {
        player = VlcEngine.factory().mediaPlayers().newEmbeddedMediaPlayer();
        player.videoSurface().set(surface());
        player.events().addMediaPlayerEventListener(events());
    }

    private CallbackVideoSurface surface() {
        BufferFormatCallbackAdapter format = new BufferFormatCallbackAdapter() {
            @Override
            public BufferFormat getBufferFormat(int sourceWidth, int sourceHeight) {
                if (front == null || back == null || width != sourceWidth || height != sourceHeight) {
                    synchronized (sink) {
                        adoptSize(sourceWidth, sourceHeight);
                    }
                }
                return new RgbaFormat(sourceWidth, sourceHeight);
            }
        };
        return new CallbackVideoSurface(format, sink, true, (mp, component) -> {});
    }

    private void adoptSize(int sourceWidth, int sourceHeight) {
        width = sourceWidth;
        height = sourceHeight;
        int bytes = sourceWidth * sourceHeight * 4;
        back = BufferUtils.createByteBuffer(bytes);
        front = BufferUtils.createByteBuffer(bytes);
        onSize.accept(width, height);
    }

    private MediaPlayerEventAdapter events() {
        return new MediaPlayerEventAdapter() {
            @Override
            public void playing(MediaPlayer mp) {
                onPlay.run();
            }

            @Override
            public void finished(MediaPlayer mp) {
                onFinish.run();
            }

            @Override
            public void error(MediaPlayer mp) {
                mp.submit(() -> mp.controls().stop());
            }

            @Override
            public void timeChanged(MediaPlayer mp, long newTime) {
                mediaAt = newTime;
                wallAt = System.currentTimeMillis();
            }
        };
    }

    public void onSize(BiConsumer<Integer, Integer> listener) {
        this.onSize = listener;
    }

    public void onFinish(Runnable listener) {
        this.onFinish = listener;
    }

    public void onPlay(Runnable listener) {
        this.onPlay = listener;
    }

    public void submit(Runnable r) {
        player.submit(r);
    }

    public void open(String mrl, String[] options) {
        mediaAt = 0;
        player.media().play(playable(mrl), options);
    }

    private static String playable(String mrl) {
        String secure = "rtspt://";
        if (mrl != null && mrl.regionMatches(false, 0, secure, 0, secure.length())) {
            return "rtsp://" + mrl.substring(secure.length());
        }
        return mrl;
    }

    public ByteBuffer nextFrame() {
        return sink.copy();
    }

    public void stop() {
        player.submit(() -> player.controls().stop());
        back = null;
        front = null;
        luma = 0;
    }

    public void cleanup() {
        stop();
        RELEASE_POOL.submit(player::release);
    }

    public boolean canPause() {
        return player.status().canPause();
    }

    public void pause(boolean pause) {
        player.controls().setPause(pause);
    }

    public boolean isPaused() {
        return !player.status().isPlaying();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public float luma() {
        return luma;
    }

    public void setVolume(int volume) {
        player.audio().setVolume(volume);
    }

    public void setMuffle(boolean on) {
        if (on == muffled) {
            return;
        }
        muffled = on;
        player.submit(() -> installEqualizer(on));
    }

    private void installEqualizer(boolean enable) {
        try {
            if (!enable) {
                player.audio().setEqualizer(null);
                return;
            }
            Equalizer eq = VlcEngine.factory().equalizer().newEqualizer();
            eq.setPreamp(-6f);
            int bands = eq.bandCount();
            float denom = Math.max(bands - 1, 1);
            for (int band = 0; band < bands; band++) {
                float t = band / denom;
                float gain = -4f - 16f * t * t;
                eq.setAmp(band, gain);
            }
            player.audio().setEqualizer(eq);
        } catch (Exception ignored) {
        }
    }

    public boolean seekable() {
        return player.status().isSeekable();
    }

    public void seek(long ms) {
        player.controls().setTime(ms);
    }

    public long progress() {
        if (mediaAt <= 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - wallAt;
        return mediaAt + Math.max(0L, elapsed);
    }

    public long total() {
        return player.status().length();
    }

    public void setRate(float rate) {
        player.controls().setRate(rate);
    }

    public float rate() {
        return player.status().rate();
    }

    private class Sink implements RenderCallback {
        private static final ByteBuffer EMPTY = BufferUtils.createByteBuffer(0);

        synchronized ByteBuffer copy() {
            if (front == null || back == null || back.remaining() != front.capacity()) {
                return EMPTY;
            }
            move(back, front);
            return front;
        }

        @Override
        public void lock(MediaPlayer m) {
        }

        @Override
        public synchronized void display(MediaPlayer mp, ByteBuffer[] buffers, BufferFormat format,
                                         int displayWidth, int displayHeight) {
            if (back == null) {
                return;
            }
            move(buffers[0], back);
            sampleLuma();
        }

        private static void move(ByteBuffer src, ByteBuffer dst) {
            int bytes = Math.min(src.capacity(), dst.capacity());
            src.limit(bytes);
            src.position(0);
            dst.clear();
            dst.put(src);
            dst.flip();
        }

        private void sampleLuma() {
            long now = System.currentTimeMillis();
            if (now - lastLuma < 100) {
                return;
            }
            lastLuma = now;
            ByteBuffer frame = back;
            if (frame == null || width <= 0 || height <= 0) {
                return;
            }
            int cap = frame.capacity();
            int stride = width * 4;
            if (cap < 4 || stride <= 0 || cap < stride) {
                return;
            }
            int grid = 32;
            int cols = Math.min(grid, width);
            int rows = Math.min(grid, Math.min(height, cap / stride));
            if (cols <= 0 || rows <= 0) {
                return;
            }
            double acc = 0;
            int n = 0;
            int maxRow = cap / stride;
            for (int gy = 0; gy < rows; gy++) {
                int py = Math.min(height - 1, gy * height / rows);
                if (py >= maxRow) {
                    py = maxRow - 1;
                }
                int row = py * stride;
                for (int gx = 0; gx < cols; gx++) {
                    int px = Math.min(width - 1, gx * width / cols);
                    int at = row + px * 4;
                    if (at + 2 >= cap) {
                        continue;
                    }
                    acc += (frame.get(at) & 0xFF) * 0.2126
                            + (frame.get(at + 1) & 0xFF) * 0.7152
                            + (frame.get(at + 2) & 0xFF) * 0.0722;
                    n++;
                }
            }
            if (n == 0) {
                return;
            }
            float target = (float) (acc / n / 255.0);
            luma = luma * 0.65f + target * 0.35f;
        }

        @Override
        public void unlock(MediaPlayer m) {
        }
    }

    public static final class RgbaFormat extends BufferFormat {
        public RgbaFormat(int width, int height) {
            super("RGBA", width, height, new int[]{width * 4}, new int[]{height});
        }
    }
}
