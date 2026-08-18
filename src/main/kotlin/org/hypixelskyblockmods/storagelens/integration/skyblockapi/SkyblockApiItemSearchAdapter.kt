package org.hypixelskyblockmods.storagelens.integration.skyblockapi

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.ChestType
import org.hypixelskyblockmods.storagelens.feature.storage.StoragePageKey
import org.hypixelskyblockmods.storagelens.feature.itemsearch.InventoryRealm
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ItemDataOrigin
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ItemFingerprintFactory
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ItemLocation
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ItemNavigationAction
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ItemSourceId
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ItemSourceRegistry
import org.hypixelskyblockmods.storagelens.feature.itemsearch.SearchableItem
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypes
import tech.thatgravyboat.skyblockapi.api.datatype.getData
import tech.thatgravyboat.skyblockapi.api.item.calculator.getItemValue
import tech.thatgravyboat.skyblockapi.api.location.LocationAPI
import tech.thatgravyboat.skyblockapi.api.location.SkyBlockIsland
import tech.thatgravyboat.skyblockapi.api.profile.items.accessory.AccessoryBagAPI
import tech.thatgravyboat.skyblockapi.api.profile.items.equipment.EquipmentAPI
import tech.thatgravyboat.skyblockapi.api.profile.items.forge.ForgeAPI
import tech.thatgravyboat.skyblockapi.api.profile.items.loadout.ArmorWardrobeAPI
import tech.thatgravyboat.skyblockapi.api.profile.items.loadout.EquipmentWardrobeAPI
import tech.thatgravyboat.skyblockapi.api.profile.items.museum.MuseumAPI
import tech.thatgravyboat.skyblockapi.api.profile.items.sacks.SacksAPI
import tech.thatgravyboat.skyblockapi.api.profile.items.storage.StorageAPI
import tech.thatgravyboat.skyblockapi.api.profile.items.vault.VaultAPI
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId
import tech.thatgravyboat.skyblockapi.utils.extentions.cleanName
import tech.thatgravyboat.skyblockapi.utils.extentions.getRawLore

object SkyblockApiItemSearchAdapter {
    fun initializeSources() {
        ItemSourceRegistry.register(ItemSourceId.STORAGE, ::storageItems)
        ItemSourceRegistry.register(ItemSourceId.EQUIPPED, ::normalEquipment)
        ItemSourceRegistry.register(ItemSourceId.RIFT, ::riftItems)
        ItemSourceRegistry.register(ItemSourceId.WARDROBE, ::armorWardrobe)
        ItemSourceRegistry.register(ItemSourceId.EQUIPMENT_WARDROBE, ::equipmentWardrobe)
        ItemSourceRegistry.register(ItemSourceId.ACCESSORY_BAG, ::accessoryBag)
        ItemSourceRegistry.register(ItemSourceId.SACKS, ::sacks)
        ItemSourceRegistry.register(ItemSourceId.VAULT, ::vault)
        ItemSourceRegistry.register(ItemSourceId.FORGE, ::forge)
        ItemSourceRegistry.register(ItemSourceId.MUSEUM, ::museum)
        ItemSourceRegistry.registerDerived(ItemSourceId.INSTALLED_PARTS, ::installedParts)
    }

    fun isOnSkyBlock(): Boolean = LocationAPI.isOnSkyBlock

    fun currentRealm(): InventoryRealm =
        if (LocationAPI.island == SkyBlockIsland.THE_RIFT) InventoryRealm.RIFT else InventoryRealm.NORMAL

    fun isOwnPrivateIsland(): Boolean =
        LocationAPI.island == SkyBlockIsland.PRIVATE_ISLAND && !LocationAPI.isGuest

    fun hasSkyBlockId(stack: ItemStack): Boolean = stack.getData(DataTypes.ID) != null

    fun cleanName(stack: ItemStack): String = stack.cleanName

    fun chestPositions(pos: BlockPos): List<BlockPos>? {
        val level = Minecraft.getInstance().level ?: return null
        val state = level.getBlockState(pos)
        if (state.block !is ChestBlock) return null
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return listOf(pos.immutable())
        val connected = pos.relative(ChestBlock.getConnectedDirection(state))
        val connectedState = level.getBlockState(connected)
        return if (connectedState.block == state.block && connectedState.block is ChestBlock) {
            listOf(pos.immutable(), connected.immutable())
        } else {
            null
        }
    }

    fun isChest(state: BlockState): Boolean = state.block is ChestBlock

