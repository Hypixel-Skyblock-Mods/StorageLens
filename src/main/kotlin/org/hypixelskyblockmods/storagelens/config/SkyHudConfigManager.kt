package org.hypixelskyblockmods.storagelens.config

import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.hypixelskyblockmods.storagelens.platform.ScreenCompat
import org.hypixelskyblockmods.storagelens.platform.InputCompat

object SkyHudConfigManager {
    private lateinit var managed: ManagedConfig<SkyHudConfig>

    val config: SkyHudConfig
        get() = managed.instance

    fun initialize() {
        val file = FabricLoader.getInstance().configDir.resolve("storagelens.json").toFile()
        val existing = file.exists()
        managed = ManagedConfig.create(file, SkyHudConfig::class.java)
        val search = config.itemSearch
        search.keybind = InputCompat.loadCode(search.keybind, search.keybindName, existing)
        save()
    }

    fun createScreen(parent: Screen?): Screen = object : MoulConfigScreenComponent(
        Component.literal("StorageLens Settings"),
        GuiContext(GuiElementComponent(managed.getEditor())),
        parent,
    ) {
        override fun removed() {
            save()
            super.removed()
        }
    }

    fun save() {
        if (::managed.isInitialized) {
            config.itemSearch.keybindName = InputCompat.fromCode(config.itemSearch.keybind).name
            managed.saveToFile()
        }
    }

    fun open() {
        ScreenCompat.setScreen(createScreen(ScreenCompat.currentScreen()))
    }
}
