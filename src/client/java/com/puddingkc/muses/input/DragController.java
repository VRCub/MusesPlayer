package com.puddingkc.muses.input;

import com.puddingkc.muses.net.Outbound;
import com.puddingkc.muses.ui.Messages;
import com.puddingkc.muses.world.Stage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class DragController {
    private DragController() {
    }

    private static final float DEPTH_STEP = 0.5f;
    private static final long REVERT_TIMEOUT = 2000;
    private static final float SURFACE_OFFSET = 0.05f;

    private static Stage moving;
    private static final Vector3f[] orig = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private static final Vector3f[] local = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private static float depth;
    private static boolean snap;

    private record Revert(Vector3f[] corners, long deadline) {
    }

    private static final Map<Stage, Revert> pending = new HashMap<>();

    private static final Vector3f R = new Vector3f(), U = new Vector3f(), F = new Vector3f();
    private static final Vector3f tmp = new Vector3f();
    private static final Vector3f g0 = new Vector3f(), g1 = new Vector3f(), g2 = new Vector3f(), g3 = new Vector3f();

    public static boolean isMoving() {
        return moving != null;
    }

    public static boolean isMoving(Stage screen) {
        return moving == screen;
    }

    private static Vector3f basis(Vector3f outR, Vector3f outU, Vector3f outF) {
        MinecraftClient client = MinecraftClient.getInstance();
        float tick = client.getRenderTickCounter().getTickDelta(true);
        Vec3d eye = client.player.getCameraPosVec(tick);
        Vec3d look = client.player.getRotationVec(tick);
        outF.set((float) look.x, (float) look.y, (float) look.z).normalize();
        outR.set(0f, 1f, 0f).cross(outF);
        if (outR.lengthSquared() < 1e-6f) {
            outR.set(1f, 0f, 0f);
        }
        outR.normalize();
        outF.cross(outR, outU).normalize();
        return new Vector3f((float) eye.x, (float) eye.y, (float) eye.z);
    }

    public static void begin(Stage screen) {
        if (screen == null) {
            return;
        }
        moving = screen;
        depth = 0f;
        snap = false;
        remember(screen);
        toCamera(basis(R, U, F));
        actionBar();
    }

    private static void remember(Stage screen) {
        orig[0].set(screen.c1);
        orig[1].set(screen.c2);
        orig[2].set(screen.c3);
        orig[3].set(screen.c4);
    }

    private static void toCamera(Vector3f eye) {
        for (int i = 0; i < 4; i++) {
            orig[i].sub(eye, tmp);
            local[i].set(tmp.dot(R), tmp.dot(U), tmp.dot(F));
        }
    }

    public static void tick() {
        expire(System.currentTimeMillis());
        if (moving == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            cancel();
            return;
        }
        noteSnap(client.player.isSneaking());
        Vector3f eye = basis(R, U, F);
        rebuildFree(eye);
        if (snap) {
            snapToBlocks(eye);
        }
        moving.setCorners(g0, g1, g2, g3);
    }

    private static void expire(long now) {
        if (pending.isEmpty()) {
            return;
        }
        pending.entrySet().removeIf(entry -> {
            Revert job = entry.getValue();
            if (now <= job.deadline()) {
                return false;
            }
            Vector3f[] corners = job.corners();
            entry.getKey().setCorners(corners[0], corners[1], corners[2], corners[3]);
            return true;
        });
    }

    private static void noteSnap(boolean want) {
        if (want == snap) {
            return;
        }
        snap = want;
        actionBar();
    }

    private static void rebuildFree(Vector3f eye) {
        Vector3f[] out = {g0, g1, g2, g3};
        for (int i = 0; i < 4; i++) {
            project(out[i], eye, local[i]);
        }
    }

    private static void project(Vector3f dst, Vector3f eye, Vector3f offset) {
        dst.set(eye);
        dst.fma(offset.x, R);
        dst.fma(offset.y, U);
        dst.fma(offset.z + depth, F);
    }

    private static final Vector3f eRight = new Vector3f(), eUp = new Vector3f(), nrm = new Vector3f();
    private static final Vector3f axR = new Vector3f(), axU = new Vector3f(), center = new Vector3f();
    private static final Vector3f axN = new Vector3f();

    private static void snapToBlocks(Vector3f eye) {
        g3.sub(g0, eRight);
        g1.sub(g0, eUp);
        float spanR = eRight.length();
        float spanU = eUp.length();
        if (spanR < 1e-4f || spanU < 1e-4f) {
            return;
        }
        eRight.cross(eUp, nrm);
        if (nrm.lengthSquared() < 1e-8f) {
            return;
        }
        int face = dominantAxis(nrm);
        int across = bestAxisExcluding(eRight, face);
        int along = 3 - face - across;
        axisVec(across, eRight, axR).mul(spanR);
        axisVec(along, eUp, axU).mul(spanU);
        center.set(g0).add(g1).add(g2).add(g3).mul(0.25f);
        g0.set(center).fma(-0.5f, axR).fma(-0.5f, axU);
        g0.set(Math.round(g0.x), Math.round(g0.y), Math.round(g0.z));
        g1.set(g0).add(axU);
        g3.set(g0).add(axR);
        g2.set(g0).add(axR).add(axU);
        axN.set(face == 0 ? 1f : 0f, face == 1 ? 1f : 0f, face == 2 ? 1f : 0f);
        center.set(g0).add(g2).mul(0.5f);
        if (eye.sub(center, tmp).dot(axN) < 0f) {
            axN.negate();
        }
        axN.mul(SURFACE_OFFSET);
        g0.add(axN);
        g1.add(axN);
        g2.add(axN);
        g3.add(axN);
    }

    private static int dominantAxis(Vector3f v) {
        float[] mag = {Math.abs(v.x), Math.abs(v.y), Math.abs(v.z)};
        int pick = 0;
        for (int axis = 1; axis < 3; axis++) {
            if (mag[axis] > mag[pick]) {
                pick = axis;
            }
        }
        return pick;
    }

    private static int bestAxisExcluding(Vector3f v, int ex) {
        float[] comp = {Math.abs(v.x), Math.abs(v.y), Math.abs(v.z)};
        int pick = -1;
        float score = -1f;
        for (int axis = 0; axis < 3; axis++) {
            if (axis == ex || comp[axis] <= score) {
                continue;
            }
            score = comp[axis];
            pick = axis;
        }
        return pick;
    }

    private static Vector3f axisVec(int axis, Vector3f ref, Vector3f out) {
        out.set(axis == 0 ? 1f : 0f, axis == 1 ? 1f : 0f, axis == 2 ? 1f : 0f);
        float comp = axis == 0 ? ref.x : axis == 1 ? ref.y : ref.z;
        if (comp < 0f) {
            out.negate();
        }
        return out;
    }

    public static void addDepth(float steps) {
        if (moving == null) {
            return;
        }
        depth = Math.clamp(depth + steps * DEPTH_STEP, -64f, 256f);
        actionBar();
    }

    public static void confirm() {
        if (moving == null) {
            return;
        }
        Stage held = moving;
        Outbound.move(held, held.c1, held.c2, held.c3, held.c4);
        pending.put(held, new Revert(copiedOrig(), System.currentTimeMillis() + REVERT_TIMEOUT));
        moving = null;
        Messages.actionBar("已提交新的屏幕位置", Formatting.GREEN);
    }

    private static Vector3f[] copiedOrig() {
        return new Vector3f[] {
                new Vector3f(orig[0]), new Vector3f(orig[1]), new Vector3f(orig[2]), new Vector3f(orig[3])
        };
    }

    public static void cancel() {
        if (moving == null) {
            return;
        }
        moving.setCorners(orig[0], orig[1], orig[2], orig[3]);
        moving = null;
        Messages.actionBar("已取消移动", Formatting.YELLOW);
    }

    public static void onAuthoritativeMove(Stage screen, Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
        pending.remove(screen);
        MinecraftClient.getInstance().execute(() -> screen.setCorners(a, b, c, d));
    }

    public static void reset() {
        moving = null;
        pending.clear();
    }

    private static void actionBar() {
        Messages.actionBar("移动屏幕 | 对齐方块: " + (snap ? "开" : "关")
                + " | 深度: " + String.format("%.1f", depth)
                + " | 左键确认 右键取消 滚轮调节远近 下蹲对齐方块", Formatting.AQUA);
    }
}
