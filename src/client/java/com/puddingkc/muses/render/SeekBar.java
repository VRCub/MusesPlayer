package com.puddingkc.muses.render;

import com.puddingkc.muses.media.FlatProjector;
import com.puddingkc.muses.media.Projector;
import com.puddingkc.muses.media.SphereProjector;
import com.puddingkc.muses.world.Stage;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class SeekBar {
    private SeekBar() {
    }

    private static final RenderLayer BAR_LAYER = RenderLayer.of(
            "muses_seek_bar",
            VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS,
            256,
            false,
            true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(new RenderPhase.ShaderProgram(ShaderProgramKeys.POSITION_COLOR))
                    .transparency(RenderPhase.NO_TRANSPARENCY)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(true)
    );

    private static final class Geo {
        final Vector3f q1 = new Vector3f(), q2 = new Vector3f(), q3 = new Vector3f(), q4 = new Vector3f();
        final Vector3f right = new Vector3f();
        final Vector3f down = new Vector3f();
        float width;
    }

    private static final Geo tmpGeo = new Geo();
    private static final Vector3f a = new Vector3f(), b = new Vector3f();

    private static Stage previewStage;
    private static float previewRatio;

    public static void setPreview(Stage screen, float ratio) {
        previewStage = screen;
        previewRatio = Math.clamp(ratio, 0f, 1f);
    }

    public static void clearPreview() {
        previewStage = null;
    }

    public static boolean applicable(Stage screen) {
        if (!screen.worldProgressBar()) {
            return false;
        }
        Projector projector = screen.sourceStage().projector();
        if (!(projector instanceof FlatProjector) || projector instanceof SphereProjector) {
            return false;
        }
        return projector.total() > 0L;
    }

    private static boolean geometry(Stage s, Geo out) {
        Vector3f p1 = s.c1;
        Vector3f p2 = s.c2;
        Vector3f p3 = s.c3;
        p3.sub(p2, out.right);
        float span = out.right.length();
        if (span < 1e-4f) {
            return false;
        }
        p2.sub(p1, out.down);
        float screenH = out.down.length();
        if (screenH < 1e-4f) {
            return false;
        }
        out.down.normalize();
        out.width = span;
        float barH = screenH * 0.012f;
        if (barH < 0.028f) barH = 0.028f;
        else if (barH > 0.1f) barH = 0.1f;
        out.q1.set(p2);
        out.q4.set(p3);
        out.q2.set(out.q1).fma(barH, out.down);
        out.q3.set(out.q4).fma(barH, out.down);
        return true;
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate immediate,
                              Stage screen, boolean highlighted) {
        if (!applicable(screen) || !geometry(screen, tmpGeo)) {
            return;
        }
        float ratio = shownRatio(screen, screen.sourceStage().projector());
        matrices.push();
        matrices.translate(-WorldDraw.cameraX, -WorldDraw.cameraY, -WorldDraw.cameraZ);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        matrices.pop();
        if (ratio <= 0f) {
            return;
        }
        VertexConsumer consumer = immediate.getBuffer(BAR_LAYER);
        int fill = highlighted ? 0xFFFFD27A : 0xFFE2B15A;
        a.set(tmpGeo.q1).fma(ratio, tmpGeo.right);
        b.set(tmpGeo.q2).fma(ratio, tmpGeo.right);
        quad(consumer, mat, tmpGeo.q1, tmpGeo.q2, b, a, fill);
        immediate.draw(BAR_LAYER);
    }

    private static float shownRatio(Stage screen, Projector projector) {
        if (screen == previewStage) {
            return previewRatio;
        }
        long total = projector.total();
        if (total <= 0L) {
            return 0f;
        }
        return Math.clamp((float) projector.progress() / total, 0f, 1f);
    }

    private static void quad(VertexConsumer c, Matrix4f mat, Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4, int argb) {
        c.vertex(mat, p1.x, p1.y, p1.z).color(argb);
        c.vertex(mat, p2.x, p2.y, p2.z).color(argb);
        c.vertex(mat, p3.x, p3.y, p3.z).color(argb);
        c.vertex(mat, p4.x, p4.y, p4.z).color(argb);
    }

    public static float raycast(Vector3f start, Vector3f end, Stage screen, float[] dist) {
        if (!applicable(screen) || !geometry(screen, tmpGeo)) {
            return -1f;
        }
        float travel = planeTravel(start, end, tmpGeo);
        if (travel < 0f) {
            return -1f;
        }
        Vector3f hit = pointAt(start, end, travel);
        if (!inTri(hit, tmpGeo.q1, tmpGeo.q2, tmpGeo.q4) && !inTri(hit, tmpGeo.q2, tmpGeo.q3, tmpGeo.q4)) {
            return -1f;
        }
        if (dist != null) {
            dist[0] = travel;
        }
        return ratioAlong(hit, tmpGeo);
    }

    private static float planeTravel(Vector3f start, Vector3f end, Geo geo) {
        Vector3f edgeDown = new Vector3f();
        Vector3f edgeAcross = new Vector3f();
        Vector3f normal = new Vector3f();
        geo.q2.sub(geo.q1, edgeDown);
        geo.q3.sub(geo.q1, edgeAcross);
        edgeDown.cross(edgeAcross, normal).normalize();
        float plane = normal.dot(geo.q1);
        Vector3f dir = new Vector3f();
        end.sub(start, dir);
        float length = dir.length();
        if (length < 1e-6f) {
            return -1f;
        }
        dir.normalize();
        float denom = normal.dot(dir);
        if (Math.abs(denom) < 1e-6f) {
            return -1f;
        }
        float distance = (plane - normal.dot(start)) / denom;
        if (distance < 0f || distance > length) {
            return -1f;
        }
        return distance;
    }

    private static Vector3f pointAt(Vector3f start, Vector3f end, float distance) {
        Vector3f dir = new Vector3f();
        end.sub(start, dir);
        return dir.normalize().mul(distance).add(start);
    }

    private static float ratioAlong(Vector3f point, Geo geo) {
        Vector3f rel = new Vector3f(point).sub(geo.q1);
        float wlen = geo.right.length();
        float ratio = rel.dot(geo.right) / (wlen * wlen);
        return Math.clamp(ratio, 0f, 1f);
    }

    private static boolean inTri(Vector3f p, Vector3f va, Vector3f vb, Vector3f vc) {
        return facing(va, vb, vc, p) && facing(vb, vc, va, p) && facing(vc, va, vb, p);
    }

    private static boolean facing(Vector3f a, Vector3f b, Vector3f c, Vector3f p) {
        Vector3f edge = new Vector3f(b).sub(a);
        Vector3f plane = new Vector3f(edge).cross(new Vector3f(c).sub(a));
        Vector3f side = new Vector3f(edge).cross(new Vector3f(p).sub(a));
        return plane.dot(side) >= 0f;
    }
}
