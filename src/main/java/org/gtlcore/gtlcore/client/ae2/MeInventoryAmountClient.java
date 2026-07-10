package org.gtlcore.gtlcore.client.ae2;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.wireless.MeInventoryAmountPackets;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;

import java.util.Optional;
import java.util.OptionalLong;

@Mod.EventBusSubscriber(modid = GTLCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MeInventoryAmountClient {

    private static final int MAX_CACHE_ENTRIES = 256;
    private static final long AVAILABLE_LIFETIME_TICKS = 20;
    private static final long UNAVAILABLE_LIFETIME_TICKS = 40;
    private static final long PENDING_TIMEOUT_TICKS = 100;
    private static final String TOOLTIP_TRANSLATION_KEY = "tooltip.gtlcore.me_network_inventory";
    private static final MeInventoryAmountCache<AEKey> CACHE = new MeInventoryAmountCache<>(
            MAX_CACHE_ENTRIES,
            AVAILABLE_LIFETIME_TICKS,
            UNAVAILABLE_LIFETIME_TICKS,
            PENDING_TIMEOUT_TICKS);

    private MeInventoryAmountClient() {}

    public static OptionalLong getAmount(AEKey key) {
        if (!(key instanceof AEItemKey || key instanceof AEFluidKey)) {
            return OptionalLong.empty();
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return OptionalLong.empty();
        }
        return CACHE.getOrRequest(key, level.getGameTime(), MeInventoryAmountPackets::sendRequest);
    }

    public static Optional<Component> getTooltip(AEKey key) {
        OptionalLong amount = getAmount(key);
        if (amount.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Component.translatable(
                TOOLTIP_TRANSLATION_KEY,
                key.formatAmount(amount.getAsLong(), AmountFormat.FULL))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    public static void receive(MeInventoryAmountPackets.Response packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            CACHE.receive(packet.key(), packet.available(), packet.amount(), level.getGameTime());
        }
    }

    public static void clear() {
        CACHE.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clear();
        }
    }
}
