package org.hypixelskyblockmods.storagelens.feature.itemsearch

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu
import org.hypixelskyblockmods.storagelens.feature.equipment.EquipmentDetector
import org.hypixelskyblockmods.storagelens.feature.equipment.EquipmentRepository
import org.hypixelskyblockmods.storagelens.feature.loadouts.LoadoutDetector
import org.hypixelskyblockmods.storagelens.feature.loadouts.LoadoutRepository
import org.hypixelskyblockmods.storagelens.feature.storage.ObservedStorageRepository
import org.hypixelskyblockmods.storagelens.feature.wardrobe.WardrobeDetector
import org.hypixelskyblockmods.storagelens.feature.wardrobe.WardrobeRepository

/** Records validated backing menus without depending on the visible screen implementation. */
object ContainerMenuObservation {
    private var backingTitle: String? = null
    private var backingMenu: ChestMenu? = null

    fun observe(screen: Screen) {
        val container = screen as? AbstractContainerScreen<*> ?: return
        val menu = container.menu as? ChestMenu ?: return
        observe(screen.title.string, menu)
    }

    fun observe(title: String, menu: ChestMenu) {
        backingTitle = title
        backingMenu = menu
        observeValidated(title, menu)
    }

    fun onClientTick() {
        val menu = backingMenu ?: return
        if (net.minecraft.client.Minecraft.getInstance().player?.containerMenu !== menu) {
            clearBacking()
            return
        }
        observeValidated(backingTitle ?: return, menu)
    }

    fun clearBacking() {
        backingTitle = null
        backingMenu = null
    }

    private fun observeValidated(title: String, menu: ChestMenu) {
        ObservedStorageRepository.observe(title, menu)
        LoadoutDetector.detect(title, menu)?.let { LoadoutRepository.remember(it.page, it.menu) }
        WardrobeDetector.detect(title, menu)?.let { WardrobeRepository.sets.remember(it.page, it.menu) }
        EquipmentDetector.detect(title, menu)?.let { EquipmentRepository.sets.remember(it.page, it.menu) }
    }
}
