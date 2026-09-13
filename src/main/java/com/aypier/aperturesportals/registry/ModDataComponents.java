package com.aypier.aperturesportals.registry;

import com.aypier.aperturesportals.AperturesPortals;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Create a Deferred Register to hold custom DataComponentTypes for the "apertures_portals" namespace
public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AperturesPortals.MODID);

    // Stores which texture state the Portal Gun should show: 0 = default, 1 = blue, 2 = orange
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PORTAL_GUN_COLOR =
            DATA_COMPONENTS.register("portal_gun_color", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());
}