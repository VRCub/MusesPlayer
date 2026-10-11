package com.puddingkc.muses.mixin.client;

import com.puddingkc.muses.input.DragController;
import com.puddingkc.muses.ui.CreateSelector;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void museplayer$moveAttack(CallbackInfoReturnable<Boolean> cir) {
        if (DragController.isMoving()) {
            DragController.confirm();
            cir.setReturnValue(false);
            return;
        }
        if (CreateSelector.active()) {
            CreateSelector.pick();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void museplayer$moveUse(CallbackInfo ci) {
        if (DragController.isMoving()) {
            DragController.cancel();
            ci.cancel();
            return;
        }
        if (CreateSelector.active()) {
            CreateSelector.undo();
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void museplayer$blockBreaking(boolean breaking, CallbackInfo ci) {
        if (DragController.isMoving() || CreateSelector.active()) ci.cancel();
    }
}
