package org.hypixelskyblockmods.storagelens.feature.itemsearch

import java.security.MessageDigest
import java.util.Locale
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.storagelens.util.ItemStackSerialization

fun interface ItemSearchSource {
    fun snapshot(): List<SearchableItem>
}

fun interface DerivedItemSearchSource {
    fun derive(items: List<SearchableItem>): List<SearchableItem>
}

internal fun interface AuthoritativeItemScopeSource {
    fun snapshot(): Set<AuthoritativeItemScope>
}

object ItemSourceRegistry {
    private val sources = linkedMapOf<ItemSourceId, MutableList<ItemSearchSource>>()
    private val derivedSources = linkedMapOf<ItemSourceId, MutableList<DerivedItemSearchSource>>()
    private val authoritativeScopeSources = linkedMapOf<ItemSourceId, MutableList<AuthoritativeItemScopeSource>>()

    fun register(id: ItemSourceId, source: ItemSearchSource) {
        sources.getOrPut(id) { mutableListOf() }.add(source)
    }

    fun registerDerived(id: ItemSourceId, source: DerivedItemSearchSource) {
        derivedSources.getOrPut(id) { mutableListOf() }.add(source)
    }

    internal fun registerAuthoritativeScopes(id: ItemSourceId, source: AuthoritativeItemScopeSource) {
        authoritativeScopeSources.getOrPut(id) { mutableListOf() }.add(source)
    }

    fun snapshot(enabled: Set<ItemSourceId> = ItemSourceId.entries.toSet()): SourceSnapshot {
        val items = mutableListOf<SearchableItem>()
        val failures = linkedMapOf<ItemSourceId, Throwable>()
        sources.forEach { (id, registered) ->
            if (id !in enabled) return@forEach
            registered.forEach { source ->
                runCatching { source.snapshot().filterNot { it.stack.isEmpty || it.amount <= 0 }.map(SearchableItem::defensiveCopy) }
                    .onSuccess(items::addAll)
                    .onFailure { failures[id] = it }
            }
        }
        val authoritativeScopes = linkedSetOf<AuthoritativeItemScope>()
        authoritativeScopeSources.forEach { (id, registered) ->
            if (id !in enabled) return@forEach
            registered.forEach { source ->
                runCatching(source::snapshot)
                    .onSuccess(authoritativeScopes::addAll)
                    .onFailure { failures[id] = it }
            }
        }
        val resolvedItems = resolveMuseumLoans(resolveObservedLocations(items, authoritativeScopes)).toMutableList()
        derivedSources.forEach { (id, registered) ->
            if (id !in enabled) return@forEach
            registered.forEach { source ->
                runCatching { source.derive(resolvedItems.map(SearchableItem::defensiveCopy)).map(SearchableItem::defensiveCopy) }
                    .onSuccess(resolvedItems::addAll)
                    .onFailure { failures[id] = it }
            }
        }
        return SourceSnapshot(resolvedItems, failures)
    }

    fun clear() {
        sources.clear()
        derivedSources.clear()
        authoritativeScopeSources.clear()
    }
}

data class SourceSnapshot(val items: List<SearchableItem>, val failures: Map<ItemSourceId, Throwable>)

object ItemFingerprintFactory {
    fun create(stack: ItemStack, skyblockId: String?, cleanName: String): ItemFingerprint {
        val normalized = stack.copyWithCount(1)
        val encoded = ItemStackSerialization.encode(normalized)
        return ItemFingerprint(
            vanillaId = BuiltInRegistries.ITEM.getKey(normalized.item).toString(),
            skyblockId = skyblockId,
            cleanName = cleanName.lowercase(Locale.ROOT),
            componentHash = hashComponentPayload(encoded),
        )
    }

