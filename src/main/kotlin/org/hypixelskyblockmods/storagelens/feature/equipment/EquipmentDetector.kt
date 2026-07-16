package org.hypixelskyblockmods.storagelens.feature.equipment

import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.inventory.ChestMenu
import org.hypixelskyblockmods.storagelens.feature.sets.SetCollectionDetection
import org.hypixelskyblockmods.storagelens.feature.sets.SetCollectionTarget

typealias EquipmentTarget = SetCollectionTarget

object EquipmentDetector {
    private val titlePatterns = listOf(
        Regex("^\\(([1-9][0-9]*)/([1-9][0-9]*)\\) Equipment Sets$"),
    )

    fun detect(screen: Screen): EquipmentTarget? = SetCollectionDetection.detect(screen, titlePatterns)

    fun detect(title: String, menu: ChestMenu): EquipmentTarget? = SetCollectionDetection.detect(title, menu, titlePatterns)
}
