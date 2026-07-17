package org.hypixelskyblockmods.storagelens.feature.itemsearch

import org.hypixelskyblockmods.storagelens.feature.equipment.EquipmentRepository
import org.hypixelskyblockmods.storagelens.feature.loadouts.LoadoutRepository
import org.hypixelskyblockmods.storagelens.feature.storage.ObservedStorageRepository
import org.hypixelskyblockmods.storagelens.feature.wardrobe.WardrobeRepository
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyblockApiItemSearchAdapter

object SkyHudRepositoryItemSources {
    fun initialize() {
        ItemSourceRegistry.register(ItemSourceId.STORAGE, ::storage)
        ItemSourceRegistry.registerAuthoritativeScopes(ItemSourceId.STORAGE, ::storageScopes)
        ItemSourceRegistry.register(ItemSourceId.LOADOUTS, ::loadouts)
        ItemSourceRegistry.register(ItemSourceId.WARDROBE, ::wardrobe)
        ItemSourceRegistry.registerAuthoritativeScopes(ItemSourceId.WARDROBE, ::wardrobeScopes)
        ItemSourceRegistry.register(ItemSourceId.EQUIPMENT_WARDROBE, ::equipment)
        ItemSourceRegistry.registerAuthoritativeScopes(ItemSourceId.EQUIPMENT_WARDROBE, ::equipmentScopes)
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

    private fun storageScopes(): Set<AuthoritativeItemScope> = ObservedStorageRepository.snapshot()
        .mapTo(linkedSetOf()) { storageItemScope(it.key) }

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
                ownershipIdentity = if (set.selected) equippedArmorOwnershipIdentity(index) else null,
            )
        }
    }

    private fun wardrobeScopes(): Set<AuthoritativeItemScope> = WardrobeRepository.sets.snapshot()
        .mapTo(linkedSetOf()) { collectionItemScope(ItemSourceId.WARDROBE, "Wardrobe", it.page, it.id) }

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

    private fun equipmentScopes(): Set<AuthoritativeItemScope> = EquipmentRepository.sets.snapshot()
        .mapTo(linkedSetOf()) { collectionItemScope(ItemSourceId.EQUIPMENT_WARDROBE, "Equipment", it.page, it.id) }
}
