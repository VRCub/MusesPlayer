package com.puddingkc.muses.media;

import com.mojang.blaze3d.systems.RenderSystem;
import com.puddingkc.muses.config.Settings;
import com.puddingkc.muses.render.WorldDraw;
import com.puddingkc.muses.vr.Vr;
import com.puddingkc.muses.world.Stage;
import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

public class SphereProjector extends FlatProjector {
    private static final Quaternionf tmpQ = new Quaternionf();

    private float[] vertices;
    private int vertexCount;
    private VertexBuffer buffer;
    private boolean dirty = true;
    private int lastGray;

    private float cx, cy, cz;
    private int rot;
    private boolean skybox;

    public SphereProjector(Stage stage) {
        super(stage);
    }

    @Override
    public synchronized void init() {
        super.init();
        buffer = new VertexBuffer(GlUsage.DYNAMIC_WRITE);
        rebuild();
    }

    @Override
    public void onFlagsChanged() {
        rebuild();
    }

    private void rebuild() {
        cx = stage.c1.x + stage.flagFloat("x", 0);
        cy = stage.c1.y + stage.flagFloat("y", 0);
        cz = stage.c1.z + stage.flagFloat("z", 0);
        int radius = stage.flag("radius", 10);
        int lat = Math.clamp(stage.flag("lat", 32), 2, 128);
        int lon = Math.clamp(stage.flag("lon", 32), 2, 256);
        float[] mesh = genVertices(radius, lat, lon, stage.su, stage.eu, stage.sv, stage.ev);
        vertices = mesh;
        vertexCount = mesh.length / 5;
        skybox = stage.flag("skybox", 0) != 0;
        rot = stage.flag("rot", 0);
        dirty = true;
    }

    @Override
    public void draw(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Stage s) {
        if (vertices == null || buffer == null) {
            return;
        }
        int texId = textureId();
        if (texId < 0) {
            return;
        }

        matrices.push();
        WorldDraw.rotate(matrices);
        if (skybox) {
            WorldDraw.skybox = true;
        } else {
            matrices.translate(cx - WorldDraw.cameraX, cy - WorldDraw.cameraY, cz - WorldDraw.cameraZ);
        }
        if (rot != 0) {
            matrices.multiply(tmpQ.rotationY((float) Math.toRadians(rot)));
        }

        int level = (int) (Settings.INSTANCE.brightness / 100.0 * 255);
        if (lastGray != level) {
            dirty = true;
            lastGray = level;
        }
        int color = 0xFF000000 | (level * 0x010101);

        buffer.bind();
        if (dirty) {
            buffer.upload(sphereMesh(color).end());
            dirty = false;
        }
        if (Vr.loaded && Vr.active() && Vr.rightEye()) {
            matrices.multiply(tmpQ.rotationY(MathHelper.PI));
        }
        WorldDraw.drawTriangles(texId, () -> buffer.draw(
                matrices.peek().getPositionMatrix(),
                RenderSystem.getProjectionMatrix(),
                RenderSystem.getShader()));
        matrices.pop();
    }

    private BufferBuilder sphereMesh(int color) {
        BufferBuilder builder = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_TEXTURE_COLOR);
        for (int i = 0; i < vertexCount; i++) {
            int idx = i * 5;
            builder.vertex(vertices[idx], vertices[idx + 1], vertices[idx + 2])
                    .texture(vertices[idx + 3], vertices[idx + 4]).color(color);
        }
        return builder;
    }

    @Override
    public synchronized void cleanup() {
        super.cleanup();
        if (buffer != null) {
            buffer.close();
        }
        buffer = null;
    }

    static float[] genVertices(float radius, int latSegments, int lonSegments,
                               float us, float ue, float vs, float ve) {
        int cols = lonSegments + 1;
        float[] data = new float[latSegments * cols * 10];
        Ring[] rings = rings(radius, latSegments, vs, ve);
        int cursor = 0;
        for (int band = 0; band < latSegments; band++) {
            cursor = stitch(data, cursor, rings[band], rings[band + 1], lonSegments, us, ue);
        }
        return data;
    }

    private static Ring[] rings(float radius, int latSegments, float vs, float ve) {
        Ring[] rings = new Ring[latSegments + 1];
        for (int i = 0; i < rings.length; i++) {
            double polar = Math.PI * i / latSegments;
            rings[i] = new Ring(
                    (float) (radius * Math.cos(polar)),
                    (float) (radius * Math.sin(polar)),
                    MathHelper.lerp(i / (float) latSegments, vs, ve));
        }
        return rings;
    }

    private static int stitch(float[] data, int cursor, Ring north, Ring south, int lonSegments, float us, float ue) {
        for (int slice = 0; slice <= lonSegments; slice++) {
            double turns = slice / (double) lonSegments;
            double phi = turns * Math.PI * 2.0;
            float cos = (float) Math.cos(phi);
            float sin = (float) Math.sin(phi);
            float u = MathHelper.lerp((float) turns, us, ue);
            cursor = put(data, cursor, north.ring * cos, north.y, north.ring * sin, u, north.v);
            cursor = put(data, cursor, south.ring * cos, south.y, south.ring * sin, u, south.v);
        }
        return cursor;
    }

    private static final class Ring {
        final float y;
        final float ring;
        final float v;

        Ring(float y, float ring, float v) {
            this.y = y;
            this.ring = ring;
            this.v = v;
        }
    }

    private static int put(float[] data, int idx, float x, float y, float z, float u, float v) {
        data[idx] = x;
        data[idx + 1] = y;
        data[idx + 2] = z;
        data[idx + 3] = u;
        data[idx + 4] = v;
        return idx + 5;
    }
}
