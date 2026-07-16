package org.hypixelskyblockmods.storagelens.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import net.minecraft.client.Minecraft
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.RegistryOps
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

object ItemStackSerialization {
    private val logger = LoggerFactory.getLogger("StorageLens Item Serialization")
    private const val maxItemBytes = 1_000_000L

    fun encode(stack: ItemStack): String = runCatching {
        if (stack.isEmpty) return ""
        val tag = ItemStack.CODEC.encodeStart(registryOps(), stack.copy())
            .resultOrPartial { error -> logger.warn("Could not encode item: $error") }
            .orElse(null) as? CompoundTag ?: return ""
        val root = CompoundTag()
        root.put("stack", tag)
        ByteArrayOutputStream().use { output ->
            NbtIo.writeCompressed(root, output)
            Base64.getEncoder().encodeToString(output.toByteArray())
        }
    }.getOrElse {
        logger.warn("Could not encode item stack", it)
        ""
    }

    fun decode(encoded: String): ItemStack {
        if (encoded.isBlank()) return ItemStack.EMPTY
        return runCatching {
            val bytes = Base64.getDecoder().decode(encoded)
            val root = NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtAccounter.create(maxItemBytes))
            val tag = root.getCompound("stack").orElse(null) ?: return ItemStack.EMPTY
            ItemStack.CODEC.parse(registryOps(), tag)
                .resultOrPartial { error -> logger.warn("Could not decode item: $error") }
                .orElse(ItemStack.EMPTY)
        }.getOrElse {
            logger.warn("Could not decode item stack", it)
            ItemStack.EMPTY
        }
    }

    fun stacksMatch(first: List<ItemStack>, second: List<ItemStack>): Boolean =
        first.size == second.size && first.indices.all { ItemStack.matches(first[it], second[it]) }

    private fun registryOps(): RegistryOps<Tag> {
        val registries = runCatching { Minecraft.getInstance().connection?.registryAccess() }.getOrNull() ?: RegistryAccess.EMPTY
        return RegistryOps.create(NbtOps.INSTANCE, registries)
    }
}
