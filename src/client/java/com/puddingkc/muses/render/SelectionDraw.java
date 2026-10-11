package com.puddingkc.muses.render;

import com.puddingkc.muses.ui.CreateSelector;
import com.puddingkc.muses.world.Stage;
import com.puddingkc.muses.world.Theater;
import com.puddingkc.muses.world.Venue;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
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

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public final class SelectionDraw {
    private SelectionDraw() {
    }

    private static final RenderLayer LAYER = RenderLayer.of(
            "muses_selection",
            VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS,
            256,
            false,
            true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(new RenderPhase.ShaderProgram(ShaderProgramKeys.POSITION_COLOR))
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(true)
    );

    private static final RenderLayer LINES = RenderLayer.of(
            "muses_selection_lines",
            VertexFormats.LINES,
            VertexFormat.DrawMode.LINES,
            256,
            false,
            true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(new RenderPhase.ShaderProgram(ShaderProgramKeys.RENDERTYPE_LINES))
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(3.0)))
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .depthTest(RenderPhase.ALWAYS_DEPTH_TEST)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(true)
    );

    private static final int EXIST_FILL = 0x3033C9C9;
    private static final int EXIST_EDGE = 0xFF33C9C9;

    private record Aabb(float x1, float y1, float z1, float x2, float y2, float z2) {
    }

    public static void render(WorldRenderContext ctx) {
        if (!Theater.authenticated || !CreateSelector.active()) {
            return;
        }
        List<Vector3f> points = CreateSelector.points();
        List<Stage> placed = existingScreens();
        MatrixStack matrices = ctx.matrixStack();
        matrices.push();
        matrices.translate(-WorldDraw.cameraX, -WorldDraw.cameraY, -WorldDraw.cameraZ);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        VertexConsumerProvider.Immediate immediate = (VertexConsumerProvider.Immediate) ctx.consumers();
        Aabb venue = venueAabb(points);
        Vector3f[] preview = screenPreview(points);
        fillFaces(immediate, mat, placed, preview);
        stroke(immediate, mat, points, placed, venue, preview);
        matrices.pop();
    }

    private static Aabb venueAabb(List<Vector3f> points) {
        if (CreateSelector.mode() != CreateSelector.Mode.VENUE || points.size() < 2) {
            return null;
        }
        Vector3f from = points.get(0);
        Vector3f to = points.get(1);
        return new Aabb(
                Math.min(from.x, to.x),
                Math.min(from.y, to.y),
                Math.min(from.z, to.z),
                Math.max(from.x, to.x) + 1f,
                Math.max(from.y, to.y) + 1f,
                Math.max(from.z, to.z) + 1f
        );
    }

    private static Vector3f[] screenPreview(List<Vector3f> points) {
        if (CreateSelector.mode() != CreateSelector.Mode.SCREEN || points.size() < 4) {
            return null;
        }
        return insetCorners(points.get(0), points.get(1), points.get(2), points.get(3));
    }

    private static void fillFaces(VertexConsumerProvider.Immediate immediate, Matrix4f mat,
                                  List<Stage> placed, Vector3f[] preview) {
        VertexConsumer consumer = immediate.getBuffer(LAYER);
        for (Stage stage : placed) {
            quad(consumer, mat, stage.c1, stage.c2, stage.c3, stage.c4, EXIST_FILL);
        }
        if (preview != null) {
            quad(consumer, mat, preview[0], preview[1], preview[2], preview[3], 0x80FFD700);
        }
        immediate.draw(LAYER);
    }

    private static void quad(VertexConsumer consumer, Matrix4f mat,
                             Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4, int argb) {
        consumer.vertex(mat, p1.x, p1.y, p1.z).color(argb);
        consumer.vertex(mat, p2.x, p2.y, p2.z).color(argb);
        consumer.vertex(mat, p3.x, p3.y, p3.z).color(argb);
        consumer.vertex(mat, p4.x, p4.y, p4.z).color(argb);
    }

    private static void stroke(VertexConsumerProvider.Immediate immediate, Matrix4f mat,
                               List<Vector3f> points, List<Stage> placed, Aabb venue, Vector3f[] preview) {
        float pad = 0.01f;
        VertexConsumer consumer = immediate.getBuffer(LINES);
        for (Stage stage : placed) {
            loop(consumer, mat, stage.c1, stage.c2, stage.c3, stage.c4, EXIST_EDGE);
        }
        int marker = 0xFFE2B15A;
        for (Vector3f pick : points) {
            boxEdges(consumer, mat, pick.x - pad, pick.y - pad, pick.z - pad,
                    pick.x + 1f + pad, pick.y + 1f + pad, pick.z + 1f + pad, marker);
        }
        if (venue != null) {
            boxEdges(consumer, mat, venue.x1 - pad, venue.y1 - pad, venue.z1 - pad,
                    venue.x2 + pad, venue.y2 + pad, venue.z2 + pad, 0xFF55FF55);
        }
        if (preview != null) {
            loop(consumer, mat, preview[0], preview[1], preview[2], preview[3], 0xFFFFD700);
        }
        immediate.draw(LINES);
    }

    private static void loop(VertexConsumer consumer, Matrix4f mat,
                             Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4, int argb) {
        line(consumer, mat, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, argb);
        line(consumer, mat, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z, argb);
        line(consumer, mat, p3.x, p3.y, p3.z, p4.x, p4.y, p4.z, argb);
        line(consumer, mat, p4.x, p4.y, p4.z, p1.x, p1.y, p1.z, argb);
    }

    private static List<Stage> existingScreens() {
        List<Stage> found = new ArrayList<>();
        for (Venue venue : Theater.venues.values()) {
            if (!venue.loaded) {
                continue;
            }
            for (Stage stage : venue.stages()) {
                if (stage.isClone() || !hasCorners(stage)) {
                    continue;
                }
                found.add(stage);
            }
        }
        return found;
    }

    private static boolean hasCorners(Stage stage) {
        return stage.c1 != null && stage.c2 != null && stage.c3 != null && stage.c4 != null;
    }

    private static Vector3f[] insetCorners(Vector3f a, Vector3f b, Vector3f cc, Vector3f d) {
        Vector3f[] corners = {
                new Vector3f(a), new Vector3f(b), new Vector3f(cc), new Vector3f(d)
        };
        Vector3f facing = normal(corners[0], corners[1], corners[3]);
        for (Vector3f corner : corners) {
            nudge(corner, facing);
        }
        int[] prev = {3, 0, 1, 2};
        int[] next = {1, 2, 3, 0};
        Vector3f[] pull = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            Vector3f here = corners[i];
            Vector3f towardNext = new Vector3f(corners[next[i]]).sub(here).normalize();
            Vector3f towardPrev = new Vector3f(corners[prev[i]]).sub(here).normalize();
            pull[i] = towardNext.add(towardPrev).mul(-0.5f);
        }
        for (int i = 0; i < 4; i++) {
            corners[i].add(pull[i]);
        }
        return corners;
    }

    private static void nudge(Vector3f v, Vector3f o) {
        v.add(0.5f + o.x * -0.55f, 0.5f + o.y * -0.55f, 0.5f + o.z * -0.55f);
    }

    private static Vector3f normal(Vector3f p0, Vector3f p1, Vector3f p3) {
        Vector3f along = new Vector3f(p1).sub(p0);
        Vector3f across = new Vector3f(p3).sub(p0);
        Vector3f facing = new Vector3f();
        across.cross(along, facing).normalize();
        return facing;
    }

    private static void boxEdges(VertexConsumer c, Matrix4f mat, float x1, float y1, float z1,
                                 float x2, float y2, float z2, int col) {
        float[][] seg = {
                {x1, y1, z1, x2, y1, z1},
                {x2, y1, z1, x2, y1, z2},
                {x2, y1, z2, x1, y1, z2},
                {x1, y1, z2, x1, y1, z1},
                {x1, y2, z1, x2, y2, z1},
                {x2, y2, z1, x2, y2, z2},
                {x2, y2, z2, x1, y2, z2},
                {x1, y2, z2, x1, y2, z1},
                {x1, y1, z1, x1, y2, z1},
                {x2, y1, z1, x2, y2, z1},
                {x2, y1, z2, x2, y2, z2},
                {x1, y1, z2, x1, y2, z2}
        };
        for (float[] edge : seg) {
            line(c, mat, edge[0], edge[1], edge[2], edge[3], edge[4], edge[5], col);
        }
    }

    private static void line(VertexConsumer c, Matrix4f mat,
                             float x1, float y1, float z1, float x2, float y2, float z2, int col) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float span = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (span < 1e-6f) {
            dx = 0f;
            dy = 1f;
            dz = 0f;
        } else {
            dx /= span;
            dy /= span;
            dz /= span;
        }
        c.vertex(mat, x1, y1, z1).color(col).normal(dx, dy, dz);
        c.vertex(mat, x2, y2, z2).color(col).normal(dx, dy, dz);
    }
}
