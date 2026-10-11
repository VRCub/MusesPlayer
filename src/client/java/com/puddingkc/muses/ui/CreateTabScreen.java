package com.puddingkc.muses.ui;

import com.puddingkc.muses.world.Theater;
import com.puddingkc.muses.world.Venue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CreateTabScreen extends MusesScreen {
    private TextFieldWidget venueField;
    private TextFieldWidget screenField;
    private TextFieldWidget sourceField;

    public CreateTabScreen(Screen parent) {
        super(parent, "新建");
    }

    @Override
    protected int panelHeight() {
        return 264;
    }

    @Override
    protected void initBody() {
        queryPermLoc("venue.create");
        queryPermLoc("stage.create");
        boolean venueDenied = deniedLoc("venue.create");
        boolean stageDenied = deniedLoc("stage.create");
        addDrawableChild(gated(Text.literal("创建观影区"), bodyX, bodyY, bodyW, 18,
                b -> CreateSelector.beginVenue(), venueDenied, "你缺少 muses.venue.create 权限"));
        addDrawableChild(gated(Text.literal("创建屏幕"), bodyX, bodyY + 22, bodyW, 18,
                b -> CreateSelector.beginScreen(), stageDenied, "你缺少 muses.stage.create 权限"));
        if (CreateSelector.hasPendingVenue()) {
            placeVenueConfirm(venueDenied);
        } else if (CreateSelector.hasPendingScreen()) {
            placeScreenConfirm(stageDenied);
        }
        addDrawableChild(Style.button(Text.literal("取消创建"), bodyX, cancelButtonY(), bodyW, 18, b -> {
            CreateSelector.cancel();
            clearAndInit();
        }));
    }

    private int cancelButtonY() {
        int backTop = panelY + panelH - 10 - 18;
        return backTop - 22;
    }

    private TextFieldWidget nameField(int x, int y, int w, String placeholder, String initial) {
        TextFieldWidget field = new FlatTextField(textRenderer, x, y, w, 18, Text.literal(placeholder));
        field.setMaxLength(32);
        field.setPlaceholder(Text.literal(placeholder).formatted(Formatting.GRAY));
        if (initial != null && !initial.isEmpty()) field.setText(initial);
        return field;
    }

    private static String currentVenueLabel() {
        for (Venue venue : Theater.venues.values()) {
            if (venue.loaded) return venue.label;
        }
        return "";
    }

    @Override
    protected void renderBody(DrawContext context, int mouseX, int mouseY, float delta) {
        String hint = CreateSelector.active()
                ? CreateSelector.statusText()
                : "点击上方按钮进入选区模式，在世界中左键选点并创建区域";
        String shown = textRenderer.trimToWidth(hint, bodyW);
        context.drawTextWithShadow(textRenderer, Text.literal(shown), bodyX, cancelButtonY() - 12, Style.TEXT_DIM);
    }

    private void placeVenueConfirm(boolean venueDenied) {
        int fieldY = bodyY + 50;
        venueField = nameField(bodyX, fieldY, bodyW, "观影区名称", "");
        addDrawableChild(venueField);
        addDrawableChild(gated(Text.literal("确认创建观影区"), bodyX, fieldY + 22, bodyW, 18,
                b -> submitVenue(), venueDenied, "你缺少 muses.venue.create 权限"));
    }

    private void placeScreenConfirm(boolean stageDenied) {
        int fieldY = bodyY + 50;
        venueField = nameField(bodyX, fieldY, bodyW, "观影区名称", currentVenueLabel());
        screenField = nameField(bodyX, fieldY + 22, bodyW, "屏幕名称", "");
        sourceField = nameField(bodyX, fieldY + 44, bodyW, "屏幕源 [可选]", "");
        addDrawableChild(venueField);
        addDrawableChild(screenField);
        addDrawableChild(sourceField);
        addDrawableChild(gated(Text.literal("确认创建屏幕"), bodyX, fieldY + 66, bodyW, 18,
                b -> submitScreen(), stageDenied, "你缺少 muses.stage.create 权限"));
    }

    private void submitVenue() {
        String name = venueField.getText().trim();
        if (name.isEmpty()) {
            Messages.error("请输入观影区名称");
            return;
        }
        CreateSelector.confirmVenue(name);
        clearAndInit();
    }

    private void submitScreen() {
        String venue = venueField.getText().trim();
        String screen = screenField.getText().trim();
        String source = sourceField.getText().trim();
        if (venue.isEmpty() || screen.isEmpty()) {
            Messages.error("请输入观影区名称和屏幕名称");
            return;
        }
        CreateSelector.confirmScreen(venue, screen, source);
        clearAndInit();
    }

    private ButtonWidget gated(Text label, int x, int y, int w, int h, ButtonWidget.PressAction action, boolean denied, String tip) {
        ButtonWidget button = Style.button(label, x, y, w, h, action);
        if (!denied) return button;
        button.active = false;
        button.setTooltip(Tooltip.of(Text.literal(tip)));
        return button;
    }
}
