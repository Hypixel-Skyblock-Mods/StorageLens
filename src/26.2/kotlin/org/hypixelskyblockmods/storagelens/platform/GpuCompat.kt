package org.hypixelskyblockmods.storagelens.platform
typealias FilterMode = com.mojang.blaze3d.textures.FilterMode
typealias GpuTexture = com.mojang.blaze3d.textures.GpuTexture
typealias GpuTextureView = com.mojang.blaze3d.textures.GpuTextureView
typealias GpuSampler = com.mojang.blaze3d.textures.GpuSampler
object InputCompat {
    val keyboardType = com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
    fun fromCode(code: Int) = if (code in 0..9) com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(code) else keyboardType.getOrCreate(code)
    fun toCode(key: com.mojang.blaze3d.platform.InputConstants.Key) = key.value
    fun supports(key: com.mojang.blaze3d.platform.InputConstants.Key) = key.type == keyboardType || key.type == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE
    fun loadCode(code: Int, name: String, existing: Boolean): Int {
        val stableName = name
        if (stableName.isEmpty()) return code
        val key = try {
            com.mojang.blaze3d.platform.InputConstants.getKey(stableName.replace("key.keyboard.keypad.decimal", "key.keyboard.keypad.period"))
        } catch (_: IllegalArgumentException) {
            com.mojang.blaze3d.platform.InputConstants.UNKNOWN
        }
        return toCode(key)
    }
}
