package com.puddingkc.muses.ui;

import com.puddingkc.muses.config.Settings;
import com.puddingkc.muses.net.Outbound;
import com.puddingkc.muses.world.Stage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class ScreenEditScreen extends MusesScreen {
    private Stage target;
    private boolean confirmDelete = false;
    private FlatTextField bgField;

    private int sidebarX, sidebarY, sidebarW, sidebarH;
    private int colX, colW;
    private ScrollColumn column;

    public ScreenEditScreen(Screen parent, Stage target) {
        super(parent, "屏幕设置");
        this.target = target;
    }

    @Override
    protected int panelWidth() {
        return 380;
    }

    @Override
    protected int panelHeight() {
        return 300;
    }

    private Stage target() {
        return target;
    }

    private void reinit() {
        confirmDelete = false;
        clearChildren();
        init();
    }

    @Override
    protected void initBody() {
        Stage current = target();
        if (current == null) return;
        placeSidebar(current);
        colX = sidebarX + sidebarW + 8;
        colW = panelX + panelW - 10 - colX;
        int viewTop = bodyY;
        int viewH = panelY + panelH - 10 - 22 - viewTop;
        int innerW = colW - 5;
        column = new ScrollColumn(colX, viewTop, colW, viewH);
        queryPermStage(current, "stage.metacustom");
        queryPermStage(current, "stage.slice");
        boolean metaDenied = deniedStage(current, "stage.metacustom");
        boolean sliceDenied = deniedStage(current, "stage.slice");
        int y = 0;
        y = placeKind(current, y, innerW, metaDenied);
        y = placeOverrides(current, y, innerW, metaDenied);
        y = placeIdle(current, y, innerW);
        y = placeSplit(current, y, innerW, sliceDenied);
        placeDelete(current, y, innerW);
        addDrawableChild(column);
    }

    private void placeSidebar(Stage current) {
        sidebarX = bodyX;
        sidebarY = bodyY + 12;
        sidebarW = 110;
        sidebarH = panelY + panelH - 10 - 22 - sidebarY;
        List<Stage> siblings = new ArrayList<>(current.venue.stages());
        int rowH = 17;
        int rows = Math.max(1, sidebarH / rowH);
        int shown = Math.min(siblings.size(), rows);
        for (int i = 0; i < shown; i++) {
            addDrawableChild(sidebarButton(siblings.get(i), current, i, rowH));
        }
    }

    private ButtonWidget sidebarButton(Stage row, Stage current, int index, int rowH) {
        boolean selected = row == current;
        String name = (row.isClone() ? "[克隆] " : "") + row.label;
        String label = textRenderer.trimToWidth(name, sidebarW - 8);
        ButtonWidget btn = Style.button(
                Text.literal(label),
                sidebarX, sidebarY + index * rowH, sidebarW, rowH - 2, b -> {
                    target = row;
                    reinit();
                });
        btn.active = !selected;
        return btn;
    }

    private int placeKind(Stage current, int y, int innerW, boolean metaDenied) {
        if (current.isClone()) return y;
        Stage.Kind kind = current.kind();
        column.add(gate(Style.button(Text.literal("类型: " + kindName(kind)), 0, 0, innerW, 18,
                b -> cycleKind(current, kind)), metaDenied, "stage.metacustom"), y);
        y += 22;
        if (kind == Stage.Kind.CURVED) {
            column.add(gate(new FlagSlider(0, 0, innerW, 18, current, "曲率", "curve", -50, 50, 0), metaDenied, "stage.metacustom"), y);
            y += 20;
            column.add(gate(new FlagSlider(0, 0, innerW, 18, current, "细分数量", "seg", 1, 128, 32), metaDenied, "stage.metacustom"), y);
            return y + 20;
        }
        if (kind != Stage.Kind.SPHERE) return y;
        column.add(gate(new FlagSlider(0, 0, innerW, 18, current, "球半径", "radius", 1, 64, 10), metaDenied, "stage.metacustom"), y);
        return y + 20;
    }

    private void cycleKind(Stage current, Stage.Kind kind) {
        Stage.Kind next = Stage.Kind.of((kind.id + 1) % 3);
        Outbound.flagCustom(current, "kind", next.id, false);
        current.flags.put("kind", next.id);
        reinit();
    }

    private int placeOverrides(Stage current, int y, int innerW, boolean metaDenied) {
        column.add(new Label(Text.literal("屏幕设置 [将覆盖玩家全局设置]"), 0, 0, innerW, 12, Style.TEXT_DIM), y);
        y += 12;
        column.add(gate(toggle(0, innerW, current, "worldbar", "下方进度条", Settings.INSTANCE.worldProgressBar), metaDenied, "stage.metacustom"), y);
        y += 20;
        column.add(gate(toggle(0, innerW, current, "danmaku", "弹幕", Settings.INSTANCE.danmaku), metaDenied, "stage.metacustom"), y);
        y += 20;
        if (current.isClone() || current.kind() == Stage.Kind.SPHERE) return y;
        column.add(gate(toggle(0, innerW, current, "dynlight", "动态照明", Settings.INSTANCE.dynamicLight), metaDenied, "stage.metacustom"), y);
        y += 20;
        column.add(gate(toggle(0, innerW, current, "spatial", "空间音频", Settings.INSTANCE.spatialAudio), metaDenied, "stage.metacustom"), y);
        return y + 20;
    }

    private int placeIdle(Stage current, int y, int innerW) {
        if (current.isClone()) return y;
        queryPermStage(current, "stage.idleplay");
        boolean denied = deniedStage(current, "stage.idleplay");
        column.add(new Label(Text.literal("背景 [没有播放内容时显示]"), 0, 0, innerW, 12, Style.TEXT_DIM), y);
        y += 12;
        bgField = buildBgField(innerW, denied);
        column.add(bgField, y);
        y += 20;
        if (denied) column.add(idleRefresh(current, innerW), y);
        else placeIdleActions(current, innerW, y);
        return y + 22;
    }

    private FlatTextField buildBgField(int innerW, boolean denied) {
        FlatTextField field = new FlatTextField(textRenderer, 0, 0, innerW, 18, Text.literal(""));
        field.setMaxLength(512);
        field.setPlaceholder(Text.literal("背景视频/图片链接...").formatted(Formatting.GRAY));
        field.setEditable(!denied);
        return field;
    }

    private ButtonWidget idleRefresh(Stage current, int innerW) {
        ButtonWidget refresh = Style.button(
                Text.literal("无权限设置背景").formatted(Formatting.GRAY),
                0, 0, innerW, 18, b -> {
                    refreshPermStage(current, "stage.idleplay");
                    reinit();
                });
        refresh.setTooltip(Tooltip.of(Text.literal("你缺少 muses.stage.idleplay 权限")));
        return refresh;
    }

    private void placeIdleActions(Stage current, int innerW, int y) {
        int half = (innerW - 6) / 2;
        column.add(Style.button(Text.literal("设为背景"), 0, 0, half, 18, b -> applyIdle(current, false)), 0, y);
        column.add(Style.button(Text.literal("清除背景"), 0, 0, innerW - half - 6, 18, b -> applyIdle(current, true)), half + 6, y);
    }

    private void applyIdle(Stage current, boolean clear) {
        String url = "off";
        if (!clear) {
            String typed = bgField.getText().trim();
            if (!typed.isEmpty()) url = typed;
        }
        Outbound.idle(current, url);
        bgField.setText("");
    }

    private int placeSplit(Stage current, int y, int innerW, boolean sliceDenied) {
        column.add(gate(Style.button(Text.literal("分割画面"), 0, 0, innerW, 18,
                b -> client.setScreen(new SplitTool(this, current))), sliceDenied, "stage.slice"), y);
        return y + 22;
    }

    private void placeDelete(Stage current, int y, int innerW) {
        queryPermStage(current, "stage.remove");
        boolean delDenied = deniedStage(current, "stage.remove");
        ButtonWidget delBtn = Style.button(deleteLabel(delDenied), 0, 0, innerW, 18, b -> confirmOrDelete(current));
        if (delDenied) {
            delBtn.active = false;
            delBtn.setTooltip(Tooltip.of(Text.literal("你缺少 muses.stage.remove 权限")));
        }
        column.add(delBtn, y);
    }

    private Text deleteLabel(boolean delDenied) {
        if (delDenied) return Text.literal("无权限删除此屏").formatted(Formatting.DARK_GRAY);
        if (confirmDelete) return Text.literal("再次点击确认删除").formatted(Formatting.RED);
        return Text.literal("删除此屏").formatted(Formatting.RED);
    }

    private void confirmOrDelete(Stage current) {
        if (!confirmDelete) {
            confirmDelete = true;
            clearChildren();
            init();
            return;
        }
        Outbound.stageDel(current);
        close();
    }

    private ButtonWidget toggle(int relX, int w, Stage t, String key, String label, boolean clientOn) {
        Integer v = t.flags.get(key);
        return Style.button(toggleCaption(label, v, clientOn), relX, 0, w, 18, b -> {
            advanceFlag(t, key, v);
            reinit();
        });
    }

    private void advanceFlag(Stage t, String key, Integer v) {
        if (v == null) {
            Outbound.flagCustom(t, key, 1, false);
            t.flags.put(key, 1);
            return;
        }
        if (v != 0) {
            Outbound.flagCustom(t, key, 0, false);
            t.flags.put(key, 0);
            return;
        }
        Outbound.flagCustom(t, key, 0, true);
        t.flags.remove(key);
    }

    private Text toggleCaption(String label, Integer v, boolean clientOn) {
        if (v == null) {
            String follow = clientOn ? "开" : "关";
            return Text.literal(label + ": 未设置 [跟随客户端: " + follow + "]").formatted(Formatting.WHITE);
        }
        if (v != 0) return Text.literal(label + ": 开").formatted(Formatting.GREEN);
        return Text.literal(label + ": 关").formatted(Formatting.RED);
    }

    private <T extends ClickableWidget> T gate(T w, boolean denied, String node) {
        if (!denied) return w;
        w.active = false;
        w.setTooltip(Tooltip.of(Text.literal("你缺少 muses." + node + " 权限")));
        return w;
    }

    @Override
    protected void renderBody(DrawContext context, int mouseX, int mouseY, float delta) {
        Stage current = target();
        if (current == null) {
            context.drawTextWithShadow(textRenderer, Text.literal("当前没有看向任何屏幕"), bodyX, bodyY, Style.TEXT_DIM);
            return;
        }
        context.drawTextWithShadow(textRenderer, Text.literal("屏幕列表"), sidebarX, bodyY, Style.TEXT_DIM);
        String info = current.isClone()
                ? "克隆屏: " + current.label + " ← " + current.parent
                : "屏幕: " + current.label;
        int headW = textRenderer.getWidth("屏幕设置");
        int maxW = panelW - 20 - headW - 8;
        String shown = textRenderer.trimToWidth(info, Math.max(0, maxW));
        int iw = textRenderer.getWidth(shown);
        context.drawTextWithShadow(textRenderer, Text.literal(shown), panelX + panelW - 10 - iw, panelY + 8, Style.SUBTITLE);
    }

    private static String kindName(Stage.Kind kind) {
        if (kind == Stage.Kind.CURVED) return "曲面屏";
        if (kind == Stage.Kind.SPHERE) return "球形全景屏";
        return "普通屏";
    }
}
