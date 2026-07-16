package org.hypixelskyblockmods.storagelens.feature.itemsearch

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.Path
import java.util.Base64
import net.fabricmc.loader.api.FabricLoader
import org.hypixelskyblockmods.storagelens.integration.skyblockapi.SkyBlockProfileIdentity
import org.slf4j.LoggerFactory

object SkyBlockProfileStore {
    private val logger = LoggerFactory.getLogger("StorageLens Profile Store")
    private val root: Path
        get() = FabricLoader.getInstance().configDir.resolve("storagelens")

    fun read(name: String, profile: SkyBlockProfileIdentity): String? = readFromRoot(root, name, profile)

    internal fun readFromRoot(base: Path, name: String, profile: SkyBlockProfileIdentity): String? = runCatching {
        val file = profileDirectory(base, profile).resolve("$name.json")
        if (Files.isRegularFile(file)) Files.readString(file) else null
    }.getOrElse {
        logger.warn("Could not read $name for ${profile.profileName}", it)
        null
    }

    fun write(name: String, profile: SkyBlockProfileIdentity, contents: String): Boolean = writeToRoot(root, name, profile, contents)

    internal fun writeToRoot(base: Path, name: String, profile: SkyBlockProfileIdentity, contents: String): Boolean = runCatching {
        val directory = profileDirectory(base, profile)
        Files.createDirectories(directory)
        val file = directory.resolve("$name.json")
        val temporary = directory.resolve("$name.json.tmp")
        Files.writeString(temporary, contents)
        runCatching {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
        true
    }.getOrElse {
        logger.warn("Could not write $name for ${profile.profileName}", it)
        false
    }

    fun clear(name: String, profile: SkyBlockProfileIdentity): Boolean = clearFromRoot(root, name, profile)

    internal fun clearFromRoot(base: Path, name: String, profile: SkyBlockProfileIdentity): Boolean = runCatching {
        Files.deleteIfExists(profileDirectory(base, profile).resolve("$name.json"))
        true
    }.getOrElse {
        logger.warn("Could not clear $name for ${profile.profileName}", it)
        false
    }

    fun clearSearchObservations(profile: SkyBlockProfileIdentity) {
        listOf("inventory", "storage-pages", "sack-of-sacks", "island-chests", "loadouts", "wardrobe", "equipment").forEach { clear(it, profile) }
    }

    internal fun profileDirectory(base: Path, profile: SkyBlockProfileIdentity) = base
        .resolve(profile.accountUuid.toString())
        .resolve(Base64.getUrlEncoder().withoutPadding().encodeToString(profile.profileName.toByteArray(StandardCharsets.UTF_8)))
}
