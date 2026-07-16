package org.hypixelskyblockmods.storagelens.util

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

object ItemText {
    fun lore(stack: ItemStack): List<String> =
        stack.get(DataComponents.LORE)?.lines?.map { it.string }.orEmpty()
}
