package com.aypier.aperturesportals.client;

import com.aypier.aperturesportals.AperturesPortals;
import com.aypier.aperturesportals.registry.ModDataComponents;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// Registers client-only setup: the item property for texture overrides, and the render layer
// for the portal blocks. value = Dist.CLIENT keeps this class from being touched on a dedicated server.
// This class self-registers via the annotation below - no changes needed elsewhere for this to run.
@EventBusSubscriber(modid = AperturesPortals.MODID, value = Dist.CLIENT)
public class ClientSetupEvents {
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    AperturesPortals.PORTAL_GUN.get(),
                    ResourceLocation.fromNamespaceAndPath(AperturesPortals.MODID, "color_state"),
                    (stack, level, entity, seed) -> stack.getOrDefault(ModDataComponents.PORTAL_GUN_COLOR.get(), 0) / 2.0f
            );

            // Without this, the block renders fully opaque no matter what alpha is in the PNG.
            ItemBlockRenderTypes.setRenderLayer(AperturesPortals.PORTAL_BLUE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(AperturesPortals.PORTAL_ORANGE.get(), RenderType.translucent());
        });
    }
}