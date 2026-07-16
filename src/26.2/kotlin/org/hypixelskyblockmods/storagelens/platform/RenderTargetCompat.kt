package org.hypixelskyblockmods.storagelens.platform

import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft

object RenderTargetCompat {
    fun mainRenderTarget(): RenderTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget()
}
