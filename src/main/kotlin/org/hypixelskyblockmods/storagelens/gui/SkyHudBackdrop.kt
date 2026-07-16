package org.hypixelskyblockmods.storagelens.gui

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.hypixelskyblockmods.storagelens.platform.RenderTargetCompat

object SkyHudBackdrop {
    data class Region(val x: Int, val y: Int, val width: Int, val height: Int)

    private var snapshot: GpuTexture? = null
    private var snapshotView: GpuTextureView? = null
    private var captureRequested = false

    fun renderPanelBlur(
        graphics: GuiGraphicsExtractor,
        vararg panels: Region,
    ) {
        val target = RenderTargetCompat.mainRenderTarget()
        val source = target.getColorTexture() ?: return
        val view = ensureSnapshot(source) ?: return
        captureRequested = true

        graphics.blurBeforeThisStratum()

        val guiWidth = graphics.guiWidth()
        val guiHeight = graphics.guiHeight()
        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        val clippedPanels = panels.mapNotNull { panel ->
            val left = panel.x.coerceIn(0, guiWidth)
            val top = panel.y.coerceIn(0, guiHeight)
            val right = (panel.x + panel.width).coerceIn(left, guiWidth)
            val bottom = (panel.y + panel.height).coerceIn(top, guiHeight)
            if (right > left && bottom > top) intArrayOf(left, top, right, bottom) else null
        }
        val yEdges = buildSet {
            add(0)
            add(guiHeight)
            clippedPanels.forEach { panel ->
                add(panel[1])
                add(panel[3])
            }
        }.sorted()

        yEdges.zipWithNext().forEach { (bandTop, bandBottom) ->
            var cursor = 0
            clippedPanels
                .filter { panel -> panel[1] < bandBottom && panel[3] > bandTop }
                .map { panel -> panel[0] to panel[2] }
                .sortedBy { interval -> interval.first }
                .forEach { (left, right) ->
                    restoreRegion(graphics, view, sampler, cursor, bandTop, left, bandBottom, guiWidth, guiHeight)
                    cursor = maxOf(cursor, right)
                }
            restoreRegion(graphics, view, sampler, cursor, bandTop, guiWidth, bandBottom, guiWidth, guiHeight)
        }
    }

    fun captureFrame() {
        if (!captureRequested) return
        captureRequested = false

        val source = RenderTargetCompat.mainRenderTarget().getColorTexture() ?: return
        val destination = snapshot ?: return
        if (destination.isClosed || destination.getWidth(0) != source.getWidth(0) || destination.getHeight(0) != source.getHeight(0)) return

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            source,
            destination,
            0,
            0,
            0,
            0,
            0,
            source.getWidth(0),
            source.getHeight(0),
        )
    }

    fun close() {
        captureRequested = false
        snapshotView?.close()
        snapshot?.close()
        snapshotView = null
        snapshot = null
    }

    private fun ensureSnapshot(source: GpuTexture): GpuTextureView? {
        val current = snapshot
        if (
            current != null &&
            !current.isClosed &&
            current.getWidth(0) == source.getWidth(0) &&
            current.getHeight(0) == source.getHeight(0) &&
            current.getFormat() == source.getFormat()
        ) {
            return snapshotView
        }

        close()
        val device = RenderSystem.getDevice()
        val texture = device.createTexture(
            { "StorageLens backdrop snapshot" },
            GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
            source.getFormat(),
            source.getWidth(0),
            source.getHeight(0),
            1,
            1,
        )
        snapshot = texture
        snapshotView = device.createTextureView(texture)
        return snapshotView
    }

    private fun restoreRegion(
        graphics: GuiGraphicsExtractor,
        view: GpuTextureView,
        sampler: com.mojang.blaze3d.textures.GpuSampler,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        guiWidth: Int,
        guiHeight: Int,
    ) {
        if (x1 <= x0 || y1 <= y0) return
        val u0 = x0.toFloat() / guiWidth
        val u1 = x1.toFloat() / guiWidth
        val v0 = 1f - y0.toFloat() / guiHeight
        val v1 = 1f - y1.toFloat() / guiHeight
        graphics.blit(view, sampler, x0, y0, x1, y1, u0, u1, v0, v1)
    }
}
