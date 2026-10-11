package com.puddingkc.muses.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class FlatButton extends ButtonWidget {
    protected FlatButton(Text message, int x, int y, int w, int h, PressAction onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        Style.flatRect(context, getX(), getY(), getWidth(), getHeight(), isHovered(), active);
        paintCaption(context);
    }

    private void paintCaption(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        Text caption = getMessage();
        int tw = client.textRenderer.getWidth(caption);
        int tx = getX() + (getWidth() - tw) / 2;
        int ty = getY() + (getHeight() - 8) / 2;
        if (!active) {
            context.drawText(client.textRenderer, caption.getString(), tx, ty, Style.CTRL_TEXT_DISABLED, false);
            return;
        }
        context.drawTextWithShadow(client.textRenderer, caption, tx, ty, Style.TEXT);
    }
}
