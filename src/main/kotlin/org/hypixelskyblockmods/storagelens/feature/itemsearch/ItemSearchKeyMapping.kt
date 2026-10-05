package org.hypixelskyblockmods.storagelens.feature.itemsearch

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.hypixelskyblockmods.storagelens.config.SkyHudConfigManager
import org.hypixelskyblockmods.storagelens.platform.InputCompat

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
            KeyMapping(TRANSLATION_KEY, InputCompat.keyboardType, DEFAULT_KEY, category),
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
                InputCompat.supports(bound) && bound.value != DEFAULT_KEY -> {
                    config.keybind = InputCompat.toCode(bound)
                    SkyHudConfigManager.save()
                }
                InputCompat.supports(bound) && config.keybind != InputCompat.toCode(bound) -> {
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
                if (InputCompat.supports(bound)) {
                    config.keybind = InputCompat.toCode(bound)
                    SkyHudConfigManager.save()
                }
            }
            configChanged -> applyConfigKey(client, config.keybind)
        }
        lastConfigKey = config.keybind
        lastMappingValue = mapping.saveString()
    }

    private fun applyConfigKey(client: Minecraft, key: Int) {
        mapping.setKey(InputCompat.fromCode(key))
        KeyMapping.resetMapping()
        client.options.save()
    }
}
