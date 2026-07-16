package org.hypixelskyblockmods.storagelens.feature.wardrobe

import net.minecraft.client.gui.screens.Screen
import org.hypixelskyblockmods.storagelens.feature.sets.SetCollectionDetection
import org.hypixelskyblockmods.storagelens.feature.sets.SetCollectionTarget

typealias WardrobeTarget = SetCollectionTarget

object WardrobeDetector {
    private val titlePatterns = listOf(
        Regex("^\\(([1-9][0-9]*)/([1-9][0-9]*)\\) Armor Sets$"),
        Regex("^Wardrobe \\(([1-9][0-9]*)/([1-9][0-9]*)\\)$"),
    )

    fun detect(screen: Screen): WardrobeTarget? = SetCollectionDetection.detect(screen, titlePatterns)
}
