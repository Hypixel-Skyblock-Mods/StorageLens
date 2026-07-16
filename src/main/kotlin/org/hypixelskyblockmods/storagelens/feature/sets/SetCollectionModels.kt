package org.hypixelskyblockmods.storagelens.feature.sets

import com.google.gson.GsonBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.storagelens.feature.itemsearch.SkyBlockProfileStore
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyBlockProfileIdentity
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyblockApiStorageAdapter
import org.hypixelskyblockmods.storagelens.util.ItemStackSerialization
import org.hypixelskyblockmods.storagelens.util.VanillaItemIds
import org.slf4j.LoggerFactory

data class SetCollectionTarget(
    val page: Int,
    val totalPages: Int,
    val menu: ChestMenu,
)

data class CachedSetSlot(
    val page: Int,
    val index: Int,
    val id: Int,
    val items: List<ItemStack>,
    val selector: ItemStack,
    val selected: Boolean,
    val locked: Boolean,
    val selectable: Boolean,
    val updatedAtEpochMillis: Long?,
)

data class SetCard(
    val page: Int,
    val index: Int,
    val id: Int,
    val set: CachedSetSlot?,
)

data class CachedSetPage(
    val page: Int,
    val slots: List<CachedSetSlot>,
)

class SetCollectionRepository(
    private val cacheName: String,
) {
    private data class SavedIndexedItem(var index: Int = 0, var stack: String = "")

    private data class SavedSet(
        var page: Int = 1,
        var index: Int = 0,
        var id: Int = 1,
        var items: MutableList<SavedIndexedItem> = mutableListOf(),
        var selector: String = "",
        var selected: Boolean = false,
        var locked: Boolean = false,
        var selectable: Boolean = false,
        var updatedAtEpochMillis: Long? = null,
    )

    private data class SavedSetCache(
        var schemaVersion: Int = 1,
        var sets: MutableList<SavedSet> = mutableListOf(),
    )

    private val logger = LoggerFactory.getLogger("StorageLens ${cacheName.replaceFirstChar(Char::uppercase)} Cache")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val pages = sortedMapOf<Int, CachedSetPage>()
    private val equippedPattern = Regex("^Slot [0-9]+: Equipped$", RegexOption.IGNORE_CASE)
    private data class ProfileKey(val accountUuid: java.util.UUID, val profileName: String)

    private var loadedProfile: ProfileKey? = null
    private var activeIdentity: SkyBlockProfileIdentity? = null
    private var lastSavedJson: String? = null
    private var saveAfterEpochMillis: Long? = null

    fun remember(page: Int, menu: ChestMenu) {
        ensureLoaded()
        if ((36..44).any { menu.getSlot(it).item.isEmpty }) return
        val observedAt = System.currentTimeMillis()
        val slots = (0 until 9).map { column ->
            val items = (0 until 4).map { row ->
                menu.getSlot(row * 9 + column).item
                    .takeUnless(VanillaItemIds::isGlassPane)
                    ?.copy()
                    ?: ItemStack.EMPTY
            }
            val selector = menu.getSlot(36 + column).item.copy()
            val selected = VanillaItemIds.isItem(selector, "lime_dye") ||
                equippedPattern.matches(selector.hoverName.string)
            val locked = VanillaItemIds.isItem(selector, "red_dye")

            CachedSetSlot(
                page = page,
                index = column,
                id = (page - 1) * 9 + column + 1,
                items = items,
                selector = selector,
                selected = selected,
                locked = locked,
                selectable = selected || (!locked && items.any { !it.isEmpty }),
                updatedAtEpochMillis = observedAt,
            )
        }
        val cachedPage = CachedSetPage(page, slots)
        val previousPage = pages[page]
        val refreshTimestamp = previousPage?.slots?.mapNotNull(CachedSetSlot::updatedAtEpochMillis)?.minOrNull()
            ?.let { observedAt - it >= 60_000L } != false
        if (!pageMatches(previousPage, cachedPage) || refreshTimestamp) {
            pages[page] = cachedPage
            scheduleSave()
        }
    }

    fun markSelected(page: Int, index: Int) {
        ensureLoaded()
        pages.replaceAll { cachedPageNumber, cachedPage ->
            cachedPage.copy(
                slots = cachedPage.slots.map { set ->
                    set.copy(selected = cachedPageNumber == page && set.index == index)
                },
            )
        }
        scheduleSave()
    }

    fun page(page: Int): CachedSetPage? {
        ensureLoaded()
        return pages[page]
    }

    fun allSets(totalPages: Int): List<SetCard> = buildList {
        ensureLoaded()
        (1..totalPages).forEach { page ->
            val cachedByIndex = pages[page]?.slots?.associateBy(CachedSetSlot::index).orEmpty()
            repeat(9) { index ->
                add(SetCard(page, index, (page - 1) * 9 + index + 1, cachedByIndex[index]))
            }
        }
    }

    fun snapshot(): List<CachedSetSlot> {
        ensureLoaded()
        return pages.values.flatMap(CachedSetPage::slots).map { set ->
            set.copy(items = set.items.map(ItemStack::copy), selector = set.selector.copy())
        }
    }

    fun resetSession() {
        saveNow()
        pages.clear()
        loadedProfile = null
        activeIdentity = null
        lastSavedJson = null
        saveAfterEpochMillis = null
    }

    fun onClientTick() {
        saveAfterEpochMillis?.let { if (System.currentTimeMillis() >= it) saveNow() }
    }

    fun flush() = saveNow()

    fun clearCurrentProfile() {
        val profile = SkyblockApiStorageAdapter.currentProfile() ?: return
        pages.clear()
        loadedProfile = ProfileKey(profile.accountUuid, profile.profileName)
        activeIdentity = profile
        lastSavedJson = null
        saveAfterEpochMillis = null
        SkyBlockProfileStore.clear(cacheName, profile)
    }

    private fun ensureLoaded() {
        val identity = SkyblockApiStorageAdapter.currentProfile()
        val profile = identity?.let { ProfileKey(it.accountUuid, it.profileName) }
        if (loadedProfile == profile) return
        loadedProfile = profile
        activeIdentity = identity
        pages.clear()
        lastSavedJson = identity?.let { SkyBlockProfileStore.read(cacheName, it) }
        val json = lastSavedJson ?: return
        runCatching {
            val saved = gson.fromJson(json, SavedSetCache::class.java)
            saved.sets.groupBy(SavedSet::page).forEach { (page, savedSets) ->
                pages[page] = CachedSetPage(
                    page,
                    savedSets.sortedBy(SavedSet::index).map { set ->
                        CachedSetSlot(
                            page = set.page,
                            index = set.index,
                            id = set.id,
                            items = decodeIndexed(set.items, 4),
                            selector = ItemStackSerialization.decode(set.selector),
                            selected = set.selected,
                            locked = set.locked,
                            selectable = set.selectable,
                            updatedAtEpochMillis = set.updatedAtEpochMillis?.takeIf { it > 0 },
                        )
                    },
                )
            }
        }.onFailure {
            logger.warn("Could not load $cacheName cache for $profile", it)
        }
    }

    private fun scheduleSave() {
        if (activeIdentity != null) saveAfterEpochMillis = System.currentTimeMillis() + 500L
    }

    private fun saveNow() {
        saveAfterEpochMillis = null
        val profile = activeIdentity ?: return
        val saved = SavedSetCache(
            sets = pages.values.flatMap(CachedSetPage::slots).map { set ->
                SavedSet(
                    page = set.page,
                    index = set.index,
                    id = set.id,
                    items = encodeIndexed(set.items),
                    selector = ItemStackSerialization.encode(set.selector),
                    selected = set.selected,
                    locked = set.locked,
                    selectable = set.selectable,
                    updatedAtEpochMillis = set.updatedAtEpochMillis,
                )
            }.toMutableList(),
        )
        val json = gson.toJson(saved)
        if (json == lastSavedJson) return
        if (SkyBlockProfileStore.write(cacheName, profile, json)) lastSavedJson = json
    }

    private fun encodeIndexed(stacks: List<ItemStack>): MutableList<SavedIndexedItem> = stacks.mapIndexedNotNull { index, stack ->
        stack.takeUnless(ItemStack::isEmpty)
            ?.let(ItemStackSerialization::encode)
            ?.takeIf(String::isNotBlank)
            ?.let { SavedIndexedItem(index, it) }
    }.toMutableList()

    private fun decodeIndexed(items: List<SavedIndexedItem>, size: Int): List<ItemStack> =
        MutableList(size) { ItemStack.EMPTY }.also { result ->
            items.forEach { item -> if (item.index in result.indices) result[item.index] = ItemStackSerialization.decode(item.stack) }
        }

    private fun pageMatches(previous: CachedSetPage?, current: CachedSetPage): Boolean {
        if (previous == null || previous.slots.size != current.slots.size) return false
        return previous.slots.zip(current.slots).all { (first, second) ->
            first.page == second.page &&
                first.index == second.index &&
                first.id == second.id &&
                first.selected == second.selected &&
                first.locked == second.locked &&
                first.selectable == second.selectable &&
                ItemStack.matches(first.selector, second.selector) &&
                ItemStackSerialization.stacksMatch(first.items, second.items)
        }
    }
}

object SetCollectionDetection {
    fun detect(screen: Screen, titlePatterns: List<Regex>): SetCollectionTarget? {
        val containerScreen = screen as? AbstractContainerScreen<*> ?: return null
        val menu = containerScreen.menu as? ChestMenu ?: return null
        return detect(screen.title.string, menu, titlePatterns)
    }

    fun detect(title: String, menu: ChestMenu, titlePatterns: List<Regex>): SetCollectionTarget? {
        val match = titlePatterns.firstNotNullOfOrNull { it.matchEntire(title) } ?: return null
        val page = match.groupValues[1].toIntOrNull() ?: return null
        val totalPages = match.groupValues[2].toIntOrNull() ?: return null

        if (menu.rowCount != 6 || totalPages !in 1..3 || page !in 1..totalPages) return null
        if (menu.slots.size < 90) return null
        if ((36..44).any { menu.getSlot(it).item.isEmpty }) return null

        return SetCollectionTarget(page, totalPages, menu)
    }
}
