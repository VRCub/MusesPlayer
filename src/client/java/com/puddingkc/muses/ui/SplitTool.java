package com.puddingkc.muses.ui;

import com.puddingkc.muses.media.Projector;
import com.puddingkc.muses.net.Outbound;
import com.puddingkc.muses.world.Stage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class SplitTool extends Screen {
    private final Screen parent;
    private final Stage stage;

    private static final int PAD = 10;
    private static final int EDGE = 6;
    private static final float MIN_UV = 0.01f;
    private static final int MODE_NEW = 1, MODE_MOVE = 2, MODE_RESIZE = 3;

    private int panelX, panelY, panelW, panelH;
    private int previewX, previewY, previewW, previewH;
    private int hintY;

    private float su1, sv1, su2, sv2;

    private int dragMode = 0;
    private boolean rL, rR, rT, rB;
    private double pressX, pressY;
    private float osu1, osv1, osu2, osv2;
    private float wu1, wv1, wu2, wv2;

    public SplitTool(Screen parent, Stage stage) {
        super(Text.literal("分割屏幕"));
        this.parent = parent;
        this.stage = stage;
        this.su1 = stage.su;
        this.sv1 = stage.sv;
        this.su2 = stage.eu;
        this.sv2 = stage.ev;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private boolean hasVideo() {
        Projector frame = stage.sourceStage().projector();
        return frame != null && frame.textureId() >= 0 && frame.width() > 0 && frame.height() > 0;
    }

    @Override
    protected void init() {
        panelW = Math.min(480, width - 20);
        panelH = Math.min(300, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        layoutPreview();
    }

    private void layoutPreview() {
        int innerX = panelX + PAD;
        int innerW = panelW - PAD * 2;
        int top = panelY + PAD + 13 + 12;
        int btnY = panelY + panelH - PAD - 20;
        hintY = btnY - 4 - 9;
        int bottom = hintY - 4;
        int availH = bottom - top;
        previewW = Math.min(innerW, availH * 16 / 9);
        previewH = Math.min(availH, innerW * 9 / 16);
        previewX = innerX + (innerW - previewW) / 2;
        previewY = top + (availH - previewH) / 2;
        addDrawableChild(Style.button(Text.literal("重置全屏"), innerX, btnY, 90, 20, b -> resetFull()));
        addDrawableChild(Style.button(Text.literal("关闭"), panelX + panelW - PAD - 70, btnY, 70, 20, b -> close()));
    }

    private void resetFull() {
        su1 = 0;
        sv1 = 0;
        su2 = 1;
        sv2 = 1;
        Outbound.uv(stage, 0, 0, 1, 1);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        Style.panel(context, panelX, panelY, panelW, panelH);
        context.fill(previewX, previewY, previewX + previewW, previewY + previewH, Style.PREVIEW_BG);
        context.drawBorder(previewX - 1, previewY - 1, previewW + 2, previewH + 2, Style.PREVIEW_BORDER);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(textRenderer,
                Text.literal("分割屏幕 - " + stage.label),
                panelX + PAD, panelY + PAD, Style.TITLE);
        float[] uv = currentUV();
        String uvText = String.format("UV: %.3f, %.3f - %.3f, %.3f", uv[0], uv[1], uv[2], uv[3]);
        context.drawTextWithShadow(textRenderer, Text.literal(uvText), previewX, panelY + PAD + 13, Style.TEXT_DIM);
        boolean playing = paintPreview(context);
        int[] box = selectionPixels(uv);
        paintMask(context, box[0], box[1], box[2], box[3]);
        int border = dragMode != 0 ? Style.SEL_BORDER_ACTIVE : Style.SEL_BORDER;
        context.drawBorder(box[0], box[1], Math.max(1, box[2] - box[0]), Math.max(1, box[3] - box[1]), border);
        if (playing) paintHandles(context, box[0], box[1], box[2], box[3]);
        if (playing && dragMode == 0) paintHint(context);
    }

    private boolean paintPreview(DrawContext context) {
        if (!hasVideo()) {
            Text hint = Text.literal("无画面");
            context.drawTextWithShadow(textRenderer, hint,
                    previewX + (previewW - textRenderer.getWidth(hint)) / 2,
                    previewY + previewH / 2 - 4, Style.TEXT_FAINT);
            return false;
        }
        Projector frame = stage.sourceStage().projector();
        context.draw();
        Gui.texture(context, frame.textureId(), previewX, previewY, previewW, previewH, false, false);
        return true;
    }

    private int[] selectionPixels(float[] uv) {
        return new int[]{
                previewX + Math.round(uv[0] * previewW),
                previewY + Math.round(uv[1] * previewH),
                previewX + Math.round(uv[2] * previewW),
                previewY + Math.round(uv[3] * previewH)
        };
    }

    private void paintMask(DrawContext context, int sx1, int sy1, int sx2, int sy2) {
        int mask = Style.MASK;
        int right = previewX + previewW;
        int bottom = previewY + previewH;
        int[][] bands = {
                {previewX, previewY, right, sy1},
                {previewX, sy2, right, bottom},
                {previewX, sy1, sx1, sy2},
                {sx2, sy1, right, sy2}
        };
        for (int[] band : bands) {
            context.fill(band[0], band[1], band[2], band[3], mask);
        }
    }

    private void paintHandles(DrawContext context, int sx1, int sy1, int sx2, int sy2) {
        int midX = (sx1 + sx2) / 2;
        int midY = (sy1 + sy2) / 2;
        int[] xs = {sx1, midX, sx2, sx1, sx2, sx1, midX, sx2};
        int[] ys = {sy1, sy1, sy1, midY, midY, sy2, sy2, sy2};
        for (int i = 0; i < xs.length; i++) stampHandle(context, xs[i], ys[i]);
    }

    private void stampHandle(DrawContext context, int px, int py) {
        context.fill(px - 2, py - 2, px + 2, py + 2, Style.HANDLE);
    }

    private void paintHint(DrawContext context) {
        String hint = textRenderer.trimToWidth("拖动边角缩放，拖动中间移动，拖动空白处新建", panelW - PAD * 2);
        context.drawTextWithShadow(textRenderer, Text.literal(hint), panelX + PAD, hintY, Style.TEXT_DIM);
    }

    private float[] currentUV() {
        if (dragMode == 0) return new float[]{su1, sv1, su2, sv2};
        return new float[]{wu1, wv1, wu2, wv2};
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private boolean inPreview(double x, double y) {
        return x >= previewX && x <= previewX + previewW && y >= previewY && y <= previewY + previewH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || !hasVideo() || !inPreview(mouseX, mouseY)) return false;
        beginDrag(mouseX, mouseY);
        return true;
    }

    private void beginDrag(double mouseX, double mouseY) {
        pressX = mouseX;
        pressY = mouseY;
        osu1 = su1;
        osv1 = sv1;
        osu2 = su2;
        osv2 = sv2;
        int sx1 = previewX + Math.round(su1 * previewW);
        int sy1 = previewY + Math.round(sv1 * previewH);
        int sx2 = previewX + Math.round(su2 * previewW);
        int sy2 = previewY + Math.round(sv2 * previewH);
        boolean nearL = Math.abs(mouseX - sx1) <= EDGE;
        boolean nearR = Math.abs(mouseX - sx2) <= EDGE;
        boolean nearT = Math.abs(mouseY - sy1) <= EDGE;
        boolean nearB = Math.abs(mouseY - sy2) <= EDGE;
        boolean inX = mouseX >= sx1 - EDGE && mouseX <= sx2 + EDGE;
        boolean inY = mouseY >= sy1 - EDGE && mouseY <= sy2 + EDGE;
        rL = nearL && inY;
        rR = nearR && inY;
        rT = nearT && inX;
        rB = nearB && inX;
        if (rL || rR || rT || rB) {
            dragMode = MODE_RESIZE;
        } else if (mouseX > sx1 && mouseX < sx2 && mouseY > sy1 && mouseY < sy2) {
            dragMode = MODE_MOVE;
        } else {
            dragMode = MODE_NEW;
            float u = clamp01((float) (mouseX - previewX) / previewW);
            float v = clamp01((float) (mouseY - previewY) / previewH);
            osu1 = u;
            osv1 = v;
            osu2 = u;
            osv2 = v;
        }
        wu1 = osu1;
        wv1 = osv1;
        wu2 = osu2;
        wv2 = osv2;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragMode == 0) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        float du = (float) ((mouseX - pressX) / previewW);
        float dv = (float) ((mouseY - pressY) / previewH);
        if (dragMode == MODE_NEW) dragNew(mouseX, mouseY);
        else if (dragMode == MODE_MOVE) dragMove(du, dv);
        else if (dragMode == MODE_RESIZE) dragResize(du, dv);
        return true;
    }

    private void dragNew(double mouseX, double mouseY) {
        float u = clamp01((float) (mouseX - previewX) / previewW);
        float v = clamp01((float) (mouseY - previewY) / previewH);
        wu1 = Math.min(osu1, u);
        wv1 = Math.min(osv1, v);
        wu2 = Math.max(osu1, u);
        wv2 = Math.max(osv1, v);
    }

    private void dragMove(float du, float dv) {
        float w = osu2 - osu1;
        float h = osv2 - osv1;
        float nu1 = clamp01(osu1 + du);
        float nv1 = clamp01(osv1 + dv);
        nu1 = Math.min(nu1, 1 - w);
        nv1 = Math.min(nv1, 1 - h);
        wu1 = nu1;
        wv1 = nv1;
        wu2 = nu1 + w;
        wv2 = nv1 + h;
    }

    private void dragResize(float du, float dv) {
        wu1 = osu1;
        wv1 = osv1;
        wu2 = osu2;
        wv2 = osv2;
        if (rL) wu1 = clamp01(osu1 + du);
        if (rR) wu2 = clamp01(osu2 + du);
        if (rT) wv1 = clamp01(osv1 + dv);
        if (rB) wv2 = clamp01(osv2 + dv);
        if (wu2 - wu1 < MIN_UV) {
            if (rL) wu1 = wu2 - MIN_UV;
            else wu2 = wu1 + MIN_UV;
        }
        if (wv2 - wv1 < MIN_UV) {
            if (rT) wv1 = wv2 - MIN_UV;
            else wv2 = wv1 + MIN_UV;
        }
        wu1 = clamp01(wu1);
        wu2 = clamp01(wu2);
        wv1 = clamp01(wv1);
        wv2 = clamp01(wv2);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragMode == 0 || button != 0) return super.mouseReleased(mouseX, mouseY, button);
        int mode = dragMode;
        dragMode = 0;
        float u1 = Math.min(wu1, wu2);
        float v1 = Math.min(wv1, wv2);
        float u2 = Math.max(wu1, wu2);
        float v2 = Math.max(wv1, wv2);
        if (mode == MODE_NEW && (u2 - u1 < MIN_UV || v2 - v1 < MIN_UV)) return true;
        su1 = u1;
        sv1 = v1;
        su2 = u2;
        sv2 = v2;
        Outbound.uv(stage, su1, sv1, su2, sv2);
        return true;
    }

    @Override
    public void close() {
        if (client == null) return;
        client.setScreen(parent);
    }
}
