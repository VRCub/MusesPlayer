package com.puddingkc.muses.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.puddingkc.muses.MusesPlayer;
import com.puddingkc.muses.danmaku.DanmakuDraw;
import com.puddingkc.muses.input.DragController;
import com.puddingkc.muses.input.Interaction;
import com.puddingkc.muses.vr.Vr;
import com.puddingkc.muses.world.Stage;
import com.puddingkc.muses.world.Theater;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

public final class WorldDraw {
    private WorldDraw() {
    }

    private static final Map<Integer, RenderLayer> quadCache = new HashMap<>();

    private static int triangleId;
    private static final RenderLayer TRIANGLES = RenderLayer.of(
            "muses_triangles",
            VertexFormats.POSITION_TEXTURE_COLOR,
            VertexFormat.DrawMode.TRIANGLE_STRIP,
            8192,
            true,
            true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(new RenderPhase.ShaderProgram(ShaderProgramKeys.POSITION_TEX_COLOR))
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .texture(new RenderPhase.TextureBase(() -> RenderSystem.setShaderTexture(0, triangleId), () -> {}))
                    .cull(RenderPhase.DISABLE_CULLING)
                    .build(true)
    );

    private static final Quaternionf rotation = new Quaternionf();
    public static float cameraX, cameraY, cameraZ;
    public static boolean skybox;

    public static void render(WorldRenderContext ctx) {
        if (!Theater.authenticated) {
            return;
        }
        skybox = false;
        MatrixStack matrices = ctx.matrixStack();
        matrices.push();
        captureView(ctx);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LESS);
        RenderSystem.disableCull();
        VertexConsumerProvider.Immediate immediate = (VertexConsumerProvider.Immediate) ctx.consumers();
        quadCache.clear();
        int bound = RenderSystem.getShaderTexture(0);
        paintStages(matrices, immediate);
        RenderSystem.setShaderTexture(0, bound);
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
        matrices.pop();
    }

    private static void captureView(WorldRenderContext ctx) {
        Vec3d pos = ctx.camera().getPos();
        cameraX = (float) pos.x;
        cameraY = (float) pos.y;
        cameraZ = (float) pos.z;
        if (Vr.loaded && Vr.active()) {
            rotation.setFromNormalized(Vr.rotation()).invert();
        } else {
            ctx.camera().getRotation().invert(rotation);
        }
    }

    private static void paintStages(MatrixStack matrices, VertexConsumerProvider.Immediate immediate) {
        for (Stage stage : Theater.screens) {
            try {
                stage.draw(matrices, immediate);
            } catch (Exception ex) {
                MusesPlayer.LOGGER.error("渲染屏幕时出错", ex);
            }
        }
    }

    public static void renderBars(WorldRenderContext ctx) {
        if (!Theater.authenticated) {
            return;
        }
        walkIdle(ctx, "渲染进度条时出错", (matrices, immediate, stage) ->
                SeekBar.render(matrices, immediate, stage, stage == Interaction.barTarget));
    }

    public static void renderDanmaku(WorldRenderContext ctx) {
        if (!Theater.authenticated) {
            return;
        }
        walkIdle(ctx, "渲染弹幕时出错", DanmakuDraw::render);
    }

    private static void walkIdle(WorldRenderContext ctx, String fault, Overlay pass) {
        MatrixStack matrices = ctx.matrixStack();
        VertexConsumerProvider.Immediate immediate = (VertexConsumerProvider.Immediate) ctx.consumers();
        for (Stage stage : Theater.screens) {
            if (!stage.interactable() || DragController.isMoving(stage)) {
                continue;
            }
            try {
                pass.draw(matrices, immediate, stage);
            } catch (Exception ex) {
                MusesPlayer.LOGGER.error(fault, ex);
            }
        }
    }

    @FunctionalInterface
    private interface Overlay {
        void draw(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Stage stage);
    }

    public static RenderLayer quadLayer(int textureId) {
        return quadCache.computeIfAbsent(textureId, WorldDraw::quadFor);
    }

    private static RenderLayer quadFor(int id) {
        return RenderLayer.of(
                "muses_quad_" + id,
                VertexFormats.POSITION_TEXTURE_COLOR,
                VertexFormat.DrawMode.QUADS,
                32,
                true,
                true,
                RenderLayer.MultiPhaseParameters.builder()
                        .program(new RenderPhase.ShaderProgram(ShaderProgramKeys.POSITION_TEX_COLOR))
                        .texture(new RenderPhase.TextureBase(() -> RenderSystem.setShaderTexture(0, id), () -> {}))
                        .cull(RenderPhase.DISABLE_CULLING)
                        .build(true)
        );
    }

    public static void drawTriangles(int textureId, Runnable r) {
        triangleId = textureId;
        TRIANGLES.startDrawing();
        r.run();
        TRIANGLES.endDrawing();
    }

    public static void rotate(MatrixStack matrices) {
        matrices.multiply(rotation);
    }
}
