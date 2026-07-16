package org.hypixelskyblockmods.storagelens.feature.itemsearch

import org.hypixelskyblockmods.storagelens.feature.equipment.EquipmentRepository
import org.hypixelskyblockmods.storagelens.feature.loadouts.LoadoutRepository
import org.hypixelskyblockmods.storagelens.feature.storage.ObservedStorageRepository
import org.hypixelskyblockmods.storagelens.feature.wardrobe.WardrobeRepository
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyblockApiItemSearchAdapter

object SkyHudRepositoryItemSources {
    fun initialize() {
        ItemSourceRegistry.register(ItemSourceId.STORAGE, ::storage)
        ItemSourceRegistry.register(ItemSourceId.LOADOUTS, ::loadouts)
        ItemSourceRegistry.register(ItemSourceId.WARDROBE, ::wardrobe)
        ItemSourceRegistry.register(ItemSourceId.EQUIPMENT_WARDROBE, ::equipment)
    }

    private fun storage(): List<SearchableItem> = ObservedStorageRepository.snapshot().flatMap { page ->
        page.items.mapIndexedNotNull { index, stack ->
            SkyblockApiItemSearchAdapter.searchable(
                stack,
                stack.count.toLong(),
                ItemSourceId.STORAGE,
                ItemLocation.Storage(page.key, index),
                ItemNavigationAction.Storage(page.key, index),
                page.origin,
                page.updatedAtEpochMillis,
            )
        }
    }

    private fun loadouts(): List<SearchableItem> = LoadoutRepository.snapshot().flatMap { loadout ->
        loadout.items.mapIndexedNotNull { index, stack ->
            SkyblockApiItemSearchAdapter.searchable(
                stack,
                stack.count.toLong(),
                ItemSourceId.LOADOUTS,
                ItemLocation.Collection("Loadout", loadout.page, loadout.id, index),
                ItemNavigationAction.Collection(CollectionType.LOADOUT, loadout.page, loadout.id, index),
                ItemDataOrigin.LOCAL_OBSERVATION,
                loadout.updatedAtEpochMillis,
            )
        }
    }

    private fun wardrobe(): List<SearchableItem> = WardrobeRepository.sets.snapshot().flatMap { set ->
        set.items.mapIndexedNotNull { index, stack ->
            SkyblockApiItemSearchAdapter.searchable(
                stack,
                stack.count.toLong(),
                ItemSourceId.WARDROBE,
                ItemLocation.Collection("Wardrobe", set.page, set.id, index),
                ItemNavigationAction.Collection(CollectionType.WARDROBE, set.page, set.id, index),
                ItemDataOrigin.LOCAL_OBSERVATION,
                set.updatedAtEpochMillis,
            )
        }
    }

    private fun equipment(): List<SearchableItem> = EquipmentRepository.sets.snapshot().flatMap { set ->
        set.items.mapIndexedNotNull { index, stack ->
            SkyblockApiItemSearchAdapter.searchable(
                stack,
                stack.count.toLong(),
                ItemSourceId.EQUIPMENT_WARDROBE,
                ItemLocation.Collection("Equipment", set.page, set.id, index),
                ItemNavigationAction.Collection(CollectionType.EQUIPMENT, set.page, set.id, index),
                ItemDataOrigin.LOCAL_OBSERVATION,
                set.updatedAtEpochMillis,
            )
        }
    }
}
