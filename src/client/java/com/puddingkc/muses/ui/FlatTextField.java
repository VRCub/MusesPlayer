package com.puddingkc.muses.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class FlatTextField extends TextFieldWidget {
    private static final int PAD_X = 3;
    private static final int EXTRA_TRIM = 6;

    public FlatTextField(TextRenderer textRenderer, int x, int y, int w, int h, Text placeholder) {
        super(textRenderer, x, y, w, h, placeholder);
        setDrawsBackground(false);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;
        Style.flatRect(context, getX(), getY(), getWidth(), getHeight(), isFocused(), true);
        float lift = (getHeight() - 8) / 2f;
        context.getMatrices().push();
        context.getMatrices().translate(PAD_X, lift, 0f);
        super.renderWidget(context, mouseX, mouseY, delta);
        context.getMatrices().pop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX - PAD_X, mouseY, button);
    }

    @Override
    public int getInnerWidth() {
        return super.getInnerWidth() - insetTrim();
    }

    private static int insetTrim() {
        return PAD_X * 2 + EXTRA_TRIM;
    }
}
