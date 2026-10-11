package com.puddingkc.muses.media;

import com.puddingkc.muses.config.Settings;
import com.puddingkc.muses.world.Stage;
import com.puddingkc.muses.world.Theater;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class SpatialAudio {
    private SpatialAudio() {
    }

    private static final Map<String, Float> smoothed = new HashMap<>();
    private static long lastUpdate;

    public static void update() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || !Theater.authenticated) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastUpdate < 50) {
            return;
        }
        float dt = (now - lastUpdate) / 1000f;
        lastUpdate = now;
        Vec3d cam = mc.player.getEyePos();
        int range = Math.max(1, Settings.INSTANCE.audioRange);
        for (Stage s : Theater.screens) {
            mix(mc, s, cam, range, dt);
        }
        dropStale();
    }

    private static void mix(MinecraftClient mc, Stage s, Vec3d cam, int range, float dt) {
        if (s.isClone()) {
            return;
        }
        Projector p = s.projector();
        if (p == null) {
            return;
        }
        int baseVol = Settings.INSTANCE.volume;
        String key = s.label + "@" + s.venue.label;
        if (!s.spatial()) {
            if (smoothed.remove(key) != null) {
                p.setMuffle(false);
                p.setVolume(baseVol);
            }
            return;
        }
        Vector3f c = s.center();
        Vec3d center = new Vec3d(c.x, c.y, c.z);
        double dist = cam.distanceTo(center);
        float atten = (float) Math.max(0, 1 - dist / range);
        atten *= atten;
        boolean blocked = isOccluded(mc, cam, center);
        float target = baseVol * atten * (blocked ? 0.6f : 1f);
        float cur = smoothed.getOrDefault(key, target);
        float a = Math.min(1f, dt / 0.2f);
        cur += (target - cur) * a;
        smoothed.put(key, cur);
        p.setMuffle(blocked);
        p.setVolume(Math.round(cur));
    }

    private static void dropStale() {
        smoothed.keySet().removeIf(key -> {
            for (Stage s : Theater.screens) {
                if (key.equals(s.label + "@" + s.venue.label)) {
                    return false;
                }
            }
            return true;
        });
    }

    private static boolean isOccluded(MinecraftClient mc, Vec3d from, Vec3d to) {
        try {
            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                    from, to,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            return hit != null && hit.getType() == HitResult.Type.BLOCK;
        } catch (Exception e) {
            return false;
        }
    }
}
