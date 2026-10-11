package com.puddingkc.muses.world;

import com.puddingkc.muses.config.Settings;
import com.puddingkc.muses.media.Media;
import com.puddingkc.muses.media.MirrorProjector;
import com.puddingkc.muses.media.Projector;
import com.puddingkc.muses.media.Projectors;
import com.puddingkc.muses.net.Buffers;
import com.puddingkc.muses.net.Protocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class Stage {
    public static final int MAX_LABEL_LENGTH = 32;

    public final Venue venue;
    public final String label;
    public final String parent;

    public Vector3f c1, c2, c3, c4;
    public float su = 0, sv = 0, eu = 1, ev = 1;
    public boolean stretch = false;
    public float sx = 1, sy = 1;
    public final Map<String, Integer> flags = new HashMap<>();

    public final Map<String, Boolean> perms = new HashMap<>();

    private Projector projector;
    private Media current;
    private Media pending;
    private long startTime = System.currentTimeMillis();
    private long pendingSeek = -1;
    private float rate = 1.0f;
    private float pendingRate = 1.0f;
    private final Deque<Media> queue = new ArrayDeque<>();

    public Stage(Venue venue, String label, Vector3f c1, Vector3f c2, Vector3f c3, Vector3f c4, String parent) {
        this.venue = venue;
        this.label = label;
        this.c1 = c1;
        this.c2 = c2;
        this.c3 = c3;
        this.c4 = c4;
        this.parent = parent;
    }

    public static Stage read(ByteBuf in, Venue venue) {
        String name = Buffers.readText(in, MAX_LABEL_LENGTH);
        Vector3f a = Buffers.readVec(in);
        Vector3f b = Buffers.readVec(in);
        Vector3f c = Buffers.readVec(in);
        Vector3f d = Buffers.readVec(in);
        String owner = Buffers.readText(in, MAX_LABEL_LENGTH);
        return new Stage(venue, name, a, b, c, d, owner);
    }

    public void readFlags(ByteBuf in) {
        int count = in.readUnsignedByte();
        flags.clear();
        for (int n = 0; n < count; n++) {
            String key = Buffers.readText(in, MAX_LABEL_LENGTH);
            flags.put(key, in.readInt());
        }
    }

    public void readUv(ByteBuf in) {
        float u0 = in.readFloat();
        float v0 = in.readFloat();
        float u1 = in.readFloat();
        float v1 = in.readFloat();
        su = u0;
        sv = v0;
        eu = u1;
        ev = v1;
    }

    public void readScale(ByteBuf in) {
        boolean fill = in.readBoolean();
        float x = in.readFloat();
        float y = in.readFloat();
        stretch = fill;
        sx = x;
        sy = y;
    }

    public boolean isClone() {
        if (parent == null || parent.isEmpty()) return false;
        return true;
    }

    public boolean interactable() {
        return flag(Protocol.Flag.INTERACTABLE.key, 1) != 0;
    }

    public float aspect() {
        return flagFloat(Protocol.Flag.ASPECT.key, 0f);
    }

    public int flag(String key, int fallback) {
        if (!flags.containsKey(key)) return fallback;
        return flags.get(key);
    }

    public float flagFloat(String key, float fallback) {
        Integer stored = flags.get(key);
        if (stored == null) return fallback;
        return Float.intBitsToFloat(stored);
    }

    public Kind kind() {
        return Kind.of(flag("kind", 0));
    }

    public boolean worldProgressBar() {
        return choose("worldbar", Settings.INSTANCE.worldProgressBar);
    }

    public boolean danmaku() {
        return choose("danmaku", Settings.INSTANCE.danmaku);
    }

    public boolean dynLight() {
        return choose("dynlight", Settings.INSTANCE.dynamicLight);
    }

    public boolean spatial() {
        return choose("spatial", Settings.INSTANCE.spatialAudio);
    }

    public Vector3f center() {
        return new Vector3f(mean(c1.x, c2.x, c3.x, c4.x), mean(c1.y, c2.y, c3.y, c4.y), mean(c1.z, c2.z, c3.z, c4.z));
    }

    public Stage sourceStage() {
        if (!isClone()) return this;
        Stage owner = venue.stage(parent);
        if (owner == null) return this;
        return owner;
    }

    public Projector projector() {
        return projector;
    }

    public Media current() {
        return current;
    }

    public Media currentInfo() {
        return sourceStage().current;
    }

    public Deque<Media> queue() {
        return queue;
    }

    public long startTime() {
        return startTime;
    }

    public void setQueue(Media[] items) {
        queue.clear();
        int i = 0;
        while (i < items.length) {
            queue.add(items[i]);
            i++;
        }
    }

    public void setCorners(Vector3f n1, Vector3f n2, Vector3f n3, Vector3f n4) {
        c1.set(n1);
        c2.set(n2);
        c3.set(n3);
        c4.set(n4);
        flagsTouched();
    }

    public void metaChanged() {
        if (current != null && !isClone()) retarget();
        flagsTouched();
    }

    public void play(Media media) {
        current = media;
        if (isClone()) return;
        swapDecoder();
        armSeek();
        projector.play(media);
        rate = pendingRate;
        projector.setRate(rate);
    }

    public void stop() {
        if (projector != null) projector.stop();
        current = null;
    }

    public void setProgress(long progressMs) {
        if (projector != null) projector.seek(progressMs);
        startTime = System.currentTimeMillis() - progressMs;
    }

    public void setPendingSeek(long ms) {
        this.pendingSeek = ms;
    }

    public float rate() {
        return rate;
    }

    public void setRate(float r) {
        this.rate = r;
        this.pendingRate = r;
        if (projector == null) return;
        projector.setRate(r);
    }

    public void setPendingRate(float r) {
        this.pendingRate = r;
        this.rate = r;
    }

    public void setPending(Media media) {
        this.pending = media;
    }

    public void draw(MatrixStack matrices, VertexConsumerProvider.Immediate immediate) {
        if (projector == null) return;
        projector.draw(matrices, immediate, this);
    }

    public void swapTexture() {
        if (projector == null) return;
        projector.swapTexture();
    }

    public void update() {
        if (projector == null) return;
        projector.updateTexture();
    }

    public void onVenueLoaded() {
        Theater.addScreen(this);
        if (isClone()) {
            bindMirror();
            return;
        }
        if (pending == null) return;
        play(pending);
        pending = null;
    }

    public void onVenueUnloaded() {
        Theater.removeScreen(this);
        if (projector != null) projector.cleanup();
        projector = null;
    }

    public void cleanup() {
        if (projector != null) projector.cleanup();
        projector = null;
        current = null;
    }

    private boolean choose(String key, boolean fallback) {
        Integer value = flags.get(key);
        if (value == null) return fallback;
        return value != 0;
    }

    private static float mean(float a, float b, float c, float d) {
        return (a + b + c + d) / 4f;
    }

    private void flagsTouched() {
        if (projector != null) projector.onFlagsChanged();
    }

    private void retarget() {
        Projector previous = projector;
        projector = Projectors.from(this, previous);
        if (previous == projector) return;
        if (previous != null) previous.cleanup();
        projector.init();
        projector.play(current);
    }

    private void swapDecoder() {
        Projector previous = projector;
        projector = Projectors.from(this, previous);
        if (projector == previous) return;
        if (previous != null) previous.cleanup();
        projector.init();
    }

    private void armSeek() {
        if (pendingSeek > 0) {
            startTime = System.currentTimeMillis() - pendingSeek;
            projector.setTargetTime(pendingSeek);
            pendingSeek = -1;
            return;
        }
        projector.setTargetTime(-1);
        startTime = System.currentTimeMillis();
    }

    private void bindMirror() {
        Stage parentStage = venue.stage(parent);
        if (parentStage != null) {
            projector = new MirrorProjector(this, parentStage);
            return;
        }
        venue.afterAttach(() -> {
            Stage owner = venue.stage(parent);
            if (owner == null) return;
            projector = new MirrorProjector(this, owner);
        });
    }

    public enum Kind {
        FLAT(0), CURVED(1), SPHERE(2);

        public final int id;

        Kind(int id) {
            this.id = id;
        }

        public static Kind of(int id) {
            Kind[] all = values();
            for (int i = 0; i < all.length; i++) {
                if (all[i].id == id) return all[i];
            }
            return FLAT;
        }
    }
}
