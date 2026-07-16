package org.hypixelskyblockmods.storagelens.feature.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StorageMenuDetectionTest {
    @Test
    fun `ender chest page titles retain page and total`() {
        assertEquals(
            ParsedStoragePage(StoragePageKey.enderChest(1), 9),
            StorageMenuDetection.parsePageTitle("Ender Chest (1/9)"),
        )
        assertEquals(
            ParsedStoragePage(StoragePageKey.enderChest(7), 9),
            StorageMenuDetection.parsePageTitle("Ender Chest ✦ (7/9)"),
        )
    }

    @Test
    fun `backpack page titles retain slot number`() {
        assertEquals(
            ParsedStoragePage(StoragePageKey.backpack(18), null),
            StorageMenuDetection.parsePageTitle("Large Backpack (Slot #18)"),
        )
        assertEquals(
            ParsedStoragePage(StoragePageKey.backpack(3), null),
            StorageMenuDetection.parsePageTitle("Jumbo Backpack ✦ (Slot #3)"),
        )
    }

    @Test
    fun `storage page title parsing rejects malformed and out of range pages`() {
        assertNull(StorageMenuDetection.parsePageTitle("Ender Chest (0/9)"))
        assertNull(StorageMenuDetection.parsePageTitle("Ender Chest (10/10)"))
        assertNull(StorageMenuDetection.parsePageTitle("Ender Chest (4/3)"))
        assertNull(StorageMenuDetection.parsePageTitle("Backpack (Slot #19)"))
        assertNull(StorageMenuDetection.parsePageTitle("Chest (1/9)"))
    }
}
