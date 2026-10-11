package com.puddingkc.muses.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.puddingkc.muses.light.DynamicLights;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldRenderer.class)
public class LightmapMixin {
    @ModifyReturnValue(method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I", at = @At("RETURN"))
    private static int museplayer$boostByPos(int original, BlockRenderView view, BlockPos pos) {
        return museplayer$apply(original, pos);
    }

    @ModifyReturnValue(method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I", at = @At("RETURN"))
    private static int museplayer$boostByState(int original, BlockRenderView view, BlockState state, BlockPos pos) {
        return museplayer$apply(original, pos);
    }

    private static int museplayer$apply(int original, BlockPos pos) {
        if (!DynamicLights.active()) return original;
        int emission = DynamicLights.emissionAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (emission <= 0) return original;
        return LightmapTextureManager.applyEmission(original, emission);
    }
}
