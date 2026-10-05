package org.hypixelskyblockmods.storagelens.mixin

import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.screens.Screen
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ContainerMenuObservation
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [Gui::class], priority = 2000)
abstract class ScreenObservationMixin {
    @Inject(method = ["setScreen"], at = [At("HEAD")])
    private fun storageLensObserveIncomingScreen(screen: Screen?, callback: CallbackInfo) {
        screen?.let(ContainerMenuObservation::observe)
    }
}
