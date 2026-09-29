package org.hypixelskyblockmods.storagelens.feature.itemsearch

import com.google.gson.GsonBuilder
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.storagelens.config.SkyHudConfigManager
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyBlockProfileIdentity
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyblockApiItemSearchAdapter
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyblockApiStorageAdapter
import org.hypixelskyblockmods.storagelens.util.BackgroundSave
import org.hypixelskyblockmods.storagelens.util.ItemStackSerialization
import org.slf4j.LoggerFactory

object IslandChestRepository {
    private const val OPEN_BIND_TICKS = 40L
    private const val SAVE_DEBOUNCE_MILLIS = 500L
    private const val WARP_HIGHLIGHT_TIMEOUT_MILLIS = 30_000L
    private val logger = LoggerFactory.getLogger("StorageLens Island Chests")

    private data class ProfileKey(val accountUuid: UUID, val profileName: String)
    private data class ChestKey(val positions: List<BlockPos>) {
        val identity: String = positions.joinToString(";") { "${it.x},${it.y},${it.z}" }
    }
    private data class ObservedItem(val slot: Int, val stack: ItemStack)
    private data class ChestSnapshot(
        val key: ChestKey,
        val updatedAtEpochMillis: Long,
        val items: List<ObservedItem>,
    )
    private data class PendingOpen(val key: ChestKey, val expiresAtTick: Long)
    private data class ActiveOpen(val key: ChestKey, val containerId: Int)
    private data class PendingHighlight(
        val profile: ProfileKey?,
        val containers: List<List<BlockPos>>,
        val expiresAtEpochMillis: Long,
    )

    private data class SavedPosition(var x: Int = 0, var y: Int = 0, var z: Int = 0)
    private data class SavedItem(var slot: Int = 0, var stack: String = "")
    private data class SavedChest(
        var positions: MutableList<SavedPosition> = mutableListOf(),
        var updatedAtEpochMillis: Long = 0,
        var items: MutableList<SavedItem> = mutableListOf(),
    )
    private data class SavedData(var schemaVersion: Int = 1, var chests: MutableList<SavedChest> = mutableListOf())

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val chests = linkedMapOf<String, ChestSnapshot>()
    private val persistence = BackgroundSave()
    private var loaded = false
    private var loadedProfile: ProfileKey? = null
    private var activeIdentity: SkyBlockProfileIdentity? = null
    private var saveAfterEpochMillis: Long? = null
    private var tick = 0L
    private var pendingOpen: PendingOpen? = null
    private var activeOpen: ActiveOpen? = null
    private var transientUnknownKey: ChestKey? = null
    private var transientUnknownUntilTick = 0L
    private var activeHighlight: PendingHighlight? = null
    private var waitingForIslandHighlight: PendingHighlight? = null

    fun initializeSource() {
        ItemSourceRegistry.register(ItemSourceId.ISLAND_CHESTS, ::snapshot)
    }

    fun onClientTick() {
        tick++
        refreshActiveContainer()
        if (pendingOpen?.expiresAtTick?.let { tick > it } == true) pendingOpen = null
        if (tick > transientUnknownUntilTick) {
            transientUnknownKey?.let { if (activeIdentity == null && activeOpen == null) chests.remove(it.identity) }
            transientUnknownKey = null
        }
        val now = System.currentTimeMillis()
        val currentProfile = SkyblockApiStorageAdapter.currentProfile()?.let { ProfileKey(it.accountUuid, it.profileName) }
        if (activeHighlight?.profile?.let { it != currentProfile } == true) activeHighlight = null
        if (waitingForIslandHighlight?.profile != currentProfile) waitingForIslandHighlight = null
        if (activeHighlight?.expiresAtEpochMillis?.let { now > it } == true) activeHighlight = null
        waitingForIslandHighlight?.let { waiting ->
            if (now > waiting.expiresAtEpochMillis) {
                waitingForIslandHighlight = null
            } else if (SkyblockApiItemSearchAdapter.isOwnPrivateIsland()) {
                activeHighlight = PendingHighlight(waiting.profile, waiting.containers, now + highlightDurationMillis())
                waitingForIslandHighlight = null
            }
        }
        saveAfterEpochMillis?.let { if (now >= it) saveNow() }
        if (saveAfterEpochMillis == null && persistence.needsSave) saveAfterEpochMillis = now + SAVE_DEBOUNCE_MILLIS
    }

    fun observeChestRightClick(positions: List<BlockPos>) {
        if (!SkyblockApiItemSearchAdapter.isOwnPrivateIsland()) return
        val key = chestKey(positions) ?: return
        pendingOpen = PendingOpen(key, tick + OPEN_BIND_TICKS)
    }

