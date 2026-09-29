package org.hypixelskyblockmods.storagelens.feature.storage

import com.google.gson.GsonBuilder
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.storagelens.feature.itemsearch.ItemDataOrigin
import org.hypixelskyblockmods.storagelens.feature.itemsearch.SkyBlockProfileStore
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyBlockProfileIdentity
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyblockApiStorageAdapter
import org.hypixelskyblockmods.storagelens.util.BackgroundSave
import org.hypixelskyblockmods.storagelens.util.ItemStackSerialization
import org.slf4j.LoggerFactory

data class ObservedStoragePage(
    val key: StoragePageKey,
    val items: List<ItemStack>,
    val updatedAtEpochMillis: Long,
    val origin: ItemDataOrigin,
)

object ObservedStorageRepository {
    private const val CACHE_NAME = "storage-pages"
    private const val SAVE_DEBOUNCE_MILLIS = 500L
    private const val TIMESTAMP_REFRESH_MILLIS = 60_000L

    private data class ProfileKey(val accountUuid: UUID, val profileName: String)
    private data class SavedItem(var index: Int = 0, var stack: String = "")
    private data class SavedPage(
        var type: String = "",
        var number: Int = 0,
        var rows: Int = 0,
        var updatedAtEpochMillis: Long = 0,
        var items: MutableList<SavedItem> = mutableListOf(),
    )
    private data class SavedStoragePages(
        var schemaVersion: Int = 1,
        var pages: MutableList<SavedPage> = mutableListOf(),
    )

    private val logger = LoggerFactory.getLogger("StorageLens Storage Observation")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val pages = sortedMapOf<StoragePageKey, ObservedStoragePage>()
    private val persistence = BackgroundSave()

    private var loadedProfile: ProfileKey? = null
    private var activeIdentity: SkyBlockProfileIdentity? = null
    private var saveAfterEpochMillis: Long? = null
    private var livePageKey: StoragePageKey? = null
    private var liveMenu: ChestMenu? = null

    fun observe(title: String, menu: ChestMenu, menuItems: List<ItemStack>? = null) {
        val target = StorageMenuDetection.detect(title, menu) ?: return
        ensureProfileState()
        captureLivePage()
        when (target) {
            is StorageMenuTarget.Overview -> clearLiveBacking()
            is StorageMenuTarget.Page -> {
                livePageKey = target.key
                liveMenu = target.menu
                if (menuItems == null) {
                    captureLivePage()
                } else {
                    capturePage(target.key, menu.rowCount, menuItems)
                }
            }
        }
    }

    fun snapshot(): List<ObservedStoragePage> {
        ensureProfileState()
        captureLivePage()
        val live = liveMenu?.takeIf(::isCurrentMenu)?.let { livePageKey }
        return pages.values.map { page ->
            page.copy(
                items = page.items.map(ItemStack::copy),
                origin = if (page.key == live) ItemDataOrigin.LIVE_MENU else ItemDataOrigin.LOCAL_OBSERVATION,
            )
        }
    }

    fun onClientTick() {
        ensureProfileState()
        val menu = liveMenu
        if (menu != null && isCurrentMenu(menu)) {
            captureLivePage()
        } else if (menu != null) {
            captureLivePage()
            clearLiveBacking()
        }
        val now = System.currentTimeMillis()
        saveAfterEpochMillis?.let { if (now >= it) saveNow() }
        if (saveAfterEpochMillis == null && persistence.needsSave) saveAfterEpochMillis = now + SAVE_DEBOUNCE_MILLIS
    }

    fun onContainerClosed() {
        captureLivePage()
        clearLiveBacking()
        if (activeIdentity == null) pages.clear()
    }

    fun resetSession() {
        captureLivePage()
        saveNow()
        persistence.reset()
        pages.clear()
        livePageKey = null
        liveMenu = null
        loadedProfile = null
        activeIdentity = null
        saveAfterEpochMillis = null
    }

    fun flush() {
        captureLivePage()
        saveNow()
        persistence.awaitIdle()
    }

    fun clearCurrentProfile() {
        val profile = SkyblockApiStorageAdapter.currentProfile() ?: return
        persistence.reset()
        pages.clear()
        loadedProfile = profile.toProfileKey()
        activeIdentity = profile
        saveAfterEpochMillis = null
        SkyBlockProfileStore.clear(CACHE_NAME, profile)
        if (liveMenu?.let(::isCurrentMenu) == true) captureLivePage()
    }

