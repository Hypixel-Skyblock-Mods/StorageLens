package org.hypixelskyblockmods.storagelens.mixin

import net.minecraft.client.renderer.GameRenderer
import org.hypixelskyblockmods.storagelens.gui.SkyHudBackdrop
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(GameRenderer::class)
abstract class GuiBlurMixin {
    @Inject(method = ["processBlurEffect"], at = [At("HEAD")])
    private fun storageLensCaptureBackdrop(callback: CallbackInfo) {
        SkyHudBackdrop.captureFrame()
    }
}
