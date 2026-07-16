package org.hypixelskyblockmods.storagelens.platform

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object ScreenCompat {
    fun currentScreen(): Screen? = Minecraft.getInstance().screen

    fun setScreen(screen: Screen?) {
        Minecraft.getInstance().setScreen(screen)
    }
}
