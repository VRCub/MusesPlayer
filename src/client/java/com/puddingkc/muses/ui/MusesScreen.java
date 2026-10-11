package com.puddingkc.muses.ui;

import com.puddingkc.muses.net.Outbound;
import com.puddingkc.muses.world.Stage;
import com.puddingkc.muses.world.Theater;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;

public abstract class MusesScreen extends Screen {
    protected static final int PANEL_BG = Style.PANEL_BG;
    protected static final int PANEL_BORDER = Style.PANEL_BORDER;
    protected static final int INSET_BG = Style.INSET_BG;

    protected final Screen parent;
    private final String heading;

    private final Set<String> permRequested = new HashSet<>();

    protected int panelX, panelY, panelW, panelH;
    protected int bodyX, bodyY, bodyW, bodyH;

    protected MusesScreen(Screen parent, String heading) {
        super(Text.literal(heading));
        this.parent = parent;
        this.heading = heading;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    protected int panelWidth() {
        return 300;
    }

    protected int panelHeight() {
        return 220;
    }

    @Override
    protected void init() {
        layoutPanel();
        int pad = 10;
        addDrawableChild(Style.button(Text.literal("返回上级"), panelX + pad, panelY + panelH - pad - 18, panelW - pad * 2, 18, b -> close()));
        initBody();
    }

    protected abstract void initBody();

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        Style.panel(context, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawHeading(context);
        renderBody(context, mouseX, mouseY, delta);
    }

    protected void renderBody(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    protected void queryPermLoc(String node) {
        if (!permRequested.add(node)) return;
        Outbound.perm("", "", node);
    }

    protected void queryPermStage(Stage stage, String node) {
        if (stage == null) return;
        if (!permRequested.add(stage.label + "|" + node)) return;
        Outbound.perm(stage, node);
    }

    protected void refreshPermLoc(String node) {
        permRequested.remove(node);
        Theater.perms.remove(node);
    }

    protected void refreshPermStage(Stage stage, String node) {
        if (stage == null) return;
        permRequested.remove(stage.label + "|" + node);
        stage.perms.remove(node);
    }

    protected boolean deniedLoc(String node) {
        return Boolean.FALSE.equals(Theater.perms.get(node));
    }

    protected boolean deniedStage(Stage stage, String node) {
        return stage != null && Boolean.FALSE.equals(stage.perms.get(node));
    }

    public void onPermUpdate(String node) {
        clearAndInit();
    }

    @Override
    public void close() {
        if (client == null) return;
        client.setScreen(parent);
    }

    private void layoutPanel() {
        int pad = 10;
        panelW = Math.min(panelWidth(), width - 20);
        panelH = Math.min(panelHeight(), height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        bodyX = panelX + pad;
        bodyY = panelY + 26;
        bodyW = panelW - pad * 2;
        bodyH = panelY + panelH - pad - 22 - bodyY;
    }

    private void drawHeading(DrawContext context) {
        context.drawTextWithShadow(textRenderer, Text.literal(heading), panelX + 10, panelY + 8, Style.TITLE);
    }
}
