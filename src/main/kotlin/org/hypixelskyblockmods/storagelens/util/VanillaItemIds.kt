package org.hypixelskyblockmods.storagelens.util

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack

object VanillaItemIds {
    fun isItem(stack: ItemStack, path: String): Boolean =
        !stack.isEmpty && BuiltInRegistries.ITEM.getKey(stack.item).path == path

    fun isGlassPane(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val path = BuiltInRegistries.ITEM.getKey(stack.item).path
        return path == "glass_pane" || path.endsWith("_stained_glass_pane")
    }
}
