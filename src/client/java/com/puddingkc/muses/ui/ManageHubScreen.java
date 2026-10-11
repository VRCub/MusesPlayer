package com.puddingkc.muses.ui;

import com.puddingkc.muses.net.Outbound;
import com.puddingkc.muses.world.Stage;
import com.puddingkc.muses.world.Theater;
import com.puddingkc.muses.world.Venue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ManageHubScreen extends MusesScreen {
    private final Stage target;
    private boolean confirmDelete = false;

    public ManageHubScreen(Screen parent, Stage target) {
        super(parent, "管理");
        this.target = target;
    }

    @Override
    protected int panelHeight() {
        return 172;
    }

    @Override
    protected void initBody() {
        int left = bodyX;
        int top = bodyY;
        int span = bodyW;
        addDrawableChild(Style.button(Text.literal("新建观影区或屏幕"), left, top, span, 18, b ->
                client.setScreen(new CreateTabScreen(this))));
        addDrawableChild(Style.button(Text.literal("屏幕设置"), left, top + 22, span, 18, b ->
                client.setScreen(new ScreenEditScreen(this, target))));
        queryPermLoc("venue.remove");
        addDrawableChild(deleteButton(left, span));
    }

    private static String currentVenueLabel() {
        for (Venue venue : Theater.venues.values()) {
            if (venue.loaded) return venue.label;
        }
        return "";
    }

    @Override
    protected void renderBody(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawTextWithShadow(textRenderer,
                Text.literal("创建区域或设置屏幕属性"),
                bodyX, bodyY + 48, Style.TEXT_DIM);
    }

    private ButtonWidget deleteButton(int left, int span) {
        boolean blocked = deniedLoc("venue.remove");
        String venue = currentVenueLabel();
        int delY = panelY + panelH - 10 - 22 - 18;
        ButtonWidget delBtn = Style.button(deleteLabel(venue, blocked), left, delY, span, 18, b -> tryDelete(venue, blocked));
        if (blocked && !venue.isEmpty()) {
            delBtn.setTooltip(Tooltip.of(Text.literal("你缺少 muses.venue.remove 权限")));
        }
        return delBtn;
    }

    private Text deleteLabel(String venue, boolean blocked) {
        if (venue.isEmpty()) return Text.literal("删除观影区").formatted(Formatting.DARK_GRAY);
        if (blocked) return Text.literal("无权限删除此观影区").formatted(Formatting.DARK_GRAY);
        if (confirmDelete) return Text.literal("再次点击确认删除 " + venue).formatted(Formatting.RED);
        return Text.literal("删除当前观影区 " + venue).formatted(Formatting.RED);
    }

    private void tryDelete(String venue, boolean blocked) {
        if (venue.isEmpty() || blocked) return;
        if (!confirmDelete) {
            confirmDelete = true;
            clearAndInit();
            return;
        }
        Outbound.venueDel(venue);
        client.setScreen(null);
    }
}
