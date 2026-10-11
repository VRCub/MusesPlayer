package com.puddingkc.muses.danmaku;

import com.puddingkc.muses.media.FlatProjector;
import com.puddingkc.muses.media.Media;
import com.puddingkc.muses.media.Projector;
import com.puddingkc.muses.media.SphereProjector;
import com.puddingkc.muses.render.WorldDraw;
import com.puddingkc.muses.world.Stage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public final class DanmakuDraw {
    private DanmakuDraw() {
    }

    private static final long SCROLL_MS = 9000;
    private static final long FIXED_MS = 4500;
    private static final int ROWS = 28;
    private static final int LINE_PX = 11;
    private static final int MAX_ON_SCREEN = 160;
    private static final float NORMAL_EPS = 0.015f;
    private static final int LIGHT = 0xF000F0;

    private static final Vector3f right = new Vector3f(), down = new Vector3f(), normal = new Vector3f();
    private static final Vector3f origin = new Vector3f();

    public static boolean applicable(Stage screen) {
        if (!screen.danmaku()) return false;
        Stage src = screen.sourceStage();
        Projector projector = src.projector();
        if (!(projector instanceof FlatProjector)) return false;
        if (projector instanceof SphereProjector) return false;
        if (projector.total() <= 0) return false;
        Media info = src.current();
        if (info == null) return false;
        String cid = info.danmaku();
        return cid != null && !cid.isEmpty();
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Stage screen) {
        if (!applicable(screen)) return;
        Stage src = screen.sourceStage();
        Media info = src.current();
        List<Bullet> list = Danmaku.get(info.danmaku());
        if (list == null || list.isEmpty()) return;
        Projector projector = src.projector();
        long now = projector.progress();
        Sheet sheet = sheet(screen);
        if (sheet == null) return;

        matrices.push();
        matrices.translate(-WorldDraw.cameraX, -WorldDraw.cameraY, -WorldDraw.cameraZ);
        Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix()).mul(sheet.basis);
        matrices.pop();

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int drawn = 0;
        int[] lanes = new int[2];
        int start = lowerBound(list, now - SCROLL_MS);
        for (int i = start; i < list.size() && drawn < MAX_ON_SCREEN; i++) {
            Bullet b = list.get(i);
            if (b.timeMs() > now) break;
            Spot spot = spot(b, now - b.timeMs(), tr.getWidth(b.text()), sheet.canvasWpx, sheet.canvasHpx, lanes);
            if (spot == null) continue;
            int color = (0xFF << 24) | (b.color() & 0xFFFFFF);
            tr.draw(b.text(), spot.x, spot.y, color, true, base, immediate,
                    TextRenderer.TextLayerType.NORMAL, 0, LIGHT);
            drawn++;
        }
        immediate.draw();
    }

    private static Sheet sheet(Stage screen) {
        Vector3f p1 = screen.c1;
        screen.c4.sub(p1, right);
        screen.c2.sub(p1, down);
        float wWorld = right.length();
        float hWorld = down.length();
        if (wWorld < 1e-4f || hWorld < 1e-4f) return null;
        right.cross(down, normal).normalize();
        right.div(wWorld);
        down.div(hWorld);
        float camX = WorldDraw.cameraX - p1.x;
        float camY = WorldDraw.cameraY - p1.y;
        float camZ = WorldDraw.cameraZ - p1.z;
        if (normal.x * camX + normal.y * camY + normal.z * camZ < 0) normal.negate();
        float scale = hWorld / (ROWS * LINE_PX);
        float canvasWpx = wWorld / scale;
        float canvasHpx = ROWS * LINE_PX;
        origin.set(p1).fma(NORMAL_EPS, normal);
        Matrix4f basis = new Matrix4f(
                right.x * scale, right.y * scale, right.z * scale, 0,
                down.x * scale, down.y * scale, down.z * scale, 0,
                normal.x, normal.y, normal.z, 0,
                origin.x, origin.y, origin.z, 1
        );
        return new Sheet(basis, canvasWpx, canvasHpx);
    }

    private static Spot spot(Bullet b, long age, int textW, float canvasWpx, float canvasHpx, int[] lanes) {
        if (b.scroll()) {
            float progress = age / (float) SCROLL_MS;
            if (progress > 1f) return null;
            float span = canvasWpx + textW;
            float x = (1f - progress) * span - textW;
            float y = Math.floorMod(rowHash(b), ROWS) * LINE_PX;
            return new Spot(x, y);
        }
        if (!b.bottom() && !b.top()) return null;
        if (age > FIXED_MS) return null;
        float x = (canvasWpx - textW) / 2f;
        if (b.bottom()) {
            float y = canvasHpx - (lanes[0] + 1) * LINE_PX;
            lanes[0] = (lanes[0] + 1) % (ROWS / 2);
            return new Spot(x, y);
        }
        float y = lanes[1] * LINE_PX;
        lanes[1] = (lanes[1] + 1) % (ROWS / 2);
        return new Spot(x, y);
    }

    private static int rowHash(Bullet b) {
        long stamp = b.timeMs();
        int mixed = Long.hashCode(stamp) * 0x9E3779B9;
        return mixed + b.text().hashCode() * 33;
    }

    private static int lowerBound(List<Bullet> list, long target) {
        int n = list.size();
        if (n == 0 || list.get(0).timeMs() >= target) return 0;
        int step = 1;
        while (step < n && list.get(step).timeMs() < target) {
            int shifted = step << 1;
            if (shifted <= step) {
                step = n;
                break;
            }
            step = shifted;
        }
        int lo = step >>> 1;
        int hi = Math.min(step, n);
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (list.get(mid).timeMs() < target) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private record Sheet(Matrix4f basis, float canvasWpx, float canvasHpx) {
    }

    private record Spot(float x, float y) {
    }
}
