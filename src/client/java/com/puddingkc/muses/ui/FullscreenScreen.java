package com.puddingkc.muses.ui;

import com.puddingkc.muses.media.Projector;
import com.puddingkc.muses.world.Stage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class FullscreenScreen extends Screen {
    private final Screen parent;
    private final Stage stage;

    public FullscreenScreen(Screen parent, Stage stage) {
        super(Text.literal("Muses Fullscreen"));
        this.parent = parent;
        this.stage = stage;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        addDrawableChild(Style.button(Text.literal("取消全屏"), width - 86, height - 24, 80, 18, b -> close()));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, pack(255, 0, 0, 0));
        if (paintFrame(context)) return;
        paintEmpty(context);
    }

    @Override
    public void close() {
        if (client == null) return;
        client.setScreen(parent);
    }

    private boolean paintFrame(DrawContext context) {
        Projector frame = stage == null ? null : stage.projector();
        if (frame == null || frame.textureId() < 0 || frame.width() <= 0 || frame.height() <= 0) return false;
        float aspect = (float) frame.width() / frame.height();
        int[] box = Gui.fit(0, 0, width, height, aspect);
        context.draw();
        Gui.texture(context, frame.textureId(), box[0], box[1], box[2], box[3], frame.flippedX(), frame.flippedY());
        return true;
    }

    private static int pack(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void paintEmpty(DrawContext context) {
        String none = "无画面";
        int tw = textRenderer.getWidth(none);
        context.drawTextWithShadow(textRenderer, Text.literal(none), (width - tw) / 2, height / 2 - 4, Style.TEXT_FAINT);
    }
}
