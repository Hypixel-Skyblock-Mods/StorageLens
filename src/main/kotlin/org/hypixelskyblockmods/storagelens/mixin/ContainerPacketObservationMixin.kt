package org.hypixelskyblockmods.storagelens.mixin

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ContainerMenuObservation
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Observes the server-backed menu independently of the screen used to render it.
 * This keeps storage tracking compatible with any mod that replaces or cancels
 * vanilla container screens.
 */
@Mixin(ClientPacketListener::class)
abstract class ContainerPacketObservationMixin {
    @Inject(method = ["handleOpenScreen"], at = [At("TAIL")])
    private fun storageLensObserveOpenedMenu(packet: ClientboundOpenScreenPacket, callback: CallbackInfo) {
        val menu = Minecraft.getInstance().player?.containerMenu as? ChestMenu ?: return
        if (menu.containerId != packet.containerId) return
        ContainerMenuObservation.observe(packet.title.string, menu)
    }

    @Inject(method = ["handleContainerContent"], at = [At("TAIL")])
    private fun storageLensObserveMenuContents(packet: ClientboundContainerSetContentPacket, callback: CallbackInfo) {
        ContainerMenuObservation.observeContents(packet.containerId, packet.items.map(ItemStack::copy))
    }

    @Inject(method = ["handleContainerSetSlot"], at = [At("TAIL")])
    private fun storageLensObserveMenuSlot(packet: ClientboundContainerSetSlotPacket, callback: CallbackInfo) {
        ContainerMenuObservation.observeSlotChange(packet.containerId)
    }
}
