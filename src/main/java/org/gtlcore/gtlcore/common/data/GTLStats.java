package org.gtlcore.gtlcore.common.data;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class GTLStats {

    private static final String PATTERNS_ENCODED_NAME = "patterns_encoded";
    private static final DeferredRegister<ResourceLocation> CUSTOM_STATS = DeferredRegister.create(Registries.CUSTOM_STAT, GTLCore.MOD_ID);

    public static final RegistryObject<ResourceLocation> PATTERNS_ENCODED = CUSTOM_STATS.register(
            PATTERNS_ENCODED_NAME,
            () -> GTLCore.id(PATTERNS_ENCODED_NAME));

    private GTLStats() {}

    public static void register(IEventBus eventBus) {
        CUSTOM_STATS.register(eventBus);
    }

    public static void awardPatternEncoded(ServerPlayer player) {
        player.awardStat(Stats.CUSTOM.get(PATTERNS_ENCODED.get()), 1);
    }
}
