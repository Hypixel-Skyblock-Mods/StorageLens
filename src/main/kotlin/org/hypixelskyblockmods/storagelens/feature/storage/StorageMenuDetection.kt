package org.hypixelskyblockmods.storagelens.feature.storage

import net.minecraft.world.inventory.ChestMenu

sealed interface StorageMenuTarget {
    val menu: ChestMenu

    data class Overview(
        override val menu: ChestMenu,
    ) : StorageMenuTarget

    data class Page(
        val key: StoragePageKey,
        val totalEnderChestPages: Int?,
        override val menu: ChestMenu,
    ) : StorageMenuTarget
}

internal data class ParsedStoragePage(
    val key: StoragePageKey,
    val totalEnderChestPages: Int?,
)

object StorageMenuDetection {
    private val enderChestPattern = Regex("^Ender Chest (?:✦ )?\\(([1-9][0-9]*)/([1-9][0-9]*)\\)$")
    private val backpackPattern = Regex("^.+Backpack (?:✦ )?\\(Slot #([1-9][0-9]*)\\)$")

    fun detect(title: String, menu: ChestMenu): StorageMenuTarget? {
        if (title == "Storage") {
            if (menu.rowCount != 6 || menu.slots.size < 90) return null
            val overviewSlots = (9..17) + (27..44)
            if (overviewSlots.none { !menu.getSlot(it).item.isEmpty }) return null
            return StorageMenuTarget.Overview(menu)
        }

        val parsed = parsePageTitle(title) ?: return null
        if (!validPageMenu(menu)) return null
        return StorageMenuTarget.Page(parsed.key, parsed.totalEnderChestPages, menu)
    }

    internal fun parsePageTitle(title: String): ParsedStoragePage? {
        enderChestPattern.matchEntire(title)?.let { match ->
            val page = match.groupValues[1].toIntOrNull() ?: return null
            val total = match.groupValues[2].toIntOrNull() ?: return null
            if (page !in 1..total || total !in 1..9) return null
            return ParsedStoragePage(StoragePageKey.enderChest(page), total)
        }

        val match = backpackPattern.matchEntire(title) ?: return null
        val page = match.groupValues[1].toIntOrNull() ?: return null
        if (page !in 1..18) return null
        return ParsedStoragePage(StoragePageKey.backpack(page), null)
    }

    private fun validPageMenu(menu: ChestMenu): Boolean =
        menu.rowCount in 2..6 && menu.slots.size >= menu.rowCount * 9 + 36
}
