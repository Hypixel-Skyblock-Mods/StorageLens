package org.hypixelskyblockmods.storagelens.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import org.hypixelskyblockmods.storagelens.config.SkyHudConfigManager

object SkyHudTheme {
    val transparent: Boolean
        get() = SkyHudConfigManager.config.theme.preset == 1

    val SCREEN_DIM: Int
        get() = if (transparent) 0x00000000 else 0x70000000
    val PANEL: Int
        get() = if (transparent) 0x4C000000 else 0xFF151515.toInt()
    val BACKGROUND: Int
        get() = if (transparent) 0x50000000 else 0xFF181818.toInt()
    val SURFACE: Int
        get() = if (transparent) 0x88101010.toInt() else 0xFF202020.toInt()
    val SURFACE_RAISED: Int
        get() = if (transparent) 0xA0181818.toInt() else 0xFF292929.toInt()
    val EMPTY_SURFACE: Int
        get() = if (transparent) 0x70101010 else 0xFF181818.toInt()
    val BORDER: Int
        get() = if (transparent) 0x80383838.toInt() else 0xFF343434.toInt()
    val PRIMARY: Int
        get() = runCatching {
            val color = SkyHudConfigManager.config.theme.mainColor.getEffectiveColourRGB() and 0x00FFFFFF
            withAlpha(color, if (transparent) 0xB0 else 0xFF)
        }.getOrDefault(0xFF2431A0.toInt())
    val PRIMARY_HOVER: Int
        get() = brighten(PRIMARY, 1.3f)
    val CONTROL: Int
        get() = if (transparent) 0x88242424.toInt() else 0xFF242424.toInt()
    val CONTROL_HOVER: Int
        get() = if (transparent) 0xB0343434.toInt() else 0xFF343434.toInt()
    val DIVIDER: Int
        get() = if (transparent) 0x80383838.toInt() else 0xFF303030.toInt()
    val SCROLLBAR_THUMB: Int
        get() = if (transparent) 0x90303030.toInt() else 0xFF303030.toInt()
    val SCROLLBAR_THUMB_HOVER: Int
        get() = if (transparent) 0xC0404040.toInt() else 0xFF404040.toInt()
    const val TEXT = 0xFFF4F6FA.toInt()
    const val TEXT_MUTED = 0xFF9097A3.toInt()
    val SLOT: Int
        get() = if (transparent) 0x70000000 else 0xFF111111.toInt()
    val SLOT_FILLED: Int
        get() = if (transparent) 0x90080808.toInt() else 0xFF181818.toInt()
    val SLOT_HOVER: Int
        get() = if (transparent) 0xB0282828.toInt() else 0xFF303030.toInt()
    val SCROLLBAR_TRACK: Int
        get() = if (transparent) 0x50000000 else 0xFF242424.toInt()

    fun roundedRect(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        if (width <= 0 || height <= 0) return
        if (width <= 2 || height <= 2) {
            graphics.fill(x, y, x + width, y + height, color)
            return
        }
        graphics.fill(x + 1, y, x + width - 1, y + 1, color)
        graphics.fill(x, y + 1, x + width, y + height - 1, color)
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, color)
    }

    fun outlinedRoundedRect(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fill: Int = SURFACE,
        outline: Int = BORDER,
    ) {
        roundedRect(graphics, x, y, width, height, fill)
        if (width <= 2 || height <= 2) {
            roundedRect(graphics, x, y, width, height, outline)
            return
        }
        graphics.fill(x + 1, y, x + width - 1, y + 1, outline)
        graphics.fill(x, y + 1, x + 1, y + height - 1, outline)
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, outline)
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, outline)
    }

    private fun brighten(color: Int, factor: Float): Int = withAlpha(
        ((component(color, 16) * factor).toInt().coerceAtMost(255) shl 16) or
            ((component(color, 8) * factor).toInt().coerceAtMost(255) shl 8) or
            (component(color, 0) * factor).toInt().coerceAtMost(255),
        (color ushr 24) and 0xFF,
    )

    private fun component(color: Int, shift: Int): Int = (color ushr shift) and 0xFF

    private fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0x00FFFFFF)
}
