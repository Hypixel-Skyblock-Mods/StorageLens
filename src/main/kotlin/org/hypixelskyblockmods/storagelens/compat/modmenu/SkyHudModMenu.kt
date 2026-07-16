package org.hypixelskyblockmods.storagelens.compat.modmenu

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import org.hypixelskyblockmods.storagelens.config.SkyHudConfigManager

class SkyHudModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent -> SkyHudConfigManager.createScreen(parent) }
}