    fun searchable(
        stack: ItemStack,
        amount: Long,
        source: ItemSourceId,
        location: ItemLocation,
        action: ItemNavigationAction,
        origin: ItemDataOrigin,
        updatedAtEpochMillis: Long? = null,
        ownershipIdentity: String? = null,
        contributesToTotals: Boolean = true,
    ): SearchableItem? {
        if (stack.isEmpty || amount <= 0) return null
        val copy = stack.copyWithCount(1)
        val cleanName = copy.cleanName
        val skyblockId = copy.getData(DataTypes.ID)
        val unitValue = copy.getItemValue().price.takeIf { it > 0 }
        return SearchableItem(
            stack = copy,
            amount = amount,
            source = source,
            location = location,
            action = action,
            origin = origin,
            updatedAtEpochMillis = updatedAtEpochMillis,
            fingerprint = ItemFingerprintFactory.create(copy, skyblockId, cleanName),
            searchableName = cleanName,
            searchableLore = copy.getRawLore(),
            skyblockId = skyblockId,
            rarityOrdinal = copy.getData(DataTypes.RARITY)?.ordinal,
            estimatedValue = unitValue?.saturatedMultiply(amount),
            ownershipIdentity = ownershipIdentity,
            instanceUuid = copy.getData(DataTypes.UUID),
            contributesToTotals = contributesToTotals,
        )
    }

    private fun storageItems(): List<SearchableItem> =
        SkyblockApiStorageAdapter.allPages().flatMap { page ->
            page.items.mapIndexedNotNull { index, stack ->
                searchable(
                    stack,
                    stack.count.toLong(),
                    ItemSourceId.STORAGE,
                    ItemLocation.Storage(page.key, index),
                    ItemNavigationAction.Storage(page.key, index),
                    ItemDataOrigin.SKYBLOCK_API_PROFILE,
                    page.updatedAtEpochMillis,
                )
            }
        }

    private fun normalEquipment(): List<SearchableItem> = profileSnapshot {
        EquipmentAPI.normalEquipment.entries.mapIndexedNotNull { index, (slot, stack) ->
            searchable(
                stack,
                stack.count.toLong(),
                ItemSourceId.EQUIPPED,
                ItemLocation.Container("Equipped ${slot.name.lowercase()}", slot = index),
                ItemNavigationAction.Command("equipment"),
                ItemDataOrigin.SKYBLOCK_API_PROFILE,
            )
        }
    }

    private fun riftItems(): List<SearchableItem> = profileSnapshot {
        val storage = StorageAPI.riftStorage.flatMap { page ->
            val number = page.index + 1
            if (number !in 1..9) return@flatMap emptyList()
            val key = StoragePageKey.enderChest(number)
            page.items.mapIndexedNotNull { index, stack ->
                searchable(
                    stack,
                    stack.count.toLong(),
                    ItemSourceId.RIFT,
                    ItemLocation.Storage(key, index, rift = true),
                    ItemNavigationAction.Storage(key, index, rift = true),
                    ItemDataOrigin.SKYBLOCK_API_PROFILE,
                    page.lastUpdated.toEpochMilliseconds(),
                )
            }
        }
        val equipment = EquipmentAPI.riftEquipment.entries.mapIndexedNotNull { index, (slot, stack) ->
            searchable(
                stack,
                stack.count.toLong(),
                ItemSourceId.RIFT,
                ItemLocation.Container("Rift ${slot.name.lowercase()}", slot = index),
                ItemNavigationAction.Command("stats"),
                ItemDataOrigin.SKYBLOCK_API_PROFILE,
            )
        }
        storage + equipment
    }

