package com.puddingkc.muses.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ScrollColumn extends ClickableWidget {
    private record Item(ClickableWidget widget, int relX, int relY) {
    }

    private final List<Item> items = new ArrayList<>();
    private int contentH = 0;
    private double scrollY = 0;
    @Nullable
    private Element focused;

    public ScrollColumn(int x, int y, int width, int height) {
        super(x, y, width, height, Text.empty());
    }

    public void clearItems() {
        setFocusedChild(null);
        items.clear();
        contentH = 0;
    }

    public void add(ClickableWidget widget, int relX, int relY) {
        items.add(new Item(widget, relX, relY));
        contentH = Math.max(contentH, relY + widget.getHeight());
    }

    public void add(ClickableWidget widget, int relY) {
        add(widget, 0, relY);
    }

    public int contentHeight() {
        return contentH;
    }

    private int maxScroll() {
        return Math.max(0, contentH - getHeight());
    }

    private void clampScroll() {
        scrollY = MathHelper.clamp(scrollY, 0, maxScroll());
    }

    private void syncPositions() {
        clampScroll();
        int top = getY();
        for (Item it : items) place(it, top);
    }

    private void place(Item it, int top) {
        it.widget().setX(getX() + it.relX());
        it.widget().setY((int) Math.round(top - scrollY + it.relY()));
    }

    private boolean inViewport(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + getHeight();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        syncPositions();
        int left = getX();
        int top = getY();
        int right = left + getWidth();
        int bottom = top + getHeight();
        context.enableScissor(left, top, right, bottom);
        boolean inside = inViewport(mouseX, mouseY);
        paintVisible(context, inside ? mouseX : -1, inside ? mouseY : -1, delta, top, bottom);
        context.disableScissor();
        renderScrollbar(context);
    }

    private void paintVisible(DrawContext context, int mx, int my, float delta, int top, int bottom) {
        for (Item it : items) {
            ClickableWidget child = it.widget();
            if (!overlaps(child, top, bottom)) continue;
            child.render(context, mx, my, delta);
        }
    }

    private boolean overlaps(ClickableWidget child, int top, int bottom) {
        int wy = child.getY();
        return wy + child.getHeight() >= top && wy <= bottom;
    }

    private void renderScrollbar(DrawContext context) {
        int limit = maxScroll();
        if (limit <= 0) return;
        int trackX = getX() + getWidth() - 3;
        int trackH = getHeight();
        int thumbH = Math.max(16, (int) ((long) trackH * getHeight() / contentH));
        int thumbY = getY() + (int) ((trackH - thumbH) * (scrollY / limit));
        context.fill(trackX, getY(), trackX + 3, getY() + trackH, Style.CTRL_TRACK);
        context.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, Style.CTRL_KNOB);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!inViewport(mouseX, mouseY)) return false;
        int limit = maxScroll();
        if (limit <= 0) return false;
        scrollY = MathHelper.clamp(scrollY - vertical * 12, 0, limit);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!inViewport(mouseX, mouseY)) {
            setFocusedChild(null);
            return false;
        }
        syncPositions();
        ClickableWidget hit = firstHit(mouseX, mouseY, button);
        setFocusedChild(hit);
        return hit != null;
    }

    private ClickableWidget firstHit(double mouseX, double mouseY, int button) {
        for (Item it : items) {
            ClickableWidget child = it.widget();
            if (child.mouseClicked(mouseX, mouseY, button)) return child;
        }
        return null;
    }

    private void setFocusedChild(@Nullable Element w) {
        if (focused == w) return;
        if (focused != null) focused.setFocused(false);
        focused = w;
        if (focused != null) focused.setFocused(true);
    }

    @Override
    public void setFocused(boolean on) {
        super.setFocused(on);
        if (on) return;
        setFocusedChild(null);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        for (Item it : items) {
            if (it.widget().mouseReleased(mouseX, mouseY, button)) handled = true;
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (focused == null) return false;
        return focused.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return focused != null && focused.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return focused != null && focused.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return focused != null && focused.charTyped(chr, modifiers);
    }

    @Nullable
    @Override
    public GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        return null;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
    }
}
