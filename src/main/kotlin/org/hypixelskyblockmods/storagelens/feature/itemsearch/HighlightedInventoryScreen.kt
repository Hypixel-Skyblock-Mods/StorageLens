package org.hypixelskyblockmods.storagelens.feature.itemsearch

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.player.Player
import org.hypixelskyblockmods.storagelens.gui.SkyHudTheme

class HighlightedInventoryScreen(
    private val targetPlayer: Player,
    private val inventorySlot: Int,
) : InventoryScreen(targetPlayer) {
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        val slot = menu.slots.firstOrNull {
            it.container === targetPlayer.inventory && it.containerSlot == inventorySlot
        } ?: return
        val x = leftPos + slot.x
        val y = topPos + slot.y
        val color = SkyHudTheme.PRIMARY_HOVER
        graphics.fill(x - 1, y - 1, x + 17, y, color)
        graphics.fill(x - 1, y + 16, x + 17, y + 17, color)
        graphics.fill(x - 1, y, x, y + 16, color)
        graphics.fill(x + 16, y, x + 17, y + 16, color)
    }
}
