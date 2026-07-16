package org.hypixelskyblockmods.storagelens.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

object SkyHudControls {
    const val SETTINGS_BUTTON_SIZE = 16

    fun centeredText(
        graphics: GuiGraphicsExtractor,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        graphics.text(
            font,
            text,
            x + (width - font.width(text)) / 2,
            y + (height - font.lineHeight) / 2,
            color,
            false,
        )
    }

    fun settingsButton(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        x: Int,
        y: Int,
    ) {
        val size = SETTINGS_BUTTON_SIZE
        val hovered = mouseX in x until (x + size) && mouseY in y until (y + size)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            x,
            y,
            size,
            size,
            if (hovered) SkyHudTheme.CONTROL_HOVER else SkyHudTheme.CONTROL,
            SkyHudTheme.PRIMARY,
        )
        drawSettingsIcon(graphics, x + 2, y + 2, SkyHudTheme.TEXT)
        if (hovered) graphics.setTooltipForNextFrame(Component.literal("Open StorageLens settings"), mouseX, mouseY)
    }

    private fun drawSettingsIcon(graphics: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        // Compact pixel rendering of Tabler's outlined settings cog.
        graphics.fill(x + 5, y, x + 7, y + 3, color)
        graphics.fill(x + 5, y + 9, x + 7, y + 12, color)
        graphics.fill(x, y + 5, x + 3, y + 7, color)
        graphics.fill(x + 9, y + 5, x + 12, y + 7, color)
        graphics.fill(x + 2, y + 2, x + 4, y + 4, color)
        graphics.fill(x + 8, y + 2, x + 10, y + 4, color)
        graphics.fill(x + 2, y + 8, x + 4, y + 10, color)
        graphics.fill(x + 8, y + 8, x + 10, y + 10, color)
        graphics.fill(x + 4, y + 3, x + 8, y + 4, color)
        graphics.fill(x + 3, y + 4, x + 4, y + 8, color)
        graphics.fill(x + 8, y + 4, x + 9, y + 8, color)
        graphics.fill(x + 4, y + 8, x + 8, y + 9, color)
        graphics.fill(x + 5, y + 5, x + 7, y + 7, color)
    }
}