    private fun armorWardrobe(): List<SearchableItem> = profileSnapshot {
        val currentSlot = ArmorWardrobeAPI.currentSlot
        ArmorWardrobeAPI.slots.filterNot { it.locked }.flatMap { slot ->
            val page = (slot.id - 1) / 9 + 1
            slot.slots.mapIndexedNotNull { index, stack ->
                searchable(
                    stack,
                    stack.count.toLong(),
                    ItemSourceId.WARDROBE,
                    ItemLocation.Collection("Wardrobe", page, slot.id, index),
                    ItemNavigationAction.Collection(org.hypixelskyblockmods.storagelens.feature.itemsearch.CollectionType.WARDROBE, page, slot.id, index),
                    ItemDataOrigin.SKYBLOCK_API_PROFILE,
                    ownershipIdentity = if (slot.id == currentSlot) {
                        org.hypixelskyblockmods.storagelens.feature.itemsearch.equippedArmorOwnershipIdentity(index)
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun equipmentWardrobe(): List<SearchableItem> = profileSnapshot {
        EquipmentWardrobeAPI.slots.filterNot { it.locked }.flatMap { slot ->
            val page = (slot.id - 1) / 9 + 1
            slot.slots.mapIndexedNotNull { index, stack ->
                searchable(
                    stack,
                    stack.count.toLong(),
                    ItemSourceId.EQUIPMENT_WARDROBE,
                    ItemLocation.Collection("Equipment", page, slot.id, index),
                    ItemNavigationAction.Collection(org.hypixelskyblockmods.storagelens.feature.itemsearch.CollectionType.EQUIPMENT, page, slot.id, index),
                    ItemDataOrigin.SKYBLOCK_API_PROFILE,
                    contributesToTotals = false,
                )
            }
        }
    }

    private fun accessoryBag(): List<SearchableItem> = profileSnapshot {
        AccessoryBagAPI.getItems().mapNotNull { item ->
            searchable(
                item.item,
                item.item.count.toLong(),
                ItemSourceId.ACCESSORY_BAG,
                ItemLocation.Container("Accessory Bag", item.page, item.slot),
                ItemNavigationAction.Command("ab ${item.page}"),
                ItemDataOrigin.SKYBLOCK_API_PROFILE,
            )
        }
    }

    private fun sacks(): List<SearchableItem> = profileSnapshot {
        SacksAPI.sackItems.mapNotNull { (id, amount) ->
            if (amount <= 0) return@mapNotNull null
            val stack = SkyBlockId.item(id).toItem()
            searchable(
                stack,
                amount.toLong(),
                ItemSourceId.SACKS,
                ItemLocation.Generic("Sacks", "sacks:$id"),
                ItemNavigationAction.Command("sacks"),
                ItemDataOrigin.SKYBLOCK_API_PROFILE,
            )
        }
    }

    private fun vault(): List<SearchableItem> = profileSnapshot {
        VaultAPI.getItems().mapIndexedNotNull { index, stack ->
            searchable(
                stack,
                stack.count.toLong(),
                ItemSourceId.VAULT,
                ItemLocation.Container("Personal Vault", slot = index),
                ItemNavigationAction.Command("bank"),
                ItemDataOrigin.SKYBLOCK_API_PROFILE,
            )
        }
    }

    private fun forge(): List<SearchableItem> = profileSnapshot {
        ForgeAPI.getForgeSlots().mapNotNull { (index, slot) ->
            val stack = slot.skyBlockId.toItem()
            searchable(
                stack,
                1,
                ItemSourceId.FORGE,
                ItemLocation.Generic("Forge slot ${index + 1}", "forge:$index"),
                ItemNavigationAction.None,
                ItemDataOrigin.SKYBLOCK_API_PROFILE,
            )
        }
    }

    private fun museum(): List<SearchableItem> = profileSnapshot {
        MuseumAPI.getItemsWithCategory().flatMap { (category, items) ->
            items.mapIndexedNotNull { index, stack ->
                val id = stack.getData(DataTypes.ID)
                if (!category.isSpecial && (id == null || !MuseumAPI.isStoredInMuseum(SkyBlockId.item(id)))) {
                    return@mapIndexedNotNull null
                }
                searchable(
                    stack,
                    stack.count.toLong(),
                    ItemSourceId.MUSEUM,
                    ItemLocation.Container("Museum ${category.name.lowercase().replace('_', ' ')}", slot = index),
                    ItemNavigationAction.Command("warp museum"),
                    ItemDataOrigin.SKYBLOCK_API_PROFILE,
                )
            }
        }
    }

    private fun installedParts(items: List<SearchableItem>): List<SearchableItem> = items.flatMap { parent ->
        val stack = parent.stack
        val ids = buildList {
            stack.getData(DataTypes.FUEL_TANK)?.let(::add)
            stack.getData(DataTypes.ENGINE)?.let(::add)
            stack.getData(DataTypes.UPGRADE_MODULE)?.let(::add)
            stack.getData(DataTypes.SINKER)?.second?.let(::add)
            stack.getData(DataTypes.HOOK)?.second?.let(::add)
            stack.getData(DataTypes.LINE)?.second?.let(::add)
        }.distinct()
        ids.mapNotNull { id ->
            searchable(
                SkyBlockId.item(id).toItem(),
                1,
                ItemSourceId.INSTALLED_PARTS,
                parent.location,
                parent.action,
                ItemDataOrigin.DERIVED,
                parent.updatedAtEpochMillis,
            )
        }
    }

    private inline fun profileSnapshot(block: () -> List<SearchableItem>): List<SearchableItem> {
        val expected = SkyblockApiStorageAdapter.currentProfile() ?: return emptyList()
        val items = block()
        val actual = SkyblockApiStorageAdapter.currentProfile()
        return items.takeIf { actual != null && expected.accountUuid == actual.accountUuid && expected.profileName == actual.profileName }.orEmpty()
    }

    private fun Long.saturatedMultiply(amount: Long): Long = runCatching { Math.multiplyExact(this, amount) }.getOrDefault(Long.MAX_VALUE)
}