    fun initializeContainer(containerId: Int, items: List<ItemStack>) {
        ensureLoaded()
        val pending = pendingOpen ?: return
        if (tick > pending.expiresAtTick) {
            pendingOpen = null
            return
        }
        val expectedSlots = pending.key.positions.size * 27
        if (items.size != expectedSlots) return
        pendingOpen = null
        activeOpen = ActiveOpen(pending.key, containerId)
        capture(pending.key, items)
    }

    fun onScreenOpened(screen: Screen) {
        val container = screen as? AbstractContainerScreen<*> ?: return
        val menu = container.menu as? ChestMenu ?: return
        initializeContainer(menu.containerId, menu.items.take(menu.rowCount * 9))
    }

    fun onContainerChanged(containerId: Int, items: List<ItemStack>) {
        val active = activeOpen?.takeIf { it.containerId == containerId } ?: return
        val expectedSlots = active.key.positions.size * 27
        if (items.size != expectedSlots) return
        capture(active.key, items)
    }

    fun onContainerClosed() {
        if (activeIdentity == null) {
            transientUnknownKey = activeOpen?.key
            transientUnknownUntilTick = tick + OPEN_BIND_TICKS
        }
        activeOpen = null
        pendingOpen = null
    }

    fun onBlockChanged(pos: BlockPos, remainsChest: Boolean) {
        if (remainsChest) return
        ensureLoaded()
        val removedIdentities = IslandChestGeometry.containersRemovedByBreak(
            chests.mapValues { it.value.key.positions },
            pos,
        )
        val removed = removedIdentities.mapNotNull(chests::get)
        if (removed.isEmpty()) return
        removed.forEach { chests.remove(it.key.identity) }
        if (activeOpen?.key?.positions?.any { it == pos } == true) activeOpen = null
        scheduleSave()
    }

    fun highlight(positions: List<BlockPos>, afterIslandWarp: Boolean = false) {
        highlightAll(listOf(positions), afterIslandWarp)
    }

    fun highlightAll(containers: List<List<BlockPos>>, afterIslandWarp: Boolean = false) {
        val canonical = IslandChestGeometry.distinctContainers(containers)
        if (canonical.isEmpty()) return
        val identity = SkyblockApiStorageAdapter.currentProfile()
        val profile = identity?.let { ProfileKey(it.accountUuid, it.profileName) }
        val now = System.currentTimeMillis()
        if (afterIslandWarp) {
            if (profile == null) return
            waitingForIslandHighlight = PendingHighlight(profile, canonical, now + WARP_HIGHLIGHT_TIMEOUT_MILLIS)
            activeHighlight = null
        } else {
            activeHighlight = PendingHighlight(profile, canonical, now + highlightDurationMillis())
            waitingForIslandHighlight = null
        }
    }

    fun highlightedContainers(): List<List<BlockPos>> = activeHighlight
        ?.takeIf { System.currentTimeMillis() <= it.expiresAtEpochMillis }
        ?.containers
        ?.map { container -> container.map(BlockPos::immutable) }
        .orEmpty()

    private fun highlightDurationMillis(): Long =
        SkyHudConfigManager.config.itemSearch.highlightDurationSeconds.coerceIn(1, 60) * 1_000L

    fun clearCurrentProfile() {
        val profile = SkyblockApiStorageAdapter.currentProfile() ?: return
        persistence.reset()
        chests.clear()
        activeOpen = null
        pendingOpen = null
        transientUnknownKey = null
        saveAfterEpochMillis = null
        SkyBlockProfileStore.clear("island-chests", profile)
    }

    fun resetSession() {
        saveNow()
        persistence.reset()
        loaded = false
        loadedProfile = null
        activeIdentity = null
        saveAfterEpochMillis = null
        chests.clear()
        pendingOpen = null
        activeOpen = null
        transientUnknownKey = null
        activeHighlight = null
        waitingForIslandHighlight = null
    }

    fun flush() {
        saveNow()
        persistence.awaitIdle()
    }

    internal fun canonicalPositions(positions: List<BlockPos>): List<BlockPos> = IslandChestGeometry.canonical(positions)

    private fun snapshot(): List<SearchableItem> {
        ensureLoaded()
        if (!SkyblockApiItemSearchAdapter.isOnSkyBlock()) return emptyList()
        val visible = if (activeIdentity == null) {
            val key = activeOpen?.key ?: transientUnknownKey
            key?.let { chests[it.identity]?.let(::listOf) }.orEmpty()
        } else {
            chests.values.toList()
        }
        val activeKey = activeOpen?.key?.identity
        val liveKey = activeKey ?: transientUnknownKey?.identity
        return visible.flatMap { chest ->
            chest.items.mapNotNull { item ->
                SkyblockApiItemSearchAdapter.searchable(
                    item.stack,
                    item.stack.count.toLong(),
                    ItemSourceId.ISLAND_CHESTS,
                    ItemLocation.IslandChest(chest.key.positions, item.slot),
                    ItemNavigationAction.IslandChest(chest.key.positions),
                    if (chest.key.identity == liveKey) ItemDataOrigin.LIVE_MENU else ItemDataOrigin.LOCAL_OBSERVATION,
                    chest.updatedAtEpochMillis,
                )
            }
        }
    }

