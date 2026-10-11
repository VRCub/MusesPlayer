package com.puddingkc.muses.media;

import com.puddingkc.muses.world.Stage;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public record MirrorProjector(Stage self, Stage src) implements Projector {
    private Projector host() {
        return src.projector();
    }

    @Override
    public Stage source() {
        return src;
    }

    @Override
    public Stage tracking() {
        return self;
    }

    @Override
    public void init() {
    }

    @Override
    public void play(Media media) {
    }

    @Override
    public void stop() {
        Projector host = host();
        if (host != null) {
            host.stop();
        }
    }

    @Override
    public void cleanup() {
    }

    @Override
    public void updateTexture() {
    }

    @Override
    public int textureId() {
        Projector host = host();
        if (host == null) {
            return -1;
        }
        return host.textureId();
    }

    @Override
    public int width() {
        Projector host = host();
        if (host == null) {
            return 0;
        }
        return host.width();
    }

    @Override
    public int height() {
        Projector host = host();
        if (host == null) {
            return 0;
        }
        return host.height();
    }

    @Override
    public boolean canPause() {
        return false;
    }

    @Override
    public void pause(boolean pause) {
    }

    @Override
    public boolean isPaused() {
        return false;
    }

    @Override
    public void setVolume(int volume) {
    }

    @Override
    public boolean canSeek() {
        return false;
    }

    @Override
    public void seek(long progress) {
    }

    @Override
    public long progress() {
        Projector host = host();
        if (host == null) {
            return 0;
        }
        return host.progress();
    }

    @Override
    public long total() {
        Projector host = host();
        if (host == null) {
            return 0;
        }
        return host.total();
    }

    @Override
    public void setTargetTime(long targetTime) {
    }

    @Override
    public void emit(Matrix4f mat, VertexConsumer consumer, Vector3f p1, Vector3f p2, Vector3f p3,
                     Vector3f p4, float u1, float v1, float u2, float v2) {
        Projector host = host();
        boolean flipU = host != null && host.flippedX();
        boolean flipV = host != null && host.flippedY();
        float left = flipU ? u2 : u1;
        float right = flipU ? u1 : u2;
        float topV = flipV ? v2 : v1;
        float botV = flipV ? v1 : v2;
        Projector.super.emit(mat, consumer, p1, p2, p3, p4, left, topV, right, botV);
    }
}
