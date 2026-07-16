package org.hypixelskyblockmods.storagelens.feature.itemsearch

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.hypixelskyblockmods.storagelens.config.SkyHudConfigManager
import org.hypixelskyblockmods.storagelens.gui.SkyHudBackdrop
import org.hypixelskyblockmods.storagelens.gui.SkyHudControls
import org.hypixelskyblockmods.storagelens.gui.SkyHudTheme
import java.util.Locale

class ItemSearchScreen(
    initialQuery: String,
    initialCategory: ItemSourceCategory,
) : Screen(Component.literal("StorageLens Item Search")) {
    private data class ItemBounds(val entry: ItemSearchEntry, val x: Int, val y: Int)
    private data class CategoryBounds(val category: ItemSourceCategory, val x: Int, val y: Int, val width: Int, val height: Int)
    private data class LocationBounds(val item: SearchableItem, val x: Int, val y: Int, val width: Int, val height: Int)
    private data class ButtonBounds(val x: Int, val y: Int, val width: Int, val height: Int)

    private val categories = listOf(
        ItemSourceCategory.ALL,
        ItemSourceCategory.STORAGE,
        ItemSourceCategory.INVENTORY,
        ItemSourceCategory.WARDROBE_EQUIPMENT,
        ItemSourceCategory.SACKS,
        ItemSourceCategory.ISLAND_CHESTS,
        ItemSourceCategory.MUSEUM,
        ItemSourceCategory.RIFT,
        ItemSourceCategory.FORGE,
        ItemSourceCategory.OTHER,
    )
    private val categoryIcons = mapOf(
        ItemSourceCategory.ALL to ItemStack(Items.COMPASS),
        ItemSourceCategory.STORAGE to ItemStack(Items.ENDER_CHEST),
        ItemSourceCategory.INVENTORY to ItemStack(Items.CHEST),
        ItemSourceCategory.WARDROBE_EQUIPMENT to ItemStack(Items.IRON_CHESTPLATE),
        ItemSourceCategory.SACKS to ItemStack(Items.BUNDLE),
        ItemSourceCategory.ISLAND_CHESTS to ItemStack(Items.TRAPPED_CHEST),
        ItemSourceCategory.MUSEUM to ItemStack(Items.PAINTING),
        ItemSourceCategory.RIFT to ItemStack(Items.ENDER_EYE),
        ItemSourceCategory.FORGE to ItemStack(Items.BLAST_FURNACE),
        ItemSourceCategory.OTHER to ItemStack(Items.HOPPER),
    )
    private var query = initialQuery
    private var category = initialCategory
    private var sort = ItemSearchSort.AMOUNT
    private var ascending = false
    private var scrollRows = 0
    private var itemBounds = emptyList<ItemBounds>()
    private var categoryBounds = emptyList<CategoryBounds>()
    private var locationBounds = emptyList<LocationBounds>()
    private var showAllBounds: ButtonBounds? = null
    private var locationEntry: ItemSearchEntry? = null
    private var locationScroll = 0
    private var searchBox: EditBox? = null

    private val headerHeight = 28
    private val slotSize = 23
    private val slotPitch = 24
    private val sidebarWidth = 112
    private val narrowSidebarWidth = 31

    override fun init() {
        super.init()
        val search = EditBox(
            font,
            searchX(),
            panelY() + 8,
            searchWidth(),
            12,
            Component.literal("Search items"),
        )
        search.value = query
        search.setHint(Component.literal("Search items..."))
        search.setBordered(false)
        search.setTextColor(SkyHudTheme.TEXT)
        search.setTextColorUneditable(SkyHudTheme.TEXT_MUTED)
        search.setResponder {
            query = it
            scrollRows = 0
            remember()
        }
        searchBox = search
        addRenderableWidget(search)
        setInitialFocus(search)
    }

    fun onIndexUpdated() {
        scrollRows = 0
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (locationEntry == null) {
            SkyHudBackdrop.renderPanelBlur(graphics, SkyHudBackdrop.Region(panelX(), panelY(), panelWidth(), panelHeight()))
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val x = panelX()
        val y = panelY()
        val width = panelWidth()
        val height = panelHeight()
        graphics.fill(0, 0, this.width, this.height, SkyHudTheme.SCREEN_DIM)
        SkyHudTheme.outlinedRoundedRect(graphics, x, y, width, height, SkyHudTheme.PANEL, SkyHudTheme.PRIMARY)
        graphics.fill(x + 1, y + headerHeight, x + width - 1, y + headerHeight + 1, SkyHudTheme.DIVIDER)
        graphics.text(font, "ITEM SEARCH", x + 8, y + 10, SkyHudTheme.TEXT, false)
        SkyHudControls.settingsButton(graphics, mouseX, mouseY, x + 79, y + 6)

        SkyHudTheme.outlinedRoundedRect(
            graphics,
            searchX() - 4,
            y + 4,
            searchWidth() + 8,
            20,
            SkyHudTheme.SURFACE,
            SkyHudTheme.BORDER,
        )
        drawSortControls(graphics, mouseX, mouseY)
        drawCategories(graphics, mouseX, mouseY)
        drawResults(graphics, mouseX, mouseY)
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        locationEntry?.let {
            graphics.nextStratum()
            graphics.blurBeforeThisStratum()
            graphics.nextStratum()
            drawLocationPicker(graphics, it, mouseX, mouseY)
        }
    }

    private fun drawSortControls(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val sortX = sortX()
        val y = panelY() + 5
        val sortHovered = locationEntry == null && mouseX in sortX until (sortX + 72) && mouseY in y until (y + 18)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            sortX,
            y,
            72,
            18,
            if (sortHovered) SkyHudTheme.CONTROL_HOVER else SkyHudTheme.CONTROL,
            SkyHudTheme.BORDER,
        )
        SkyHudControls.centeredText(graphics, font, sort.label(), sortX, y, 72, 18, SkyHudTheme.TEXT)
        val directionX = sortX + 75
        val directionHovered = locationEntry == null && mouseX in directionX until (directionX + 20) && mouseY in y until (y + 18)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            directionX,
            y,
            20,
            18,
            if (directionHovered) SkyHudTheme.CONTROL_HOVER else SkyHudTheme.CONTROL,
            SkyHudTheme.PRIMARY,
        )
        SkyHudControls.centeredText(graphics, font, if (ascending) "↑" else "↓", directionX, y, 20, 18, SkyHudTheme.TEXT)
    }

    private fun drawCategories(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val x = panelX() + 5
        val startY = panelY() + headerHeight + 7
        val collapsed = collapsedSidebar()
        val width = if (collapsed) narrowSidebarWidth - 8 else sidebarWidth - 9
        val availableHeight = panelY() + panelHeight() - 7 - startY
        val pitch = (availableHeight / categories.size).coerceIn(19, 25)
        val buttonHeight = (pitch - 4).coerceIn(17, 21)
        val bounds = mutableListOf<CategoryBounds>()
        categories.forEachIndexed { index, value ->
            val y = startY + index * pitch
            val hovered = locationEntry == null && mouseX in x until (x + width) && mouseY in y until (y + buttonHeight)
            val selected = value == category
            SkyHudTheme.outlinedRoundedRect(
                graphics,
                x,
                y,
                width,
                buttonHeight,
                when {
                    selected -> SkyHudTheme.SURFACE_RAISED
                    hovered -> SkyHudTheme.CONTROL_HOVER
                    else -> SkyHudTheme.CONTROL
                },
                if (selected) SkyHudTheme.PRIMARY else SkyHudTheme.BORDER,
            )
            val icon = categoryIcons.getValue(value)
            val iconX = if (collapsed) x + (width - 16) / 2 else x + 4
            val iconY = y + (buttonHeight - 16) / 2
            graphics.item(icon, iconX, iconY)
            if (!collapsed) {
                graphics.text(
                    font,
                    value.label(),
                    x + 24,
                    y + (buttonHeight - font.lineHeight) / 2,
                    if (selected) SkyHudTheme.TEXT else SkyHudTheme.TEXT_MUTED,
                    false,
                )
            }
            if (collapsed && hovered) graphics.setTooltipForNextFrame(Component.literal(value.label()), mouseX, mouseY)
            bounds += CategoryBounds(value, x, y, width, buttonHeight)
        }
        categoryBounds = bounds
    }

    private fun drawResults(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val left = contentLeft()
        val top = panelY() + headerHeight + 7
        val right = panelX() + panelWidth() - 7
        val bottom = panelY() + panelHeight() - 7
        val contentWidth = (right - left - 5).coerceAtLeast(slotPitch)
        val columns = (contentWidth / slotPitch).coerceAtLeast(1)
        val visibleRows = ((bottom - top) / slotPitch).coerceAtLeast(1)
        val config = SkyHudConfigManager.config.itemSearch
        val state = ItemSearchController.state()
        val results = state.index.query(
            query,
            category,
            ItemSearchOptions(config.matchLore, config.matchIds, config.matchLocations),
            sort,
            ascending,
        )
        val totalRows = (results.size + columns - 1) / columns
        val maxScroll = (totalRows - visibleRows).coerceAtLeast(0)
        scrollRows = scrollRows.coerceIn(0, maxScroll)
        val visible = results.drop(scrollRows * columns).take(visibleRows * columns)
        val bounds = mutableListOf<ItemBounds>()
        graphics.enableScissor(left, top, right, bottom)
        visible.forEachIndexed { index, entry ->
            val slotX = left + (index % columns) * slotPitch
            val slotY = top + (index / columns) * slotPitch
            val hovered = locationEntry == null && mouseX in slotX until (slotX + slotSize) && mouseY in slotY until (slotY + slotSize)
            graphics.fill(
                slotX,
                slotY,
                slotX + slotSize,
                slotY + slotSize,
                if (hovered) SkyHudTheme.SLOT_HOVER else SkyHudTheme.SLOT_FILLED,
            )
            val stack = entry.displayStack
            graphics.item(stack, slotX + 3, slotY + 3)
            graphics.itemDecorations(font, stack, slotX + 3, slotY + 3, itemGridAmountLabel(entry.totalAmount))
            if (config.staleWarnings && entry.isStale(System.currentTimeMillis(), 86_400_000L)) {
                graphics.text(font, "!", slotX + 15, slotY + 2, 0xFFFFB84D.toInt(), false)
            }
            if (hovered) {
                val tooltip = getTooltipFromItem(minecraft, stack).toMutableList()
                tooltip += Component.empty()
                tooltip += Component.literal(if (entry.locations.size == 1) "Location" else "Locations")
                    .withStyle(ChatFormatting.GRAY)
                entry.locationTooltipLines().forEach { location ->
                    tooltip += Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(location).withStyle(ChatFormatting.AQUA))
                }
                graphics.setTooltipForNextFrame(font, tooltip, stack.tooltipImage, mouseX, mouseY)
            }
            bounds += ItemBounds(entry, slotX, slotY)
        }
        graphics.disableScissor()
        itemBounds = bounds

        when {
            state.loading -> centeredMessage(graphics, "Building profile index…", left, top, right, bottom)
            results.isEmpty() && query.isBlank() -> centeredMessage(graphics, "No items are available from the enabled sources", left, top, right, bottom)
            results.isEmpty() -> centeredMessage(graphics, "No items match '$query'", left, top, right, bottom)
        }
        if (state.failures.isNotEmpty()) {
            val label = "${state.failures.size} source${if (state.failures.size == 1) "" else "s"} unavailable"
            graphics.text(font, label, left, bottom - font.lineHeight, 0xFFFF8E8E.toInt(), false)
        }
        if (maxScroll > 0) drawScrollbar(graphics, right - 3, top, bottom, scrollRows, maxScroll)
    }

    private fun drawLocationPicker(graphics: GuiGraphicsExtractor, entry: ItemSearchEntry, mouseX: Int, mouseY: Int) {
        val modalWidth = 320.coerceAtMost(width - 24)
        val modalHeight = 260.coerceAtMost(height - 24)
        val x = (width - modalWidth) / 2
        val y = (height - modalHeight) / 2
        graphics.fill(0, 0, width, height, 0x88000000.toInt())
        SkyHudTheme.outlinedRoundedRect(graphics, x, y, modalWidth, modalHeight, SkyHudTheme.PANEL, SkyHudTheme.PRIMARY)
        graphics.text(font, entry.name, x + 9, y + 9, SkyHudTheme.TEXT, false)
        graphics.text(font, "Choose a location", x + 9, y + 22, SkyHudTheme.TEXT_MUTED, false)
        val islandContainers = entry.distinctIslandChestContainers()
        showAllBounds = if (islandContainers.size > 1) {
            val button = ButtonBounds(x + modalWidth - 79, y + 8, 70, 20)
            val hovered = mouseX in button.x until (button.x + button.width) && mouseY in button.y until (button.y + button.height)
            SkyHudTheme.outlinedRoundedRect(
                graphics,
                button.x,
                button.y,
                button.width,
                button.height,
                if (hovered) SkyHudTheme.CONTROL_HOVER else SkyHudTheme.CONTROL,
                if (hovered) SkyHudTheme.PRIMARY else SkyHudTheme.BORDER,
            )
            SkyHudControls.centeredText(graphics, font, "SHOW ALL", button.x, button.y, button.width, button.height, SkyHudTheme.TEXT)
            if (hovered) {
                graphics.setTooltipForNextFrame(Component.literal("Highlight all ${islandContainers.size} chests"), mouseX, mouseY)
            }
            button
        } else {
            null
        }
        val listTop = y + 39
        val listBottom = y + modalHeight - 9
        val visibleRows = ((listBottom - listTop) / 31).coerceAtLeast(1)
        val locations = entry.locationsByDescendingAmount()
        val maxScroll = (locations.size - visibleRows).coerceAtLeast(0)
        locationScroll = locationScroll.coerceIn(0, maxScroll)
        val bounds = mutableListOf<LocationBounds>()
        graphics.enableScissor(x + 5, listTop, x + modalWidth - 5, listBottom)
        locations.drop(locationScroll).take(visibleRows).forEachIndexed { index, item ->
            val rowY = listTop + index * 31
            val hovered = mouseX in (x + 8) until (x + modalWidth - 8) && mouseY in rowY until (rowY + 27)
            SkyHudTheme.outlinedRoundedRect(
                graphics,
                x + 8,
                rowY,
                modalWidth - 16,
                27,
                if (hovered) SkyHudTheme.CONTROL_HOVER else SkyHudTheme.CONTROL,
                if (hovered) SkyHudTheme.PRIMARY else SkyHudTheme.BORDER,
            )
            graphics.item(item.stack, x + 13, rowY + 5)
            val amount = "× ${item.amount}"
            val amountX = x + modalWidth - 14 - font.width(amount)
            val labelWidth = (amountX - (x + 35) - 5).coerceAtLeast(1)
            graphics.text(font, clipText(item.location.label, labelWidth), x + 35, rowY + 5, SkyHudTheme.TEXT, false)
            graphics.text(font, amount, amountX, rowY + 5, SkyHudTheme.PRIMARY, false)
            val seen = lastSeenLabel(item)
            graphics.text(font, seen, x + 35, rowY + 16, SkyHudTheme.TEXT_MUTED, false)
            if (hovered) {
                val tooltip = getTooltipFromItem(minecraft, item.stack).toMutableList()
                tooltip += Component.empty()
                tooltip += Component.literal("Location").withStyle(ChatFormatting.GRAY)
                tooltip += Component.literal(item.location.label).withStyle(ChatFormatting.AQUA)
                tooltip += Component.literal("Amount: ${item.amount}").withStyle(ChatFormatting.GRAY)
                graphics.setTooltipForNextFrame(font, tooltip, item.stack.tooltipImage, mouseX, mouseY)
            }
            bounds += LocationBounds(item, x + 8, rowY, modalWidth - 16, 27)
        }
        graphics.disableScissor()
        locationBounds = bounds
        if (maxScroll > 0) drawScrollbar(graphics, x + modalWidth - 5, listTop, listBottom, locationScroll, maxScroll)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (locationEntry != null) {
            if (click.button() != 0) return true
            showAllBounds?.takeIf { click.x.toInt() in it.x until (it.x + it.width) && click.y.toInt() in it.y until (it.y + it.height) }
                ?.let {
                    ItemSearchNavigator.navigateIslandChests(locationEntry.orEmptyIslandChestContainers())
                    locationEntry = null
                    return true
                }
            locationBounds.firstOrNull { click.x.toInt() in it.x until (it.x + it.width) && click.y.toInt() in it.y until (it.y + it.height) }
                ?.let {
                    ItemSearchNavigator.navigate(it.item)
                    locationEntry = null
                    return true
                }
            locationEntry = null
            return true
        }
        if (super.mouseClicked(click, doubled)) return true
        if (click.button() != 0) return false
        val mouseX = click.x.toInt()
        val mouseY = click.y.toInt()
        if (mouseX in (panelX() + 79) until (panelX() + 95) && mouseY in (panelY() + 6) until (panelY() + 22)) {
            remember()
            onClose()
            SkyHudConfigManager.open()
            return true
        }
        if (mouseX in sortX() until (sortX() + 72) && mouseY in (panelY() + 5) until (panelY() + 23)) {
            sort = ItemSearchSort.entries[(sort.ordinal + 1) % ItemSearchSort.entries.size]
            scrollRows = 0
            return true
        }
        if (mouseX in (sortX() + 75) until (sortX() + 95) && mouseY in (panelY() + 5) until (panelY() + 23)) {
            ascending = !ascending
            return true
        }
        categoryBounds.firstOrNull { mouseX in it.x until (it.x + it.width) && mouseY in it.y until (it.y + it.height) }
            ?.let {
                category = it.category
                scrollRows = 0
                remember()
                return true
            }
        itemBounds.firstOrNull { mouseX in it.x until (it.x + slotSize) && mouseY in it.y until (it.y + slotSize) }
            ?.let { bound ->
                if (bound.entry.locations.size == 1) ItemSearchNavigator.navigate(bound.entry.locations.first()) else locationEntry = bound.entry
                return true
            }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (locationEntry != null) {
            locationScroll = (locationScroll - verticalAmount.toInt()).coerceAtLeast(0)
            return true
        }
        scrollRows = (scrollRows - verticalAmount.toInt()).coerceAtLeast(0)
        return true
    }

    override fun onClose() {
        remember()
        ItemSearchController.screenClosed(this)
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false

    private fun remember() = ItemSearchController.remember(query, category)

    private fun centeredMessage(graphics: GuiGraphicsExtractor, message: String, left: Int, top: Int, right: Int, bottom: Int) {
        graphics.text(font, message, left + (right - left - font.width(message)) / 2, top + (bottom - top) / 2, SkyHudTheme.TEXT_MUTED, false)
    }

    private fun drawScrollbar(graphics: GuiGraphicsExtractor, x: Int, top: Int, bottom: Int, position: Int, max: Int) {
        val track = bottom - top
        val thumb = (track / (max + 1)).coerceAtLeast(18)
        val y = top + ((track - thumb) * position / max.coerceAtLeast(1))
        SkyHudTheme.roundedRect(graphics, x, top, 3, track, SkyHudTheme.SCROLLBAR_TRACK)
        SkyHudTheme.roundedRect(graphics, x, y, 3, thumb, SkyHudTheme.SCROLLBAR_THUMB)
    }

    private fun panelWidth(): Int = (width - 20).coerceAtMost(720).coerceAtLeast(320.coerceAtMost(width))
    private fun panelHeight(): Int = (height - 20).coerceAtMost(440).coerceAtLeast(240.coerceAtMost(height))
    private fun panelX(): Int = (width - panelWidth()) / 2
    private fun panelY(): Int = (height - panelHeight()) / 2
    private fun collapsedSidebar(): Boolean = panelWidth() < 520
    private fun contentLeft(): Int = panelX() + if (collapsedSidebar()) narrowSidebarWidth else sidebarWidth
    private fun searchWidth(): Int = (panelWidth() - 305).coerceIn(90, 240)
    private fun searchX(): Int = sortX() - searchWidth() - 8
    private fun sortX(): Int = panelX() + panelWidth() - 103

    private fun clipText(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        var clipped = text
        while (clipped.isNotEmpty() && font.width("$clipped...") > maxWidth) clipped = clipped.dropLast(1)
        return "$clipped..."
    }

    private fun lastSeenLabel(item: SearchableItem): String {
        if (item.origin == ItemDataOrigin.LIVE_MENU || item.origin == ItemDataOrigin.LIVE_PLAYER) return "Live"
        val updated = item.updatedAtEpochMillis ?: return "Last seen unknown"
        val age = (System.currentTimeMillis() - updated).coerceAtLeast(0)
        return when {
            age < 60_000 -> "Seen just now"
            age < 3_600_000 -> "Seen ${age / 60_000}m ago"
            age < 86_400_000 -> "Seen ${age / 3_600_000}h ago"
            else -> "Seen ${age / 86_400_000}d ago"
        }
    }
}

