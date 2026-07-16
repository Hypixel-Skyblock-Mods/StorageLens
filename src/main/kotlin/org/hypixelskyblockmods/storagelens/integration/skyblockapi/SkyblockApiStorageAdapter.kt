package org.hypixelskyblockmods.storagelens.integration.skyblockapi

import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.storagelens.feature.storage.StoragePageKey
import org.hypixelskyblockmods.storagelens.feature.storage.StoragePageType
import tech.thatgravyboat.skyblockapi.api.profile.items.storage.PlayerStorageInstance
import tech.thatgravyboat.skyblockapi.api.profile.items.storage.StorageAPI
import tech.thatgravyboat.skyblockapi.api.profile.profile.ProfileAPI

data class SkyBlockProfileIdentity(
    val accountUuid: UUID,
    val profileName: String,
    val profileUuid: UUID?,
)

data class SkyblockApiStoragePage(
    val key: StoragePageKey,
    val items: List<ItemStack>,
    val updatedAtEpochMillis: Long,
)

object SkyblockApiStorageAdapter {
    fun currentProfile(): SkyBlockProfileIdentity? {
        if (!ProfileAPI.isLoaded) return null
        val profileName = ProfileAPI.profileName?.takeIf(String::isNotBlank) ?: return null
        return SkyBlockProfileIdentity(
            accountUuid = Minecraft.getInstance().user.profileId,
            profileName = profileName,
            profileUuid = ProfileAPI.profileId,
        )
    }

    fun allPages(): List<SkyblockApiStoragePage> {
        val profile = currentProfile() ?: return emptyList()
        val pages = buildList {
            StorageAPI.enderchests.mapNotNullTo(this) { it.toPage(StoragePageType.ENDER_CHEST) }
            StorageAPI.backpacks.mapNotNullTo(this) { it.toPage(StoragePageType.BACKPACK) }
        }.distinctBy(SkyblockApiStoragePage::key)
        return pages.takeIf { sameStorageProfile(profile, currentProfile()) } ?: emptyList()
    }

    private fun PlayerStorageInstance.toPage(type: StoragePageType): SkyblockApiStoragePage? {
        val key = storagePageKeyFromApiIndex(type, index) ?: return null
        return SkyblockApiStoragePage(
            key = key,
            items = items.map(ItemStack::copy),
            updatedAtEpochMillis = lastUpdated.toEpochMilliseconds(),
        )
    }

    private fun sameStorageProfile(
        expected: SkyBlockProfileIdentity,
        actual: SkyBlockProfileIdentity?,
    ): Boolean = actual != null &&
        expected.accountUuid == actual.accountUuid &&
        expected.profileName == actual.profileName
}

internal fun storagePageKeyFromApiIndex(type: StoragePageType, index: Int): StoragePageKey? {
    val number = index + 1
    val validRange = when (type) {
        StoragePageType.ENDER_CHEST -> 1..9
        StoragePageType.BACKPACK -> 1..18
    }
    return number.takeIf { it in validRange }?.let { StoragePageKey(type, it) }
}
