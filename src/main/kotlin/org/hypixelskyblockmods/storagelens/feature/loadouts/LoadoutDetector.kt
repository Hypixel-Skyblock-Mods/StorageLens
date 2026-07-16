package org.hypixelskyblockmods.storagelens.feature.loadouts

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu

data class LoadoutTarget(
    val page: Int,
    val totalPages: Int,
    val menu: ChestMenu,
)

object LoadoutDetector {
    private val titlePattern = Regex("^\\(([1-9][0-9]*)/([1-9][0-9]*)\\) Loadouts$")

    fun detect(screen: Screen): LoadoutTarget? {
        val containerScreen = screen as? AbstractContainerScreen<*> ?: return null
        val menu = containerScreen.menu as? ChestMenu ?: return null
        val match = titlePattern.matchEntire(screen.title.string) ?: return null
        val page = match.groupValues[1].toIntOrNull() ?: return null
        val totalPages = match.groupValues[2].toIntOrNull() ?: return null

        if (menu.rowCount != 6 || totalPages !in 1..3 || page !in 1..totalPages) return null
        if (menu.slots.size < 90) return null
        if (LoadoutLayout.iconSlots(page).any { menu.getSlot(it).item.isEmpty }) return null

        return LoadoutTarget(page, totalPages, menu)
    }
}
