package org.hypixelskyblockmods.storagelens

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.phys.AABB
import org.hypixelskyblockmods.storagelens.config.SkyHudConfigManager
import org.hypixelskyblockmods.storagelens.feature.equipment.EquipmentDetector
import org.hypixelskyblockmods.storagelens.feature.equipment.EquipmentRepository
import org.hypixelskyblockmods.storagelens.feature.itemsearch.IslandChestRepository
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ItemSearchController
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ItemSearchDataManager
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ItemSearchKeyMapping
import org.hypixelskyblockmods.storagelens.feature.itemsearch.PlayerInventorySearchRepository
import org.hypixelskyblockmods.storagelens.feature.itemsearch.SackOfSacksRepository
import org.hypixelskyblockmods.storagelens.feature.loadouts.LoadoutDetector
import org.hypixelskyblockmods.storagelens.feature.loadouts.LoadoutRepository
import org.hypixelskyblockmods.storagelens.feature.wardrobe.WardrobeDetector
import org.hypixelskyblockmods.storagelens.feature.wardrobe.WardrobeRepository
import org.hypixelskyblockmods.storagelens.gui.SkyHudBackdrop
import org.hypixelskyblockmods.storagelens.gui.SkyHudTheme
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyblockApiIntegration
import org.slf4j.LoggerFactory

object StorageLensClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("StorageLens")

    override fun onInitializeClient() {
        SkyHudConfigManager.initialize()
        ItemSearchKeyMapping.initialize()
        SkyblockApiIntegration.initialize()
        registerCommands()
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            IslandChestRepository.onScreenOpened(screen)
            LoadoutDetector.detect(screen)?.let { LoadoutRepository.remember(it.page, it.menu) }
            WardrobeDetector.detect(screen)?.let { WardrobeRepository.sets.remember(it.page, it.menu) }
            EquipmentDetector.detect(screen)?.let { EquipmentRepository.sets.remember(it.page, it.menu) }
        })
        ClientTickEvents.END_CLIENT_TICK.register { PlayerInventorySearchRepository.onClientTick() }
        ClientTickEvents.END_CLIENT_TICK.register { IslandChestRepository.onClientTick() }
        ClientTickEvents.END_CLIENT_TICK.register { SackOfSacksRepository.onClientTick() }
        ClientTickEvents.END_CLIENT_TICK.register { LoadoutRepository.onClientTick() }
        ClientTickEvents.END_CLIENT_TICK.register { WardrobeRepository.sets.onClientTick() }
        ClientTickEvents.END_CLIENT_TICK.register { EquipmentRepository.sets.onClientTick() }
        ClientTickEvents.END_CLIENT_TICK.register(ItemSearchController::onClientTick)
        LevelRenderEvents.BEFORE_GIZMOS.register {
            val accent = SkyHudTheme.PRIMARY and 0x00FFFFFF
            val style = GizmoStyle.strokeAndFill(0xFF000000.toInt() or accent, 2.0f, 0x30000000 or accent)
            IslandChestRepository.highlightedContainers().forEach { positions ->
                val minX = positions.minOf { it.x }.toDouble()
                val minY = positions.minOf { it.y }.toDouble()
                val minZ = positions.minOf { it.z }.toDouble()
                val maxX = positions.maxOf { it.x }.toDouble() + 1.0
                val maxY = positions.maxOf { it.y }.toDouble() + 1.0
                val maxZ = positions.maxOf { it.z }.toDouble() + 1.0
                Gizmos.cuboid(AABB(minX, minY, minZ, maxX, maxY, maxZ), style).setAlwaysOnTop()
            }
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            SkyHudConfigManager.save()
            PlayerInventorySearchRepository.flush()
            IslandChestRepository.flush()
            SackOfSacksRepository.flush()
            LoadoutRepository.flush()
            WardrobeRepository.sets.flush()
            EquipmentRepository.sets.flush()
            SkyHudBackdrop.close()
        }
        logger.info("StorageLens initialized")
    }

    private fun registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("storagelens")
                    .executes {
                        Minecraft.getInstance().execute(SkyHudConfigManager::open)
                        1
                    }
                    .then(
                        ClientCommands.literal("search")
                            .executes {
                                Minecraft.getInstance().execute { ItemSearchController.open() }
                                1
                            }
                            .then(
                                ClientCommands.literal("reset-island-chests").executes {
                                    Minecraft.getInstance().execute(ItemSearchDataManager::clearIslandChests)
                                    1
                                },
                            )
                            .then(
                                ClientCommands.argument("query", StringArgumentType.greedyString()).executes { context ->
                                    val query = StringArgumentType.getString(context, "query")
                                    Minecraft.getInstance().execute { ItemSearchController.open(query) }
                                    1
                                },
                            ),
                    ),
            )
        }
    }
}
