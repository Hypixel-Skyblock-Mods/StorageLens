package org.hypixelskyblockmods.storagelens.feature.itemsearch

import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.storagelens.feature.storage.StoragePageKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemSearchIndexTest {
    @Test
    fun `fingerprint includes complete component variants`() {
        val first = ItemFingerprintFactory.hashComponentPayload("{enchantments:{sharpness:5},reforge:spicy}")
        val second = ItemFingerprintFactory.hashComponentPayload("{enchantments:{sharpness:5},reforge:fabled}")

        assertNotEquals(first, second)
    }

    @Test
    fun `aggregation preserves locations and long amounts`() {
        val fingerprint = fingerprint("enchanted_diamond")
        val first = item(
            ItemStack.EMPTY,
            3_000_000_000L,
            fingerprint,
            ItemLocation.Generic("Storage", "one"),
        )
        val second = item(
            ItemStack.EMPTY,
            4_000_000_000L,
            fingerprint,
            ItemLocation.Generic("Sacks", "two"),
        )

        val entry = ItemSearchIndex.buildForTests(listOf(first, second)).all().single()

        assertEquals(7_000_000_000L, entry.totalAmount)
        assertEquals(listOf("one", "two"), entry.locations.map { it.location.identity })
    }

    @Test
    fun `live observation replaces api record at exact location`() {
        val fingerprint = fingerprint("diamond")
        val location = ItemLocation.Inventory(InventoryRealm.NORMAL, 3)
        val api = item(ItemStack.EMPTY, 1, fingerprint, location, origin = ItemDataOrigin.SKYBLOCK_API_PROFILE)
        val live = item(ItemStack.EMPTY, 9, fingerprint, location, origin = ItemDataOrigin.LIVE_PLAYER)

        val entry = ItemSearchIndex.buildForTests(listOf(api, live)).all().single()

        assertEquals(9, entry.totalAmount)
        assertEquals(1, entry.locations.size)
        assertEquals(ItemDataOrigin.LIVE_PLAYER, entry.locations.single().origin)
    }

    @Test
    fun `equipped armor mirrored by active wardrobe counts once`() {
        val fingerprint = fingerprint("warden_helmet")
        val ownershipIdentity = "equipped-armor:0"
        val equipped = item(
            ItemStack.EMPTY,
            1,
            fingerprint,
            ItemLocation.Inventory(InventoryRealm.NORMAL, 39, equipped = true),
            source = ItemSourceId.EQUIPPED,
            value = 100,
            ownershipIdentity = ownershipIdentity,
        )
        val wardrobe = item(
            ItemStack.EMPTY,
            1,
            fingerprint,
            ItemLocation.Collection("Wardrobe", 1, 2, 0),
            source = ItemSourceId.WARDROBE,
            value = 100,
            ownershipIdentity = ownershipIdentity,
        )

        val index = ItemSearchIndex.buildForTests(listOf(equipped, wardrobe))
        val entry = index.all().single()

        assertEquals(1, entry.totalAmount)
        assertEquals(100, entry.estimatedValue)
        assertEquals(2, entry.locations.size)
        assertEquals(1, index.query("", ItemSourceCategory.INVENTORY, ItemSearchOptions(), ItemSearchSort.AMOUNT, false).size)
        assertEquals(1, index.query("", ItemSourceCategory.WARDROBE_EQUIPMENT, ItemSearchOptions(), ItemSearchSort.AMOUNT, false).size)
    }

    @Test
    fun `inactive wardrobe armor remains separate ownership`() {
        val fingerprint = fingerprint("warden_helmet")
        val equipped = item(
            ItemStack.EMPTY,
            1,
            fingerprint,
            ItemLocation.Inventory(InventoryRealm.NORMAL, 39, equipped = true),
            source = ItemSourceId.EQUIPPED,
            ownershipIdentity = "equipped-armor:0",
        )
        val stored = item(
            ItemStack.EMPTY,
            1,
            fingerprint,
            ItemLocation.Collection("Wardrobe", 1, 3, 0),
            source = ItemSourceId.WARDROBE,
        )

        assertEquals(2, ItemSearchIndex.buildForTests(listOf(equipped, stored)).all().single().totalAmount)
    }

    @Test
    fun `observed storage slot replaces changed api fingerprint`() {
        val location = ItemLocation.Storage(StoragePageKey.enderChest(2), 4)
        val api = item(
            ItemStack.EMPTY,
            1,
            fingerprint("old_item"),
            location,
            origin = ItemDataOrigin.SKYBLOCK_API_PROFILE,
            source = ItemSourceId.STORAGE,
        )
        val observed = item(
            ItemStack.EMPTY,
            32,
            fingerprint("new_item"),
            location,
            origin = ItemDataOrigin.LOCAL_OBSERVATION,
            source = ItemSourceId.STORAGE,
        )

        val entries = ItemSearchIndex.buildForTests(listOf(api, observed)).all()

        assertEquals(1, entries.size)
        assertEquals("new_item", entries.single().name)
        assertEquals(32, entries.single().totalAmount)
    }

    @Test
    fun `observed storage page removes stale api items from other slots`() {
        val page = StoragePageKey.enderChest(1)
        val staleApiItem = item(
            ItemStack.EMPTY,
            1,
            fingerprint("stale_item"),
            ItemLocation.Storage(page, 7),
            origin = ItemDataOrigin.SKYBLOCK_API_PROFILE,
            source = ItemSourceId.STORAGE,
        )
        val currentItem = item(
            ItemStack.EMPTY,
            4,
            fingerprint("current_item"),
            ItemLocation.Storage(page, 2),
            source = ItemSourceId.STORAGE,
        )
        val otherPageItem = item(
            ItemStack.EMPTY,
            8,
            fingerprint("other_page_item"),
            ItemLocation.Storage(StoragePageKey.enderChest(2), 7),
            origin = ItemDataOrigin.SKYBLOCK_API_PROFILE,
            source = ItemSourceId.STORAGE,
        )

        val entries = ItemSearchIndex.buildForTests(
            listOf(staleApiItem, currentItem, otherPageItem),
            setOf(storageItemScope(page)),
        ).all()

        assertEquals(setOf("current_item", "other_page_item"), entries.mapTo(mutableSetOf()) { it.name })
    }

    @Test
    fun `observed empty storage page removes every stale api item`() {
        val page = StoragePageKey.backpack(3)
        val staleApiItem = item(
            ItemStack.EMPTY,
            1,
            fingerprint("removed_item"),
            ItemLocation.Storage(page, 0),
            origin = ItemDataOrigin.SKYBLOCK_API_PROFILE,
            source = ItemSourceId.STORAGE,
        )

        val entries = ItemSearchIndex.buildForTests(
            listOf(staleApiItem),
            setOf(storageItemScope(page)),
        ).all()

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `observed wardrobe set removes stale api items but preserves other sets`() {
        val staleApiItem = item(
            ItemStack.EMPTY,
            1,
            fingerprint("old_helmet"),
            ItemLocation.Collection("Wardrobe", page = 1, setId = 2, itemIndex = 0),
            origin = ItemDataOrigin.SKYBLOCK_API_PROFILE,
            source = ItemSourceId.WARDROBE,
        )
        val otherSetItem = item(
            ItemStack.EMPTY,
            1,
            fingerprint("other_helmet"),
            ItemLocation.Collection("Wardrobe", page = 1, setId = 3, itemIndex = 0),
            origin = ItemDataOrigin.SKYBLOCK_API_PROFILE,
            source = ItemSourceId.WARDROBE,
        )

        val entries = ItemSearchIndex.buildForTests(
            listOf(staleApiItem, otherSetItem),
            setOf(collectionItemScope(ItemSourceId.WARDROBE, "Wardrobe", page = 1, setId = 2)),
        ).all()

        assertEquals(listOf("other_helmet"), entries.map { it.name })
    }

    @Test
    fun `query uses whitespace and enabled fields`() {
        val searchable = item(
            ItemStack.EMPTY,
            1,
            fingerprint("aspect_of_the_end"),
            ItemLocation.Generic("Ender Chest", "aote"),
            name = "Aspect of the End",
            lore = listOf("Instant Transmission teleports you forward"),
            skyblockId = "ASPECT_OF_THE_END",
        )
        val index = ItemSearchIndex.buildForTests(listOf(searchable))

        assertEquals(1, index.query("aspect teleport", ItemSourceCategory.ALL, ItemSearchOptions(), ItemSearchSort.AMOUNT, false).size)
        assertEquals(1, index.query("aspect_of", ItemSourceCategory.ALL, ItemSearchOptions(searchIds = true), ItemSearchSort.AMOUNT, false).size)
        assertTrue(index.query("ender chest", ItemSourceCategory.ALL, ItemSearchOptions(searchLocations = false), ItemSearchSort.AMOUNT, false).isEmpty())
        assertEquals(1, index.query("ender chest", ItemSourceCategory.ALL, ItemSearchOptions(searchLocations = true), ItemSearchSort.AMOUNT, false).size)
    }

    @Test
    fun `sort defaults descending and leaves unknown values last`() {
        val low = item(ItemStack.EMPTY, 2, fingerprint("low"), ItemLocation.Generic("Low"), name = "Low", value = 20)
        val high = item(ItemStack.EMPTY, 8, fingerprint("high"), ItemLocation.Generic("High"), name = "High", value = 80)
        val unknown = item(ItemStack.EMPTY, 5, fingerprint("unknown"), ItemLocation.Generic("Unknown"), name = "Unknown", value = null)
        val index = ItemSearchIndex.buildForTests(listOf(low, high, unknown))

        assertEquals(listOf("High", "Unknown", "Low"), index.query("", ItemSourceCategory.ALL, ItemSearchOptions(), ItemSearchSort.AMOUNT, false).map { it.name })
        assertEquals(listOf("High", "Low", "Unknown"), index.query("", ItemSourceCategory.ALL, ItemSearchOptions(), ItemSearchSort.VALUE, false).map { it.name })
    }

    @Test
    fun `staleness ignores live data and marks old cached data`() {
        val now = 2_000_000_000L
        val old = item(
            ItemStack.EMPTY,
            1,
            fingerprint("old"),
            ItemLocation.Generic("Old"),
            origin = ItemDataOrigin.LOCAL_OBSERVATION,
            updated = now - 86_400_001L,
        )
        val live = item(
            ItemStack.EMPTY,
            1,
            fingerprint("live"),
            ItemLocation.Generic("Live"),
            origin = ItemDataOrigin.LIVE_MENU,
            updated = now - 86_400_001L,
        )

        assertTrue(ItemSearchIndex.buildForTests(listOf(old)).all().single().isStale(now, 86_400_000L))
        assertFalse(ItemSearchIndex.buildForTests(listOf(live)).all().single().isStale(now, 86_400_000L))
    }

    @Test
    fun `tooltip locations retain names amounts and overflow count`() {
        val fingerprint = fingerprint("coal")
        val items = (1..10).map { index ->
            item(
                ItemStack.EMPTY,
                index.toLong(),
                fingerprint,
                ItemLocation.Generic("Forge slot $index", "forge:$index"),
            )
        }

        val lines = ItemSearchIndex.buildForTests(items).all().single().locationTooltipLines(maxLocations = 3)

        assertEquals(listOf("Forge slot 1", "Forge slot 2 × 2", "Forge slot 3 × 3", "…and 7 more locations"), lines)
    }

    @Test
    fun `location picker sorts exact location amounts descending`() {
        val fingerprint = fingerprint("iron")
        val items = listOf(
            item(ItemStack.EMPTY, 25, fingerprint, ItemLocation.Generic("Ender Chest", "ender-chest")),
            item(ItemStack.EMPTY, 4_800, fingerprint, ItemLocation.Generic("Sack", "sack")),
            item(ItemStack.EMPTY, 600, fingerprint, ItemLocation.Generic("Island Chest", "island-chest")),
        )

        val locations = ItemSearchIndex.buildForTests(items).all().single().locationsByDescendingAmount()

        assertEquals(listOf(4_800L, 600L, 25L), locations.map(SearchableItem::amount))
        assertEquals(listOf("sack", "island-chest", "ender-chest"), locations.map { it.location.identity })
    }

    @Test
    fun `grid amounts use one decimal below ten units and whole units afterward`() {
        assertEquals("", itemGridAmountLabel(1))
        assertEquals("999", itemGridAmountLabel(999))
        assertEquals("1k", itemGridAmountLabel(1_000))
        assertEquals("4.8k", itemGridAmountLabel(4_800))
        assertEquals("10k", itemGridAmountLabel(10_000))
        assertEquals("10k", itemGridAmountLabel(10_999))
        assertEquals("999k", itemGridAmountLabel(999_999))
        assertEquals("1.5m", itemGridAmountLabel(1_500_000))
        assertEquals("12m", itemGridAmountLabel(12_750_000))
    }

    private fun item(
        stack: ItemStack,
        amount: Long,
        fingerprint: ItemFingerprint,
        location: ItemLocation,
        origin: ItemDataOrigin = ItemDataOrigin.LOCAL_OBSERVATION,
        name: String = fingerprint.cleanName,
        lore: List<String> = emptyList(),
        skyblockId: String? = fingerprint.skyblockId,
        value: Long? = null,
        updated: Long? = 1_000L,
        source: ItemSourceId = ItemSourceId.INVENTORY,
        ownershipIdentity: String? = null,
    ) = SearchableItem(
        stack = stack,
        amount = amount,
        source = source,
        location = location,
        origin = origin,
        updatedAtEpochMillis = updated,
        fingerprint = fingerprint,
        searchableName = name,
        searchableLore = lore,
        skyblockId = skyblockId,
        estimatedValue = value,
        ownershipIdentity = ownershipIdentity,
    )

    private fun fingerprint(id: String) = ItemFingerprint("minecraft:stone", id.uppercase(), id, id)

}
