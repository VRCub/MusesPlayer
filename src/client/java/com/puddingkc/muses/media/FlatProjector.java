package com.puddingkc.muses.media;

import com.puddingkc.muses.config.Settings;
import com.puddingkc.muses.world.Stage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class FlatProjector implements Projector {
    protected final Stage stage;
    protected Decoder decoder;
    protected Texture texture;
    protected boolean initialized = false;
    protected boolean changed = false;
    protected long targetTime = -1;
    public int videoWidth, videoHeight;

    public FlatProjector(Stage stage) {
        this.stage = stage;
    }

    @Override
    public Stage source() {
        return stage;
    }

    @Override
    public Stage tracking() {
        return stage;
    }

    @Override
    public synchronized void init() {
        if (initialized) {
            return;
        }
        decoder = new Decoder();
        hookDecoder();
        texture = new Texture(decoder.width(), decoder.height());
        initialized = true;
    }

    private void hookDecoder() {
        decoder.onSize((w, h) -> {
            changed = true;
            MinecraftClient.getInstance().execute(() -> {
                videoWidth = w;
                videoHeight = h;
                texture.resize(w, h);
                changed = false;
            });
        });
        decoder.onFinish(() -> MinecraftClient.getInstance().execute(() -> texture.resize(1, 1)));
    }

    @Override
    public void play(Media media) {
        String[] options = media.options();
        if (targetTime > 0) {
            options = withStart(options, targetTime);
        }
        decoder.onPlay(() -> decoder.submit(() -> decoder.setVolume(Settings.INSTANCE.volume)));
        decoder.open(media.mrl(), options);
    }

    private static String[] withStart(String[] options, long ms) {
        String[] grown = Arrays.copyOf(options, options.length + 1);
        grown[options.length] = ":start-time=" + ms / 1000f;
        return grown;
    }

    @Override
    public void updateTexture() {
        if (changed || !initialized) {
            return;
        }
        ByteBuffer buf = decoder.nextFrame();
        if (buf == null || buf.capacity() == 0) {
            return;
        }
        texture.upload(buf);
    }

    @Override
    public int textureId() {
        if (!initialized) {
            return -1;
        }
        return texture.id();
    }

    @Override
    public int width() {
        return videoWidth;
    }

    @Override
    public int height() {
        return videoHeight;
    }

    @Override
    public void stop() {
        if (decoder != null) {
            decoder.stop();
        }
        if (texture != null) {
            texture.stop();
        }
    }

    @Override
    public synchronized void cleanup() {
        initialized = false;
        if (decoder != null) {
            decoder.cleanup();
        }
        if (texture != null) {
            texture.cleanup();
        }
    }

    @Override
    public boolean canPause() {
        return decoder.canPause();
    }

    @Override
    public void pause(boolean pause) {
        decoder.pause(pause);
    }

    @Override
    public boolean isPaused() {
        return decoder.isPaused();
    }

    @Override
    public void setVolume(int volume) {
        decoder.setVolume(volume);
    }

    @Override
    public float luma() {
        if (decoder == null) {
            return 0;
        }
        return decoder.luma();
    }

    @Override
    public void setMuffle(boolean on) {
        if (decoder != null) {
            decoder.setMuffle(on);
        }
    }

    @Override
    public boolean canSeek() {
        return decoder.seekable();
    }

    @Override
    public void seek(long progress) {
        decoder.seek(progress);
    }

    @Override
    public long progress() {
        return decoder.progress();
    }

    @Override
    public long total() {
        return decoder.total();
    }

    @Override
    public void setTargetTime(long targetTime) {
        this.targetTime = targetTime;
    }

    @Override
    public void setRate(float rate) {
        decoder.setRate(rate);
    }

    @Override
    public float rate() {
        return decoder.rate();
    }

    private static final Vector3f cW = new Vector3f(), cH = new Vector3f(), cN = new Vector3f();
    private static final Vector3f top = new Vector3f(), bot = new Vector3f();
    private static final Vector3f topN = new Vector3f(), botN = new Vector3f();

    @Override
    public void emit(Matrix4f mat, VertexConsumer consumer, Vector3f p1, Vector3f p2, Vector3f p3,
                     Vector3f p4, float u1, float v1, float u2, float v2) {
        int curve = stage.kind() == Stage.Kind.CURVED ? stage.flag("curve", 0) : 0;
        if (curve == 0) {
            Projector.super.emit(mat, consumer, p1, p2, p3, p4, u1, v1, u2, v2);
            return;
        }
        drawCurved(mat, consumer, p1, p2, p3, p4, u1, v1, u2, v2, curve);
    }

    private void drawCurved(Matrix4f mat, VertexConsumer consumer, Vector3f p1, Vector3f p2, Vector3f p3,
                            Vector3f p4, float u1, float v1, float u2, float v2, int curve) {
        int seg = Math.clamp(stage.flag("seg", 32), 1, 128);
        int gray = (int) (Settings.INSTANCE.brightness / 100.0 * 255);
        int color = 0xFF000000 | (gray << 16) | (gray << 8) | gray;

        p4.sub(p1, cW);
        p2.sub(p1, cH);
        cW.cross(cH, cN);
        if (cN.lengthSquared() < 1e-9f) {
            Projector.super.emit(mat, consumer, p1, p2, p3, p4, u1, v1, u2, v2);
            return;
        }
        cN.normalize();
        float depth = cW.length() * (curve / 100f);
        float du = u2 - u1;

        for (int i = 0; i < seg; i++) {
            float t0 = i / (float) seg;
            float t1 = (i + 1) / (float) seg;
            p1.lerp(p4, t0, top);
            p2.lerp(p3, t0, bot);
            p1.lerp(p4, t1, topN);
            p2.lerp(p3, t1, botN);
            float o0 = depth * arch(t0);
            float o1 = depth * arch(t1);
            float ua = u1 + du * t0;
            float ub = u1 + du * t1;
            corner(mat, consumer, top, o0, ua, v1, color);
            corner(mat, consumer, bot, o0, ua, v2, color);
            corner(mat, consumer, botN, o1, ub, v2, color);
            corner(mat, consumer, topN, o1, ub, v1, color);
        }
    }

    private static float arch(float t) {
        return (float) Math.sin(Math.PI * t);
    }

    private static void corner(Matrix4f mat, VertexConsumer consumer, Vector3f p, float offset,
                               float u, float v, int color) {
        consumer.vertex(mat, p.x + cN.x * offset, p.y + cN.y * offset, p.z + cN.z * offset)
                .texture(u, v).color(color);
    }
}