    internal fun hashComponentPayload(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

data class ItemSearchOptions(
    val searchLore: Boolean = true,
    val searchIds: Boolean = true,
    val searchLocations: Boolean = false,
)

enum class ItemSearchSort {
    AMOUNT,
    VALUE,
    RARITY,
    NAME,
}

class ItemSearchIndex private constructor(private val entries: List<ItemSearchEntry>) {
    fun all(): List<ItemSearchEntry> = entries.map(ItemSearchEntry::defensiveCopy)

    fun query(
        query: String,
        category: ItemSourceCategory,
        options: ItemSearchOptions,
        sort: ItemSearchSort,
        ascending: Boolean,
    ): List<ItemSearchEntry> {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val filtered = entries.filter { entry ->
            (category == ItemSourceCategory.ALL || entry.locations.any { it.source.category == category }) &&
                terms.all { term -> entry.matches(term, options) }
        }
        val comparator = when (sort) {
            ItemSearchSort.AMOUNT -> compareBy<ItemSearchEntry> { it.totalAmount }
            ItemSearchSort.VALUE -> compareBy(nullsLast()) { it.estimatedValue }
            ItemSearchSort.RARITY -> compareBy(nullsLast()) { it.rarityOrdinal }
            ItemSearchSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        val effective = if (ascending) comparator else comparator.reversedWithNullsLast(sort)
        return filtered.sortedWith(effective).map(ItemSearchEntry::defensiveCopy)
    }

    companion object {
        val EMPTY = ItemSearchIndex(emptyList())

        fun build(items: List<SearchableItem>): ItemSearchIndex {
            return build(items, allowEmptyStacks = false)
        }

        internal fun buildForTests(items: List<SearchableItem>): ItemSearchIndex =
            build(items, allowEmptyStacks = true)

        internal fun buildForTests(
            items: List<SearchableItem>,
            authoritativeScopes: Set<AuthoritativeItemScope>,
        ): ItemSearchIndex = build(resolveObservedLocations(items, authoritativeScopes), allowEmptyStacks = true)

        private fun build(items: List<SearchableItem>, allowEmptyStacks: Boolean): ItemSearchIndex {
            val buckets = linkedMapOf<ItemFingerprint, MutableList<MutableEntry>>()
            resolveMuseumLoans(resolveObservedLocations(items))
                .filterNot { (!allowEmptyStacks && it.stack.isEmpty) || it.amount <= 0 }
                .forEach { item ->
                val bucket = buckets.getOrPut(item.fingerprint) { mutableListOf() }
                val aggregate = bucket.firstOrNull { ItemStack.matches(it.displayStack.copyWithCount(1), item.stack.copyWithCount(1)) }
                    ?: MutableEntry(item).also(bucket::add)
                if (aggregate.locations.isNotEmpty()) aggregate.add(item)
            }
            return ItemSearchIndex(buckets.values.flatten().map(MutableEntry::freeze))
        }
    }

    private class MutableEntry(first: SearchableItem) {
        val fingerprint = first.fingerprint
        val displayStack = first.stack.copyWithCount(1)
        var rarityOrdinal: Int? = first.rarityOrdinal
        val locations = mutableListOf(first.defensiveCopy())

        fun add(item: SearchableItem) {
            val existing = locations.indexOfFirst { it.location.identity == item.location.identity }
            if (existing >= 0) {
                val previous = locations[existing]
                if (item.origin.priority() <= previous.origin.priority()) return
                locations[existing] = item.defensiveCopy()
            } else {
                locations += item.defensiveCopy()
            }
            rarityOrdinal = listOfNotNull(rarityOrdinal, item.rarityOrdinal).maxOrNull()
        }

        fun freeze(): ItemSearchEntry {
            val countedOwnership = mutableSetOf<String>()
            val ownedItems = locations.filter { item ->
                item.contributesToTotals && (item.ownershipIdentity?.let(countedOwnership::add) ?: true)
            }
            val allValuesKnown = ownedItems.all { it.estimatedValue != null }
            return ItemSearchEntry(
                fingerprint,
                displayStack.copy(),
                ownedItems.sumOf(SearchableItem::amount),
                ownedItems.sumOf { it.estimatedValue ?: 0L }.takeIf { allValuesKnown },
                rarityOrdinal,
                locations.map(SearchableItem::defensiveCopy),
            )
        }
    }
}

internal fun resolveMuseumLoans(items: List<SearchableItem>): List<SearchableItem> {
    val physicallyHeldUuids = items.asSequence()
        .filterNot { it.source == ItemSourceId.MUSEUM || it.source == ItemSourceId.LOADOUTS }
        .mapNotNull(SearchableItem::instanceUuid)
        .toSet()
    if (physicallyHeldUuids.isEmpty()) return items
    return items.filterNot { item ->
        item.source == ItemSourceId.MUSEUM && item.instanceUuid in physicallyHeldUuids
    }
}

private val observedLocationSources = setOf(
    ItemSourceId.STORAGE,
    ItemSourceId.WARDROBE,
    ItemSourceId.EQUIPMENT_WARDROBE,
)

private fun resolveObservedLocations(
    items: List<SearchableItem>,
    authoritativeScopes: Set<AuthoritativeItemScope> = emptySet(),
): List<SearchableItem> {
    val resolved = mutableListOf<SearchableItem>()
    val positions = mutableMapOf<Pair<ItemSourceId, String>, Int>()
    items.forEach { item ->
        if (item.authoritativeScope() in authoritativeScopes && item.origin.priority() < ItemDataOrigin.LOCAL_OBSERVATION.priority()) {
            return@forEach
        }
        if (item.source !in observedLocationSources) {
            resolved += item
            return@forEach
        }
        val key = item.source to item.location.identity
        val index = positions[key]
        if (index == null) {
            positions[key] = resolved.size
            resolved += item
        } else if (item.origin.priority() > resolved[index].origin.priority()) {
            resolved[index] = item
        }
    }
    return resolved
}

private fun ItemSearchEntry.matches(term: String, options: ItemSearchOptions): Boolean {
    val needle = term.lowercase(Locale.ROOT)
    return locations.any { item ->
        needle in item.searchableName.lowercase(Locale.ROOT) ||
            (options.searchLore && item.searchableLore.any { needle in it.lowercase(Locale.ROOT) }) ||
            (options.searchIds && item.skyblockId?.lowercase(Locale.ROOT)?.contains(needle) == true) ||
            (options.searchLocations && (needle in item.source.displayName.lowercase(Locale.ROOT) || needle in item.location.label.lowercase(Locale.ROOT)))
    }
}

private fun ItemDataOrigin.priority(): Int = when (this) {
    ItemDataOrigin.LIVE_MENU, ItemDataOrigin.LIVE_PLAYER -> 4
    ItemDataOrigin.LOCAL_OBSERVATION -> 3
    ItemDataOrigin.SKYBLOCK_API_PROFILE -> 2
    ItemDataOrigin.DERIVED -> 1
}

private fun ItemSearchEntry.defensiveCopy() = copy(
    displayStack = displayStack.copy(),
    locations = locations.map(SearchableItem::defensiveCopy),
)

private fun <T : Comparable<T>> nullsLast(): Comparator<T?> = Comparator { first, second ->
    when {
        first == null && second == null -> 0
        first == null -> 1
        second == null -> -1
        else -> first.compareTo(second)
    }
}

private fun Comparator<ItemSearchEntry>.reversedWithNullsLast(sort: ItemSearchSort): Comparator<ItemSearchEntry> = Comparator { first, second ->
    val firstUnknown = sort == ItemSearchSort.VALUE && first.estimatedValue == null || sort == ItemSearchSort.RARITY && first.rarityOrdinal == null
    val secondUnknown = sort == ItemSearchSort.VALUE && second.estimatedValue == null || sort == ItemSearchSort.RARITY && second.rarityOrdinal == null
    when {
        firstUnknown && !secondUnknown -> 1
        !firstUnknown && secondUnknown -> -1
        else -> -compare(first, second)
    }
}
