package org.hypixelskyblockmods.storagelens.feature.itemsearch

import com.google.gson.GsonBuilder
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyBlockProfileIdentity
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyblockApiItemSearchAdapter
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyblockApiStorageAdapter
import org.hypixelskyblockmods.storagelens.util.ItemStackSerialization

object PlayerInventorySearchRepository {
    private data class SavedItem(var slot: Int = 0, var equipped: Boolean = false, var stack: String = "")
    private data class SavedRealm(var updatedAtEpochMillis: Long = 0, var items: MutableList<SavedItem> = mutableListOf())
    private data class SavedInventory(var schemaVersion: Int = 1, var realms: MutableMap<String, SavedRealm> = mutableMapOf())
    private data class RealmSnapshot(val updatedAtEpochMillis: Long, val items: List<ObservedItem>)
    private data class ObservedItem(val slot: Int, val equipped: Boolean, val stack: ItemStack)
    private data class ProfileKey(val accountUuid: java.util.UUID, val profileName: String)

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val snapshots = java.util.EnumMap<InventoryRealm, RealmSnapshot>(InventoryRealm::class.java)
    private var loadedProfile: ProfileKey? = null
    private var activeIdentity: SkyBlockProfileIdentity? = null
    private var lastSavedJson: String? = null
    private var saveAfterEpochMillis: Long? = null

    fun initializeSource() {
        ItemSourceRegistry.register(ItemSourceId.INVENTORY) { snapshot(InventoryRealm.NORMAL, equippedOnly = false) }
        ItemSourceRegistry.register(ItemSourceId.EQUIPPED) { snapshot(InventoryRealm.NORMAL, equippedOnly = true) }
        ItemSourceRegistry.register(ItemSourceId.RIFT) { snapshot(InventoryRealm.RIFT, equippedOnly = null) }
    }

    fun onInventoryChanged() {
        if (!SkyblockApiItemSearchAdapter.isOnSkyBlock()) return
        ensureLoaded()
        rememberLive()
    }

    fun onClientTick() {
        val deadline = saveAfterEpochMillis ?: return
        if (System.currentTimeMillis() >= deadline) saveNow()
    }

    fun flush() = saveNow()

    fun clearCurrentProfile() {
        val profile = SkyblockApiStorageAdapter.currentProfile() ?: return
        snapshots.clear()
        loadedProfile = ProfileKey(profile.accountUuid, profile.profileName)
        activeIdentity = profile
        lastSavedJson = null
        saveAfterEpochMillis = null
        SkyBlockProfileStore.clear("inventory", profile)
    }

    fun resetSession() {
        saveNow()
        snapshots.clear()
        loadedProfile = null
        activeIdentity = null
        lastSavedJson = null
        saveAfterEpochMillis = null
    }

    private fun snapshot(requestedRealm: InventoryRealm, equippedOnly: Boolean?): List<SearchableItem> {
        if (!SkyblockApiItemSearchAdapter.isOnSkyBlock()) return emptyList()
        ensureLoaded()
        rememberLive()
        val currentRealm = SkyblockApiItemSearchAdapter.currentRealm()
        return snapshots.filterKeys { it == requestedRealm }.flatMap { (realm, snapshot) ->
            snapshot.items.filter { equippedOnly == null || it.equipped == equippedOnly }.mapNotNull { item ->
                val source = when {
                    realm == InventoryRealm.RIFT -> ItemSourceId.RIFT
                    item.equipped -> ItemSourceId.EQUIPPED
                    else -> ItemSourceId.INVENTORY
                }
                SkyblockApiItemSearchAdapter.searchable(
                    item.stack,
                    item.stack.count.toLong(),
                    source,
                    ItemLocation.Inventory(realm, item.slot, item.equipped),
                    if (realm == currentRealm) ItemNavigationAction.Inventory(realm, item.slot) else ItemNavigationAction.None,
                    if (realm == currentRealm) ItemDataOrigin.LIVE_PLAYER else ItemDataOrigin.LOCAL_OBSERVATION,
                    if (realm == currentRealm) System.currentTimeMillis() else snapshot.updatedAtEpochMillis,
                    ownershipIdentity = if (realm == InventoryRealm.NORMAL && item.equipped) {
                        equippedArmorOwnershipIdentityFromInventorySlot(item.slot)
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun ensureLoaded() {
        val identity = SkyblockApiStorageAdapter.currentProfile()
        val key = identity?.let { ProfileKey(it.accountUuid, it.profileName) }
        if (loadedProfile == key) return
        snapshots.clear()
        loadedProfile = key
        activeIdentity = identity
        lastSavedJson = identity?.let { SkyBlockProfileStore.read("inventory", it) }
        val json = lastSavedJson ?: return
        runCatching {
            val saved = gson.fromJson(json, SavedInventory::class.java)
            saved.realms.forEach { (name, realm) ->
                val type = runCatching { InventoryRealm.valueOf(name) }.getOrNull() ?: return@forEach
                snapshots[type] = RealmSnapshot(
                    realm.updatedAtEpochMillis,
                    realm.items.map { ObservedItem(it.slot, it.equipped, ItemStackSerialization.decode(it.stack)) },
                )
            }
        }
    }

    private fun rememberLive() {
        val player = Minecraft.getInstance().player ?: return
        val realm = SkyblockApiItemSearchAdapter.currentRealm()
        val items = buildList {
            player.inventory.nonEquipmentItems.forEachIndexed { index, stack ->
                if (index != 8 && !stack.isEmpty) add(ObservedItem(index, false, stack.copy()))
            }
            listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET).forEachIndexed { index, slot ->
                val stack = player.getItemBySlot(slot)
                if (!stack.isEmpty) add(ObservedItem(39 - index, true, stack.copy()))
            }
        }
        val previous = snapshots[realm]
        if (previous != null && observedMatches(previous.items, items)) return
        snapshots[realm] = RealmSnapshot(System.currentTimeMillis(), items)
        if (activeIdentity != null) saveAfterEpochMillis = System.currentTimeMillis() + 500
    }

    private fun saveNow() {
        saveAfterEpochMillis = null
        val profile = activeIdentity ?: return
        val saved = SavedInventory(realms = snapshots.entries.associateTo(linkedMapOf()) { (realmType, realm) ->
            realmType.name to SavedRealm(
                realm.updatedAtEpochMillis,
                realm.items.map { SavedItem(it.slot, it.equipped, ItemStackSerialization.encode(it.stack)) }.toMutableList(),
            )
        })
        val json = gson.toJson(saved)
        if (json == lastSavedJson) return
        if (SkyBlockProfileStore.write("inventory", profile, json)) lastSavedJson = json
    }

    private fun observedMatches(first: List<ObservedItem>, second: List<ObservedItem>): Boolean =
        first.size == second.size && first.zip(second).all { (left, right) ->
            left.slot == right.slot && left.equipped == right.equipped && ItemStack.matches(left.stack, right.stack)
        }
}
