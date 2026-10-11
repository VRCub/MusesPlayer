package com.puddingkc.muses.mixin.client;

import com.puddingkc.muses.input.DragController;
import com.puddingkc.muses.world.Theater;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "clearWorld", at = @At("HEAD"))
    private void museplayer$clearWorld(CallbackInfo ci) {
        Theater.reset();
        DragController.reset();
    }
}
