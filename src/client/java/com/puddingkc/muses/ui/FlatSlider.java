package com.puddingkc.muses.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public abstract class FlatSlider extends SliderWidget {
    protected FlatSlider(int x, int y, int w, int h, Text message, double value) {
        super(x, y, w, h, message, value);
    }

    protected void onCommit() {
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        onCommit();
        super.onRelease(mouseX, mouseY);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = getX();
        int top = getY();
        int span = getWidth();
        int tall = getHeight();
        if (!active) {
            paintDisabled(context, left, top, span, tall);
            return;
        }
        paintTrack(context, left, top, span, tall, isHovered());
        paintCaption(context, left, top, span, tall, true);
    }

    private void paintDisabled(DrawContext context, int left, int top, int span, int tall) {
        context.fill(left, top, left + span, top + tall, Style.CTRL_BG_DISABLED);
        context.drawBorder(left, top, span, tall, Style.CTRL_BORDER);
        paintCaption(context, left, top, span, tall, false);
    }

    private void paintTrack(DrawContext context, int left, int top, int span, int tall, boolean hovered) {
        context.fill(left, top, left + span, top + tall, Style.CTRL_TRACK);
        int filled = (int) Math.round(value * span);
        if (filled > 0) context.fill(left, top, left + filled, top + tall, Style.CTRL_FILL);
        int edge = hovered ? Style.CTRL_BORDER_HOVER : Style.CTRL_BORDER;
        context.drawBorder(left, top, span, tall, edge);
        int knob = knobX(left, span);
        context.fill(knob - 2, top, knob + 2, top + tall, Style.CTRL_KNOB);
    }

    private int knobX(int left, int span) {
        int along = (int) Math.round(value * span);
        return left + Math.clamp(along, 2, Math.max(2, span - 2));
    }

    private void paintCaption(DrawContext context, int left, int top, int span, int tall, boolean shadow) {
        MinecraftClient client = MinecraftClient.getInstance();
        Text caption = getMessage();
        int tw = client.textRenderer.getWidth(caption);
        int tx = left + (span - tw) / 2;
        int ty = top + (tall - 8) / 2;
        if (!shadow) {
            context.drawText(client.textRenderer, caption.getString(), tx, ty, Style.CTRL_TEXT_DISABLED, false);
            return;
        }
        context.drawTextWithShadow(client.textRenderer, caption, tx, ty, Style.TEXT);
    }
}
