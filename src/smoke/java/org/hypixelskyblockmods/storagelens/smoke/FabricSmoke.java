package org.hypixelskyblockmods.storagelens.smoke;

import java.util.Arrays;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Development-only linkage and production mixin checks, without login or gameplay. */
public final class FabricSmoke implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        if (!Boolean.getBoolean("storagelens.smoke")) throw new IllegalStateException("Development checks only");
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            ClassLoader loader = getClass().getClassLoader();
            String version = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
            String screenOwner = version.startsWith("26.1") ? "net.minecraft.client.Minecraft" : "net.minecraft.client.gui.Gui";
            expectInjected(loader, screenOwner, "storageLens");
            expectInjected(loader, "net.minecraft.client.renderer.GameRenderer", "storageLensCaptureBackdrop");
            expectInjected(loader, "net.minecraft.client.multiplayer.ClientPacketListener", "storageLensObserve");
            expectInjected(loader, "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen", "storageLensHoveredSlot");
            for (String name : new String[]{
                "io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent",
                "io.github.notenoughupdates.moulconfig.platform.MoulConfigRenderContext",
                "io.github.notenoughupdates.moulconfig.platform.MoulConfigPlatform",
                "org.hypixelskyblockmods.storagelens.gui.SkyHudBackdrop"
            }) {
                Class<?> linked = Class.forName(name, false, loader);
                linked.getDeclaredMethods();
                linked.getDeclaredConstructors();
                linked.getDeclaredFields();
                System.out.println("STORAGELENS_SMOKE_LINKED " + name);
            }
            Class.forName("net.minecraft.client.renderer.RenderPipelines", true, loader);
            Class.forName(version.startsWith("26.3") ? "org.lwjgl.sdl.SDLMouse" : "org.lwjgl.glfw.GLFW", false, loader);
            // Exercise the bundled official MoulConfig platform's Identifier conversion.
            Class<?> identifier = Class.forName("net.minecraft.resources.Identifier", true, loader);
            Object value = identifier.getMethod("fromNamespaceAndPath", String.class, String.class)
                .invoke(null, "storagelens", "smoke");
            Class<?> platform = Class.forName("io.github.notenoughupdates.moulconfig.platform.MoulConfigPlatform", true, loader);
            Object wrapped = platform.getMethod("wrap", identifier).invoke(null, value);
            Class<?> resource = Class.forName("io.github.notenoughupdates.moulconfig.common.MyResourceLocation", true, loader);
            Object roundTrip = platform.getMethod("unwrap", resource).invoke(null, wrapped);
            if (!value.equals(roundTrip)) throw new AssertionError("Bundled MoulConfig platform conversion failed");
            System.out.println("STORAGELENS_SMOKE_PASSED " + version);
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }

    private static void expectInjected(ClassLoader loader, String name, String prefix) throws ClassNotFoundException {
        Class<?> target = Class.forName(name, false, loader);
        if (Arrays.stream(target.getDeclaredMethods()).noneMatch(method -> method.getName().contains(prefix))) {
            throw new AssertionError("Production mixin not applied: " + name + " / " + prefix);
        }
        System.out.println("STORAGELENS_SMOKE_TRANSFORMED " + name);
    }
}
