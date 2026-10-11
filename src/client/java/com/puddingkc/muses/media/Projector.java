package com.puddingkc.muses.media;

import com.puddingkc.muses.config.Settings;
import com.puddingkc.muses.render.WorldDraw;
import com.puddingkc.muses.world.Stage;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public interface Projector {
    Vector3f t1 = new Vector3f(), t2 = new Vector3f(), t3 = new Vector3f(), t4 = new Vector3f();

    Stage source();

    Stage tracking();

    void init();

    void play(Media media);

    void stop();

    void cleanup();

    void updateTexture();

    default void swapTexture() {
    }

    default void onFlagsChanged() {
    }

    int textureId();

    int width();

    int height();

    boolean canPause();

    void pause(boolean pause);

    boolean isPaused();

    void setVolume(int volume);

    default float luma() {
        return 0;
    }

    default void setMuffle(boolean on) {
    }

    default void setRate(float rate) {
    }

    default float rate() {
        return 1.0f;
    }

    boolean canSeek();

    void seek(long progress);

    long progress();

    long total();

    void setTargetTime(long targetTime);

    default boolean flippedX() {
        return false;
    }

    default boolean flippedY() {
        return false;
    }

    default void draw(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Stage s) {
        Stage src = source();
        if (src == null) {
            return;
        }
        int texId = textureId();
        if (texId < 0) {
            return;
        }

        Vector3f p1 = s.c1, p2 = s.c2, p3 = s.c3, p4 = s.c4;
        float du = Math.abs(s.su - s.eu);
        float dv = Math.abs(s.sv - s.ev);
        if (width() == 0 || height() == 0 || du == 0 || dv == 0) {
            return;
        }

        float sx = p1.sub(p4, t1).length() / (width() * du) * s.sx;
        float sy = p1.sub(p2, t1).length() / (height() * dv) * s.sy;
        boolean fx = flippedX();
        boolean fy = flippedY();

        matrices.push();
        matrices.translate(-WorldDraw.cameraX, -WorldDraw.cameraY, -WorldDraw.cameraZ);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        matrices.pop();

        RenderLayer layer = WorldDraw.quadLayer(texId);
        VertexConsumer consumer = immediate.getBuffer(layer);

        float u1 = s.su;
        float u2 = s.eu;
        float v1 = s.sv;
        float v2 = s.ev;
        if (fx) {
            float held = u1;
            u1 = u2;
            u2 = held;
        }
        if (fy) {
            float held = v1;
            v1 = v2;
            v2 = held;
        }

        float scale = 1f;
        if (!s.stretch) {
            scale = Math.min(sx, sy) / Math.max(sx, sy);
        }
        if (scale == 1f) {
            emit(mat, consumer, p1, p2, p3, p4, u1, v1, u2, v2);
            return;
        }
        float inset = (1f - scale) / 2f;
        boolean letter = sx < sy;
        Vector3f toward1 = letter ? p2 : p4;
        Vector3f toward2 = letter ? p1 : p3;
        Vector3f toward3 = letter ? p4 : p2;
        Vector3f toward4 = letter ? p3 : p1;
        emit(mat, consumer,
                p1.lerp(toward1, inset, t1),
                p2.lerp(toward2, inset, t2),
                p3.lerp(toward3, inset, t3),
                p4.lerp(toward4, inset, t4),
                u1, v1, u2, v2);
        immediate.draw(layer);
    }

    default void emit(Matrix4f mat, VertexConsumer consumer, Vector3f p1, Vector3f p2, Vector3f p3,
                      Vector3f p4, float u1, float v1, float u2, float v2) {
        int level = (int) (Settings.INSTANCE.brightness / 100.0 * 255);
        int color = 0xFF000000 | (level * 0x010101);
        consumer.vertex(mat, p1.x, p1.y, p1.z).texture(u1, v1).color(color);
        consumer.vertex(mat, p2.x, p2.y, p2.z).texture(u1, v2).color(color);
        consumer.vertex(mat, p3.x, p3.y, p3.z).texture(u2, v2).color(color);
        consumer.vertex(mat, p4.x, p4.y, p4.z).texture(u2, v1).color(color);
    }
}