    private fun ensureLoaded() {
        val identity = SkyblockApiStorageAdapter.currentProfile()
        val key = identity?.let { ProfileKey(it.accountUuid, it.profileName) }
        if (loaded && loadedProfile == key) return
        saveNow()
        persistence.reset()
        saveAfterEpochMillis = null
        loaded = true
        loadedProfile = key
        activeIdentity = identity
        chests.clear()
        val json = identity?.let { SkyBlockProfileStore.read("island-chests", it) } ?: return
        runCatching {
            gson.fromJson(json, SavedData::class.java).chests.forEach { saved ->
                val keyForChest = chestKey(saved.positions.map { BlockPos(it.x, it.y, it.z) }) ?: return@forEach
                chests[keyForChest.identity] = ChestSnapshot(
                    keyForChest,
                    saved.updatedAtEpochMillis,
                    saved.items.mapNotNull { item ->
                        ItemStackSerialization.decode(item.stack).takeUnless(ItemStack::isEmpty)?.let { ObservedItem(item.slot, it) }
                    },
                )
            }
        }
    }

    private fun capture(key: ChestKey, stacks: List<ItemStack>) {
        val previous = chests[key.identity]
        if (previous != null && observedMatches(previous.items, stacks)) return
        val items = stacks.mapIndexedNotNull { slot, stack -> stack.takeUnless(ItemStack::isEmpty)?.copy()?.let { ObservedItem(slot, it) } }
        chests[key.identity] = ChestSnapshot(key, System.currentTimeMillis(), items)
        if (previous == null) logger.info("Remembered island chest ${key.identity} with ${items.size} occupied slots")
        scheduleSave()
    }

    private fun refreshActiveContainer() {
        val active = activeOpen ?: return
        val menu = Minecraft.getInstance().player?.containerMenu as? ChestMenu
        if (menu == null || menu.containerId != active.containerId) {
            onContainerClosed()
            return
        }
        onContainerChanged(active.containerId, menu.items.take(menu.rowCount * 9))
    }

    private fun scheduleSave() {
        if (activeIdentity != null) {
            persistence.markDirty()
            saveAfterEpochMillis = System.currentTimeMillis() + SAVE_DEBOUNCE_MILLIS
        }
    }

    private fun saveNow() {
        saveAfterEpochMillis = null
        val profile = activeIdentity ?: return
        persistence.submit {
            // Repository snapshots own their stacks and are replaced, never mutated.
            val snapshot = chests.values.toList()
            val ops = ItemStackSerialization.registryOps()
            val write: () -> Boolean = {
                val saved = SavedData(chests = snapshot.map { chest ->
                    SavedChest(
                        chest.key.positions.map { SavedPosition(it.x, it.y, it.z) }.toMutableList(),
                        chest.updatedAtEpochMillis,
                        chest.items.map { SavedItem(it.slot, ItemStackSerialization.encode(it.stack, ops)) }.toMutableList(),
                    )
                }.toMutableList())
                SkyBlockProfileStore.write("island-chests", profile, gson.toJson(saved))
            }
            write
        }
    }

    private fun chestKey(positions: List<BlockPos>): ChestKey? {
        val canonical = canonicalPositions(positions)
        return canonical.takeIf { it.size in 1..2 }?.let(::ChestKey)
    }

    private fun observedMatches(first: List<ObservedItem>, stacks: List<ItemStack>): Boolean =
        first.size == stacks.count { !it.isEmpty } && first.all { item ->
            stacks.getOrNull(item.slot)?.let { ItemStack.matches(item.stack, it) } == true
        }
}

internal object IslandChestGeometry {
    fun canonical(positions: List<BlockPos>): List<BlockPos> = positions
        .distinct()
        .sortedWith(compareBy<BlockPos>({ it.x }, { it.y }, { it.z }))
        .map(BlockPos::immutable)

    fun distinctContainers(containers: List<List<BlockPos>>): List<List<BlockPos>> = containers
        .map(::canonical)
        .filter { it.size in 1..2 }
        .distinctBy { positions -> positions.joinToString(";") { "${it.x},${it.y},${it.z}" } }

    fun containersRemovedByBreak(containers: Map<String, List<BlockPos>>, broken: BlockPos): Set<String> =
        containers.filterValues { positions -> positions.any { it == broken } }.keys
}
