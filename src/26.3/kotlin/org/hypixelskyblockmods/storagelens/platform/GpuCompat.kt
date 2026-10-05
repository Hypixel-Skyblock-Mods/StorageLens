package org.hypixelskyblockmods.storagelens.platform
typealias FilterMode = com.mojang.renderpearl.api.textures.FilterMode
typealias GpuTexture = com.mojang.renderpearl.api.textures.GpuTexture
typealias GpuTextureView = com.mojang.renderpearl.api.textures.GpuTextureView
typealias GpuSampler = com.mojang.renderpearl.api.textures.GpuSampler
object InputCompat {
    val keyboardType = com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD
    fun fromCode(code: Int): com.mojang.blaze3d.platform.InputConstants.Key =
        if (code <= -2) com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(-2 - code)
        else keyboardType.getOrCreate(code)
    fun toCode(key: com.mojang.blaze3d.platform.InputConstants.Key): Int =
        if (key.type == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) -2 - key.value else key.value
    fun supports(key: com.mojang.blaze3d.platform.InputConstants.Key) =
        key.type == keyboardType || key.type == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE
    fun loadCode(code: Int, name: String, existing: Boolean): Int {
        val stableName = name.ifEmpty { if (existing) org.hypixelskyblockmods.storagelens.config.LegacyKeyNames.name(code) else "" }
        if (stableName.isEmpty()) return code
        val key = try {
            com.mojang.blaze3d.platform.InputConstants.getKey(stableName.replace("key.keyboard.keypad.decimal", "key.keyboard.keypad.period"))
        } catch (_: IllegalArgumentException) {
            com.mojang.blaze3d.platform.InputConstants.UNKNOWN
        }
        return toCode(key)
    }
}
