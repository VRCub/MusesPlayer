package com.puddingkc.muses.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.puddingkc.muses.config.Settings;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

public final class Gui {
    private Gui() {
    }

    public static void texture(DrawContext context, int tex, int x, int y, int w, int h, boolean fx, boolean fy) {
        float u0 = fx ? 1f : 0f;
        float u1 = fx ? 0f : 1f;
        float v0 = fy ? 1f : 0f;
        float v1 = fy ? 0f : 1f;
        Matrix4f mat = context.getMatrices().peek().getPositionMatrix();
        bind(tex);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        emit(buf, mat, x, y, w, h, u0, u1, v0, v1, tint());
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    public static int[] fit(int boxX, int boxY, int boxW, int boxH, float aspect) {
        float ratio = aspect <= 0 ? 16f / 9f : aspect;
        int dw = boxW;
        int dh = (int) (dw / ratio);
        if (dh > boxH) {
            dh = boxH;
            dw = (int) (dh * ratio);
        }
        int ox = boxX + (boxW - dw) / 2;
        int oy = boxY + (boxH - dh) / 2;
        return new int[]{ox, oy, dw, dh};
    }

    private static void bind(int tex) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, tex);
    }

    private static int tint() {
        int gray = (int) (Settings.INSTANCE.brightness / 100.0 * 255);
        return pack(255, gray, gray, gray);
    }

    private static int pack(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void emit(BufferBuilder buf, Matrix4f mat, int x, int y, int w, int h,
                             float u0, float u1, float v0, float v1, int color) {
        float[] xs = {x, x, x + w, x + w};
        float[] ys = {y, y + h, y + h, y};
        float[] us = {u0, u0, u1, u1};
        float[] vs = {v0, v1, v1, v0};
        for (int i = 0; i < 4; i++) {
            buf.vertex(mat, xs[i], ys[i], 0).texture(us[i], vs[i]).color(color);
        }
    }
}