    private fun ensureProfileState() {
        val identity = SkyblockApiStorageAdapter.currentProfile()
        val profile = identity?.toProfileKey()
        if (loadedProfile == profile) {
            activeIdentity = identity
            return
        }

        val carryUnknownLivePage = loadedProfile == null && profile != null
        val previousLiveKey = livePageKey
        val previousLiveMenu = liveMenu
        captureLivePage()
        saveNow()
        persistence.reset()
        pages.clear()
        livePageKey = null
        liveMenu = null
        loadedProfile = profile
        activeIdentity = identity
        saveAfterEpochMillis = null
        if (identity != null) load(identity)
        if (carryUnknownLivePage && previousLiveKey != null && previousLiveMenu != null && isCurrentMenu(previousLiveMenu)) {
            livePageKey = previousLiveKey
            liveMenu = previousLiveMenu
            captureLivePage()
        }
    }

    private fun load(profile: SkyBlockProfileIdentity) {
        val json = SkyBlockProfileStore.read(CACHE_NAME, profile) ?: return
        runCatching {
            val saved = gson.fromJson(json, SavedStoragePages::class.java)
            if (saved.schemaVersion != 1) return@runCatching
            saved.pages.forEach { page ->
                val type = runCatching { StoragePageType.valueOf(page.type) }.getOrNull() ?: return@forEach
                val validNumbers = if (type == StoragePageType.ENDER_CHEST) 1..9 else 1..18
                if (page.number !in validNumbers || page.rows !in 1..5 || page.updatedAtEpochMillis <= 0) return@forEach
                val items = MutableList(page.rows * 9) { ItemStack.EMPTY }
                page.items.forEach { item ->
                    if (item.index in items.indices) items[item.index] = ItemStackSerialization.decode(item.stack)
                }
                val key = StoragePageKey(type, page.number)
                pages[key] = ObservedStoragePage(
                    key,
                    items,
                    page.updatedAtEpochMillis,
                    ItemDataOrigin.LOCAL_OBSERVATION,
                )
            }
        }.onFailure {
            logger.warn("Could not load observed Storage pages for ${profile.profileName}", it)
        }
    }

    private fun captureLivePage() {
        val key = livePageKey ?: return
        val menu = liveMenu ?: return
        capturePage(key, menu.rowCount, menu.items)
    }

    private fun capturePage(key: StoragePageKey, rowCount: Int, menuItems: List<ItemStack>) {
        val now = System.currentTimeMillis()
        val previous = pages[key]
        val refreshTimestamp = previous?.updatedAtEpochMillis?.let { now - it >= TIMESTAMP_REFRESH_MILLIS } != false
        val slots = storagePageSlotRange(rowCount)
        if (previous != null && previous.items.size == slots.count() && !refreshTimestamp &&
            slots.withIndex().all { (index, slot) ->
                menuItems.getOrNull(slot)?.let { ItemStack.matches(previous.items[index], it) } == true
            }
        ) return
        val observed = ObservedStoragePage(
            key = key,
            items = storagePageItems(menuItems, rowCount),
            updatedAtEpochMillis = now,
            origin = ItemDataOrigin.LOCAL_OBSERVATION,
        )
        pages[key] = observed
        if (activeIdentity != null) {
            persistence.markDirty()
            saveAfterEpochMillis = now + SAVE_DEBOUNCE_MILLIS
        }
    }

    private fun clearLiveBacking() {
        livePageKey = null
        liveMenu = null
    }

    private fun saveNow() {
        saveAfterEpochMillis = null
        val profile = activeIdentity ?: return
        persistence.submit {
            // Capture owned, immutable observations and registry access before leaving the game thread.
            val snapshot = pages.values.toList()
            val ops = ItemStackSerialization.registryOps()
            val write: () -> Boolean = {
                val saved = SavedStoragePages(pages = snapshot.map { page ->
                    SavedPage(
                        type = page.key.type.name,
                        number = page.key.number,
                        rows = page.items.size / 9,
                        updatedAtEpochMillis = page.updatedAtEpochMillis,
                        items = page.items.mapIndexedNotNull { index, stack ->
                            stack.takeUnless(ItemStack::isEmpty)
                                ?.let { ItemStackSerialization.encode(it, ops) }
                                ?.takeIf(String::isNotBlank)
                                ?.let { SavedItem(index, it) }
                        }.toMutableList(),
                    )
                }.toMutableList())
                SkyBlockProfileStore.write(CACHE_NAME, profile, gson.toJson(saved))
            }
            write
        }
    }

    private fun isCurrentMenu(menu: ChestMenu): Boolean = Minecraft.getInstance().player?.containerMenu === menu

    private fun SkyBlockProfileIdentity.toProfileKey() = ProfileKey(accountUuid, profileName)
}

internal fun storagePageItems(menuItems: List<ItemStack>, rowCount: Int): List<ItemStack> =
    storagePageSlotRange(rowCount).mapNotNull(menuItems::getOrNull).map(ItemStack::copy)

internal fun storagePageSlotRange(rowCount: Int): IntRange = 9 until rowCount.coerceAtLeast(1) * 9
