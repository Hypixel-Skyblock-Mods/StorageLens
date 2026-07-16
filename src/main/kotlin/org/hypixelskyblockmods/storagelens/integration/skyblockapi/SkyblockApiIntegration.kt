package org.hypixelskyblockmods.storagelens.integration.skyblockapi

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ChestMenu
import org.hypixelskyblockmods.storagelens.feature.equipment.EquipmentRepository
import org.hypixelskyblockmods.storagelens.feature.itemsearch.IslandChestRepository
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ItemSearchController
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ItemSourceRegistry
import org.hypixelskyblockmods.storagelens.feature.itemsearch.PlayerInventorySearchRepository
import org.hypixelskyblockmods.storagelens.feature.itemsearch.SackOfSacksRepository
import org.hypixelskyblockmods.storagelens.feature.itemsearch.SkyHudRepositoryItemSources
import org.hypixelskyblockmods.storagelens.feature.loadouts.LoadoutRepository
import org.hypixelskyblockmods.storagelens.feature.wardrobe.WardrobeRepository
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI
import tech.thatgravyboat.skyblockapi.api.events.level.BlockChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.level.RightClickBlockEvent
import tech.thatgravyboat.skyblockapi.api.events.profile.ProfileChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerCloseEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerInitializedEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.InventoryChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.PlayerInventoryChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.ScreenKeyPressedEvent

object SkyblockApiIntegration {
    fun initialize() {
        ItemSourceRegistry.clear()
        SkyblockApiItemSearchAdapter.initializeSources()
        SkyHudRepositoryItemSources.initialize()
        PlayerInventorySearchRepository.initializeSource()
        SackOfSacksRepository.initializeSource()
        IslandChestRepository.initializeSource()
        SkyBlockAPI.eventBus.register<PlayerInventoryChangeEvent> {
            Minecraft.getInstance().execute(PlayerInventorySearchRepository::onInventoryChanged)
        }
        SkyBlockAPI.eventBus.register<InventoryChangeEvent> { event ->
            if (!event.title.equals("Sack of Sacks", ignoreCase = true)) return@register
            val items = event.inventory
                .filterNot { it.container is Inventory }
                .mapNotNull { slot ->
                    slot.item.takeUnless { it.isEmpty || !SkyblockApiItemSearchAdapter.hasSkyBlockId(it) }
                        ?.let { slot.index to it.copy() }
                }
            Minecraft.getInstance().execute { SackOfSacksRepository.remember(items) }
        }
        SkyBlockAPI.eventBus.register<RightClickBlockEvent> { event ->
            val positions = SkyblockApiItemSearchAdapter.chestPositions(event.pos) ?: return@register
            Minecraft.getInstance().execute { IslandChestRepository.observeChestRightClick(positions) }
        }
        SkyBlockAPI.eventBus.register<ContainerInitializedEvent> { event ->
            val menu = event.screen.menu as? ChestMenu ?: return@register
            val items = event.containerItems.map(net.minecraft.world.item.ItemStack::copy)
            Minecraft.getInstance().execute { IslandChestRepository.initializeContainer(menu.containerId, items) }
        }
        SkyBlockAPI.eventBus.register<InventoryChangeEvent> { event ->
            val menu = event.screen.menu as? ChestMenu ?: return@register
            if (event.isInPlayerInventory) return@register
            val items = event.inventory
                .filterNot { it.container is Inventory }
                .map { it.item.copy() }
            Minecraft.getInstance().execute { IslandChestRepository.onContainerChanged(menu.containerId, items) }
        }
        SkyBlockAPI.eventBus.register<ContainerCloseEvent> {
            Minecraft.getInstance().execute(IslandChestRepository::onContainerClosed)
        }
        SkyBlockAPI.eventBus.register<BlockChangeEvent> { event ->
            val remainsChest = SkyblockApiItemSearchAdapter.isChest(event.state)
            Minecraft.getInstance().execute { IslandChestRepository.onBlockChanged(event.pos, remainsChest) }
        }
        SkyBlockAPI.eventBus.register<ScreenKeyPressedEvent.Pre> { event ->
            if (ItemSearchController.onScreenKeyPressed(event.screen, event.key)) event.cancel()
        }
        SkyBlockAPI.eventBus.register<ProfileChangeEvent> {
            Minecraft.getInstance().execute {
                ItemSearchController.onProfileChanged()
                PlayerInventorySearchRepository.resetSession()
                SackOfSacksRepository.resetSession()
                IslandChestRepository.resetSession()
                LoadoutRepository.resetSession()
                WardrobeRepository.sets.resetSession()
                EquipmentRepository.sets.resetSession()
            }
        }
    }
}