internal fun itemGridAmountLabel(amount: Long): String = when {
    amount >= 1_000_000_000 -> compactGridAmount(amount, 1_000_000_000L, "b")
    amount >= 1_000_000 -> compactGridAmount(amount, 1_000_000L, "m")
    amount >= 1_000 -> compactGridAmount(amount, 1_000L, "k")
    amount > 1 -> amount.toString()
    else -> ""
}

private fun compactGridAmount(amount: Long, divisor: Long, suffix: String): String {
    val scaled = amount.toDouble() / divisor
    val value = if (scaled < 10.0) {
        String.format(Locale.ROOT, "%.1f", scaled).removeSuffix(".0")
    } else {
        (amount / divisor).toString()
    }
    return "$value$suffix"
}

private fun ItemSourceCategory.label(): String = when (this) {
    ItemSourceCategory.ALL -> "All"
    ItemSourceCategory.STORAGE -> "Storage"
    ItemSourceCategory.INVENTORY -> "Inventory"
    ItemSourceCategory.WARDROBE_EQUIPMENT -> "Wardrobe/Equip"
    ItemSourceCategory.SACKS -> "Sacks"
    ItemSourceCategory.ISLAND_CHESTS -> "Island Chests"
    ItemSourceCategory.MUSEUM -> "Museum"
    ItemSourceCategory.RIFT -> "Rift"
    ItemSourceCategory.FORGE -> "Forge"
    ItemSourceCategory.OTHER -> "Other"
}

private fun ItemSearchSort.label(): String = when (this) {
    ItemSearchSort.AMOUNT -> "Amount"
    ItemSearchSort.VALUE -> "Value"
    ItemSearchSort.RARITY -> "Rarity"
    ItemSearchSort.NAME -> "Name"
}

internal fun ItemSearchEntry.distinctIslandChestContainers(): List<List<net.minecraft.core.BlockPos>> =
    IslandChestGeometry.distinctContainers(
        locations.mapNotNull { (it.action as? ItemNavigationAction.IslandChest)?.positions },
    )

private fun ItemSearchEntry?.orEmptyIslandChestContainers(): List<List<net.minecraft.core.BlockPos>> =
    this?.distinctIslandChestContainers().orEmpty()
