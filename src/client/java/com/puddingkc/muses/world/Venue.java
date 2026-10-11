package com.puddingkc.muses.world;

import com.puddingkc.muses.net.Buffers;
import io.netty.buffer.ByteBuf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Venue {
    public final String label;
    public final Vector3f lo;
    public final Vector3f hi;

    private final Map<String, Stage> stages = new LinkedHashMap<>();
    public boolean loaded = false;

    private final List<Runnable> deferred = new ArrayList<>();

    public Venue(String label, Vector3f lo, Vector3f hi) {
        this.label = label;
        this.lo = lo;
        this.hi = hi;
    }

    public static Venue read(String label, ByteBuf in) {
        Vector3f min = Buffers.readVec(in);
        Vector3f max = Buffers.readVec(in);
        Buffers.readText(in, Stage.MAX_LABEL_LENGTH);
        Buffers.readText(in, 256);
        return new Venue(label, min, max);
    }

    public Stage stage(String label) {
        return stages.get(label);
    }

    public Collection<Stage> stages() {
        return stages.values();
    }

    public void attach(Stage stage) {
        stages.put(stage.label, stage);
        if (loaded) stage.onVenueLoaded();
        flushDeferred();
    }

    public Stage detach(String label) {
        Stage gone = stages.remove(label);
        if (gone == null || !loaded) return gone;
        gone.onVenueUnloaded();
        return gone;
    }

    public void afterAttach(Runnable r) {
        deferred.add(r);
    }

    public void load() {
        loaded = true;
        visit(Stage::onVenueLoaded);
    }

    public void unload() {
        loaded = false;
        visit(Stage::onVenueUnloaded);
    }

    public void dispose() {
        visit(Stage::cleanup);
        stages.clear();
    }

    private void visit(Consumer<Stage> fn) {
        for (Stage stage : stages.values()) fn.accept(stage);
    }

    private void flushDeferred() {
        if (deferred.isEmpty()) return;
        List<Runnable> jobs = new ArrayList<>(deferred);
        deferred.clear();
        for (Runnable job : jobs) job.run();
    }
}
