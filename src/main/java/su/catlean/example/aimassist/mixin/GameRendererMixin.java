package su.catlean.example.aimassist.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.catlean.example.aimassist.AimAssistModule;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void catleanAddon$onWorldRender(DeltaTracker deltaTracker, CallbackInfo ci) {
        AimAssistModule.INSTANCE.onWorldRender(deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
