package su.catlean.example.aimassist.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import su.catlean.example.aimassist.AimAssistModule;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void catleanAddon$onMouseTurn(LocalPlayer player, double deltaX, double deltaY) {
        double[] filteredDelta = AimAssistModule.INSTANCE.onMouseTurn(deltaX, deltaY);
        player.turn(filteredDelta[0], filteredDelta[1]);
    }
}
