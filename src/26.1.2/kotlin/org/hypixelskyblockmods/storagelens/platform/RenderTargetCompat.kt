package org.hypixelskyblockmods.storagelens.platform

import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft

object RenderTargetCompat {
    fun mainRenderTarget(): RenderTarget = Minecraft.getInstance().mainRenderTarget
    fun createSnapshotTexture(source: GpuTexture, label: String): GpuTexture =
        com.mojang.blaze3d.systems.RenderSystem.getDevice().createTexture(
            { label }, GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
            source.getFormat(), source.getWidth(0), source.getHeight(0), 1, 1,
        )}
