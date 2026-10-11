package com.puddingkc.muses.light;

import com.puddingkc.muses.config.Settings;
import com.puddingkc.muses.media.Projector;
import com.puddingkc.muses.world.Stage;
import com.puddingkc.muses.world.Theater;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class DynamicLights {
    private DynamicLights() {
    }

    public record Light(double x, double y, double z, int level) {
    }

    private static volatile List<Light> snapshot = List.of();

    private static long lastSignature = Long.MIN_VALUE;
    private static long lastRebuild;

    public static boolean active() {
        return !snapshot.isEmpty();
    }

    public static void refresh() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (shouldIdle(mc)) {
            park(mc);
            return;
        }
        List<Light> built = new ArrayList<>();
        long sig = collect(built);
        List<Light> prev = snapshot;
        snapshot = built;
        long now = System.currentTimeMillis();
        if (sig != lastSignature && now - lastRebuild >= 200L) {
            lastSignature = sig;
            lastRebuild = now;
            scheduleFor(mc, prev, built);
        }
    }

    private static boolean shouldIdle(MinecraftClient mc) {
        return mc.world == null || !Theater.authenticated || !anyEnabled();
    }

    private static void park(MinecraftClient mc) {
        if (snapshot.isEmpty()) {
            return;
        }
        snapshot = List.of();
        scheduleAll(mc);
    }

    private static long collect(List<Light> into) {
        long sig = 1469598103934665603L;
        int cap = maxLevel();
        for (Stage stage : Theater.screens) {
            Integer level = levelOf(stage, cap);
            if (level == null) {
                continue;
            }
            Vector3f center = stage.center();
            into.add(new Light(center.x, center.y, center.z, level));
            sig = mix(sig, Float.floatToIntBits(Math.round(center.x)));
            sig = mix(sig, Float.floatToIntBits(Math.round(center.y)));
            sig = mix(sig, Float.floatToIntBits(Math.round(center.z)));
            sig = mix(sig, level);
        }
        return sig;
    }

    private static Integer levelOf(Stage stage, int cap) {
        if (!stage.dynLight() || stage.kind() == Stage.Kind.SPHERE) {
            return null;
        }
        Projector projector = stage.projector();
        if (projector == null) {
            return null;
        }
        float luma = projector.luma();
        if (luma <= 0.02f) {
            return null;
        }
        float curved = (float) Math.sqrt(MathHelper.clamp(luma, 0f, 1f));
        int level = MathHelper.clamp(Math.round(curved * cap), 0, 15);
        if (level <= 0) {
            return null;
        }
        return level;
    }

    private static long mix(long sig, int bits) {
        return sig * 1099511628211L ^ bits;
    }

    public static int emissionAt(double x, double y, double z) {
        List<Light> lights = snapshot;
        int peak = 0;
        for (int i = 0, n = lights.size(); i < n; i++) {
            int lv = falloff(lights.get(i), x, y, z);
            if (lv > peak) {
                peak = lv;
            }
        }
        if (peak <= 0) {
            return 0;
        }
        return Math.min(peak, 15);
    }

    private static int falloff(Light src, double x, double y, double z) {
        double dx = x - src.x;
        double dy = y - src.y;
        double dz = z - src.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return src.level - (int) Math.round(dist);
    }

    private static boolean anyEnabled() {
        for (Stage stage : Theater.screens) {
            if (stage.dynLight()) {
                return true;
            }
        }
        return Settings.INSTANCE.dynamicLight;
    }

    private static int maxLevel() {
        return switch (Settings.INSTANCE.dynamicLightStrength) {
            case 0 -> 8;
            case 2 -> 15;
            default -> 12;
        };
    }

    private static void scheduleFor(MinecraftClient mc, List<Light> older, List<Light> newer) {
        WorldRenderer wr = mc.worldRenderer;
        if (wr == null) {
            return;
        }
        for (Light src : older) {
            scheduleAround(wr, src);
        }
        for (Light src : newer) {
            scheduleAround(wr, src);
        }
    }

    private static void scheduleAround(WorldRenderer wr, Light src) {
        int radius = src.level + 1;
        int x = (int) Math.floor(src.x);
        int y = (int) Math.floor(src.y);
        int z = (int) Math.floor(src.z);
        wr.scheduleBlockRenders(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    private static void scheduleAll(MinecraftClient mc) {
        WorldRenderer wr = mc.worldRenderer;
        if (wr != null && mc.player != null) {
            int px = (int) mc.player.getX();
            int py = (int) mc.player.getY();
            int pz = (int) mc.player.getZ();
            int radius = 48;
            wr.scheduleBlockRenders(px - radius, py - radius, pz - radius, px + radius, py + radius, pz + radius);
        }
        lastSignature = Long.MIN_VALUE;
    }
}
