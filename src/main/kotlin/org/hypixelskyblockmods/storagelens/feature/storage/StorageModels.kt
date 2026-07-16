package org.hypixelskyblockmods.storagelens.feature.storage

enum class StoragePageType {
    ENDER_CHEST,
    BACKPACK,
}

data class StoragePageKey(
    val type: StoragePageType,
    val number: Int,
) : Comparable<StoragePageKey> {
    val displayName: String
        get() = when (type) {
            StoragePageType.ENDER_CHEST -> "Ender Chest $number"
            StoragePageType.BACKPACK -> "Backpack $number"
        }

    val navigationCommand: String
        get() = when (type) {
            StoragePageType.ENDER_CHEST -> "enderchest $number"
            StoragePageType.BACKPACK -> "backpack $number"
        }

    override fun compareTo(other: StoragePageKey): Int =
        compareValuesBy(this, other, { it.type.ordinal }, { it.number })

    companion object {
        fun enderChest(number: Int): StoragePageKey = StoragePageKey(StoragePageType.ENDER_CHEST, number)

        fun backpack(number: Int): StoragePageKey = StoragePageKey(StoragePageType.BACKPACK, number)
    }
}
