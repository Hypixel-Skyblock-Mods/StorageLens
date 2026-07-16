package org.hypixelskyblockmods.storagelens.feature.itemsearch

import com.google.gson.GsonBuilder
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyBlockProfileIdentity
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyblockApiItemSearchAdapter
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyblockApiStorageAdapter
import org.hypixelskyblockmods.storagelens.util.ItemStackSerialization

object SackOfSacksRepository {
    private data class SavedItem(var slot: Int = 0, var stack: String = "")
    private data class SavedData(var schemaVersion: Int = 1, var updatedAtEpochMillis: Long = 0, var items: MutableList<SavedItem> = mutableListOf())
    private data class ProfileKey(val accountUuid: java.util.UUID, val profileName: String)

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var items = emptyList<Pair<Int, ItemStack>>()
    private var updatedAtEpochMillis: Long? = null
    private var loadedProfile: ProfileKey? = null
    private var activeIdentity: SkyBlockProfileIdentity? = null
    private var lastSavedJson: String? = null
    private var saveAfterEpochMillis: Long? = null

    fun initializeSource() {
        ItemSourceRegistry.register(ItemSourceId.SACK_OF_SACKS, ::snapshot)
    }

    fun remember(stacks: List<Pair<Int, ItemStack>>) {
        ensureLoaded()
        if (activeIdentity == null) return
        val filtered = stacks.filterNot { it.second.isEmpty }.map { it.first to it.second.copy() }
        if (items.size == filtered.size && items.zip(filtered).all { (a, b) -> a.first == b.first && ItemStack.matches(a.second, b.second) }) return
        items = filtered
        updatedAtEpochMillis = System.currentTimeMillis()
        if (activeIdentity != null) saveAfterEpochMillis = System.currentTimeMillis() + 500L
    }

    fun onClientTick() {
        saveAfterEpochMillis?.let { if (System.currentTimeMillis() >= it) saveNow() }
    }

    fun flush() = saveNow()

    fun resetSession() {
        saveNow()
        items = emptyList()
        updatedAtEpochMillis = null
        loadedProfile = null
        activeIdentity = null
        lastSavedJson = null
        saveAfterEpochMillis = null
    }

    fun clearCurrentProfile() {
        val profile = SkyblockApiStorageAdapter.currentProfile() ?: return
        items = emptyList()
        updatedAtEpochMillis = null
        SkyBlockProfileStore.clear("sack-of-sacks", profile)
        lastSavedJson = null
        saveAfterEpochMillis = null
    }

    private fun snapshot(): List<SearchableItem> {
        ensureLoaded()
        return items.mapNotNull { (slot, stack) ->
            SkyblockApiItemSearchAdapter.searchable(
                stack,
                stack.count.toLong(),
                ItemSourceId.SACK_OF_SACKS,
                ItemLocation.Container("Sack of Sacks", slot = slot),
                ItemNavigationAction.Command("sax"),
                ItemDataOrigin.LOCAL_OBSERVATION,
                updatedAtEpochMillis,
            )
        }
    }

    private fun ensureLoaded() {
        val identity = SkyblockApiStorageAdapter.currentProfile()
        val key = identity?.let { ProfileKey(it.accountUuid, it.profileName) }
        if (loadedProfile == key) return
        items = emptyList()
        updatedAtEpochMillis = null
        loadedProfile = key
        activeIdentity = identity
        lastSavedJson = identity?.let { SkyBlockProfileStore.read("sack-of-sacks", it) }
        val json = lastSavedJson ?: return
        runCatching {
            val saved = gson.fromJson(json, SavedData::class.java)
            updatedAtEpochMillis = saved.updatedAtEpochMillis.takeIf { it > 0 }
            items = saved.items.map { it.slot to ItemStackSerialization.decode(it.stack) }
        }
    }

    private fun saveNow() {
        saveAfterEpochMillis = null
        val profile = activeIdentity ?: return
        val json = gson.toJson(
            SavedData(
                updatedAtEpochMillis = updatedAtEpochMillis ?: System.currentTimeMillis(),
                items = items.map { SavedItem(it.first, ItemStackSerialization.encode(it.second)) }.toMutableList(),
            ),
        )
        if (json == lastSavedJson) return
        if (SkyBlockProfileStore.write("sack-of-sacks", profile, json)) lastSavedJson = json
    }
}
