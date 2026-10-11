package com.puddingkc.muses.input;

import com.puddingkc.muses.render.SeekBar;
import com.puddingkc.muses.world.Raycast;
import com.puddingkc.muses.world.Stage;
import com.puddingkc.muses.world.Theater;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public final class Interaction {
    private Interaction() {
    }

    private static final float INTERACT_RADIUS = 32f;

    public static Stage looking;
    public static Stage current;
    public static boolean inVenue;

    public static Stage barTarget;
    public static float barRatio;

    public static void scan() {
        resetGaze();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || Theater.screens.isEmpty()) {
            return;
        }
        Vector3f origin = new Vector3f();
        Vector3f limit = new Vector3f();
        segment(client, origin, limit);
        looking = closestStage(origin, limit);
        if (looking == null && !DragController.isMoving()) {
            closestBar(origin, limit);
        }
        if (looking != null) {
            current = looking;
            return;
        }
        venueFallback();
    }

    private static void resetGaze() {
        looking = null;
        current = null;
        inVenue = false;
        barTarget = null;
    }

    private static void segment(MinecraftClient client, Vector3f origin, Vector3f limit) {
        float tick = client.getRenderTickCounter().getTickDelta(true);
        Vec3d eye = client.player.getCameraPosVec(tick);
        Vec3d aim = client.player.getRotationVec(tick);
        origin.set(eye.toVector3f());
        limit.set(eye.add(aim.multiply(INTERACT_RADIUS)).toVector3f());
    }

    private static Stage closestStage(Vector3f origin, Vector3f limit) {
        Stage chosen = null;
        float nearest = Float.MAX_VALUE;
        for (Stage stage : Theater.screens) {
            if (!stage.interactable() || DragController.isMoving(stage)) {
                continue;
            }
            Raycast.Hit probe = Raycast.test(origin, limit, stage);
            if (!probe.intersects || probe.distance >= nearest) {
                continue;
            }
            nearest = probe.distance;
            chosen = stage;
        }
        return chosen;
    }

    private static void closestBar(Vector3f origin, Vector3f limit) {
        float nearest = Float.MAX_VALUE;
        float[] span = new float[1];
        for (Stage stage : Theater.screens) {
            if (!stage.interactable() || DragController.isMoving(stage)) {
                continue;
            }
            float along = SeekBar.raycast(origin, limit, stage, span);
            if (along < 0f || span[0] >= nearest) {
                continue;
            }
            nearest = span[0];
            barTarget = stage;
            barRatio = along;
        }
    }

    private static void venueFallback() {
        boolean inside = false;
        Stage picked = null;
        for (var room : Theater.venues.values()) {
            if (!room.loaded) {
                continue;
            }
            inside = true;
            Stage primary = firstInteractable(room.stages());
            if (primary != null) {
                picked = primary;
            }
        }
        inVenue = inside;
        if (picked != null) {
            current = picked;
        }
    }

    private static Stage firstInteractable(Iterable<Stage> stages) {
        for (Stage stage : stages) {
            if (stage.interactable()) {
                return stage;
            }
        }
        return null;
    }
}
