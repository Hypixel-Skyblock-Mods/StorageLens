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

    private var loadedProfile: ProfileKey? = null
    private var activeIdentity: SkyBlockProfileIdentity? = null
    private var lastSavedJson: String? = null
    private var saveAfterEpochMillis: Long? = null
    private var livePageKey: StoragePageKey? = null
    private var liveMenu: ChestMenu? = null

    fun observe(title: String, menu: ChestMenu) {
        val target = StorageMenuDetection.detect(title, menu) ?: return
        ensureProfileState()
        captureLivePage()
        when (target) {
            is StorageMenuTarget.Overview -> clearLiveBacking()
            is StorageMenuTarget.Page -> {
                livePageKey = target.key
                liveMenu = target.menu
                captureLivePage()
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
        saveAfterEpochMillis?.let { if (System.currentTimeMillis() >= it) saveNow() }
    }

    fun onContainerClosed() {
        captureLivePage()
        clearLiveBacking()
        if (activeIdentity == null) pages.clear()
        saveNow()
    }

    fun resetSession() {
        captureLivePage()
        saveNow()
        pages.clear()
        livePageKey = null
        liveMenu = null
        loadedProfile = null
        activeIdentity = null
        lastSavedJson = null
        saveAfterEpochMillis = null
    }

    fun flush() {
        captureLivePage()
        saveNow()
    }

    fun clearCurrentProfile() {
        val profile = SkyblockApiStorageAdapter.currentProfile() ?: return
        pages.clear()
        loadedProfile = profile.toProfileKey()
        activeIdentity = profile
        lastSavedJson = null
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
        pages.clear()
        livePageKey = null
        liveMenu = null
        loadedProfile = profile
        activeIdentity = identity
        lastSavedJson = null
        saveAfterEpochMillis = null
        if (identity != null) load(identity)
        if (carryUnknownLivePage && previousLiveKey != null && previousLiveMenu != null && isCurrentMenu(previousLiveMenu)) {
            livePageKey = previousLiveKey
            liveMenu = previousLiveMenu
            captureLivePage()
        }
    }

    private fun load(profile: SkyBlockProfileIdentity) {
        lastSavedJson = SkyBlockProfileStore.read(CACHE_NAME, profile)
        val json = lastSavedJson ?: return
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
        val now = System.currentTimeMillis()
        val observed = ObservedStoragePage(
            key = key,
            items = menu.items.take(menu.rowCount * 9).drop(9).map(ItemStack::copy),
            updatedAtEpochMillis = now,
            origin = ItemDataOrigin.LOCAL_OBSERVATION,
        )
        val previous = pages[key]
        val refreshTimestamp = previous?.updatedAtEpochMillis?.let { now - it >= TIMESTAMP_REFRESH_MILLIS } != false
        if (pagesMatch(previous, observed) && !refreshTimestamp) return
        pages[key] = observed
        if (activeIdentity != null) saveAfterEpochMillis = now + SAVE_DEBOUNCE_MILLIS
    }

    private fun clearLiveBacking() {
        livePageKey = null
        liveMenu = null
    }

    private fun saveNow() {
        saveAfterEpochMillis = null
        val profile = activeIdentity ?: return
        val saved = SavedStoragePages(pages = pages.values.map { page ->
            SavedPage(
                type = page.key.type.name,
                number = page.key.number,
                rows = page.items.size / 9,
                updatedAtEpochMillis = page.updatedAtEpochMillis,
                items = page.items.mapIndexedNotNull { index, stack ->
                    stack.takeUnless(ItemStack::isEmpty)
                        ?.let(ItemStackSerialization::encode)
                        ?.takeIf(String::isNotBlank)
                        ?.let { SavedItem(index, it) }
                }.toMutableList(),
            )
        }.toMutableList())
        val json = gson.toJson(saved)
        if (json == lastSavedJson) return
        if (SkyBlockProfileStore.write(CACHE_NAME, profile, json)) lastSavedJson = json
    }

    private fun pagesMatch(first: ObservedStoragePage?, second: ObservedStoragePage): Boolean =
        first != null && ItemStackSerialization.stacksMatch(first.items, second.items)

    private fun isCurrentMenu(menu: ChestMenu): Boolean = Minecraft.getInstance().player?.containerMenu === menu

    private fun SkyBlockProfileIdentity.toProfileKey() = ProfileKey(accountUuid, profileName)
}
