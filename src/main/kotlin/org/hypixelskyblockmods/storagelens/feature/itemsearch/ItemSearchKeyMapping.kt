package org.hypixelskyblockmods.storagelens.feature.itemsearch

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.hypixelskyblockmods.storagelens.config.SkyHudConfigManager

object ItemSearchKeyMapping {
    private const val TRANSLATION_KEY = "key.storagelens.item_search"
    private const val DEFAULT_KEY = InputConstants.KEY_I

    private lateinit var mapping: KeyMapping
    private var synchronized = false
    private var lastConfigKey = DEFAULT_KEY
    private var lastMappingValue = ""

    fun initialize() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("storagelens", "controls"))
        mapping = KeyMappingHelper.registerKeyMapping(
            KeyMapping(TRANSLATION_KEY, InputConstants.Type.KEYSYM, DEFAULT_KEY, category),
        )
    }

    fun consumeClick(client: Minecraft): Boolean {
        synchronize(client)
        return mapping.consumeClick()
    }

    private fun synchronize(client: Minecraft) {
        val config = SkyHudConfigManager.config.itemSearch
        val bound = KeyMappingHelper.getBoundKeyOf(mapping)
        val mappingValue = mapping.saveString()

        if (!synchronized) {
            when {
                bound.type == InputConstants.Type.KEYSYM && bound.value != DEFAULT_KEY -> {
                    config.keybind = bound.value
                    SkyHudConfigManager.save()
                }
                bound.type == InputConstants.Type.KEYSYM && config.keybind != bound.value -> {
                    applyConfigKey(client, config.keybind)
                }
            }
            lastConfigKey = config.keybind
            lastMappingValue = mapping.saveString()
            synchronized = true
            return
        }

        val configChanged = config.keybind != lastConfigKey
        val mappingChanged = mappingValue != lastMappingValue
        when {
            mappingChanged -> {
                if (bound.type == InputConstants.Type.KEYSYM) {
                    config.keybind = bound.value
                    SkyHudConfigManager.save()
                }
            }
            configChanged -> applyConfigKey(client, config.keybind)
        }
        lastConfigKey = config.keybind
        lastMappingValue = mapping.saveString()
    }

    private fun applyConfigKey(client: Minecraft, key: Int) {
        mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(key))
        KeyMapping.resetMapping()
        client.options.save()
    }
}
