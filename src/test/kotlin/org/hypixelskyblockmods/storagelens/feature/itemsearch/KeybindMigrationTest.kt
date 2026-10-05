package org.hypixelskyblockmods.storagelens.feature.itemsearch

import com.mojang.blaze3d.platform.InputConstants
import org.hypixelskyblockmods.storagelens.config.LegacyKeyNames
import org.hypixelskyblockmods.storagelens.platform.InputCompat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeybindMigrationTest {
    @Test
    fun `old default and custom GLFW bindings retain their meaning`() {
        assertEquals(InputConstants.KEY_I, InputCompat.loadCode(73, "", true))
        assertEquals("key.keyboard.a", LegacyKeyNames.name(65))
        assertEquals("key.keyboard.keypad.period", LegacyKeyNames.name(330))
        assertEquals("key.keyboard.unknown", LegacyKeyNames.name(9000))
    }

    @Test
    fun `stable names override backend-specific numbers`() {
        val key = InputConstants.getKey("key.keyboard.a")
        assertEquals(InputCompat.toCode(key), InputCompat.loadCode(73, key.name, true))
        assertEquals(key, InputCompat.fromCode(InputCompat.toCode(key)))
        val mouse = InputConstants.getKey("key.mouse.left")
        assertEquals(mouse, InputCompat.fromCode(InputCompat.toCode(mouse)))
        assertEquals(InputCompat.toCode(mouse), InputCompat.loadCode(73, mouse.name, true))
    }

    @Test
    fun `fresh defaults use the current backend without migration`() {
        assertEquals(InputConstants.KEY_I, InputCompat.loadCode(InputConstants.KEY_I, "", false))
    }
}
