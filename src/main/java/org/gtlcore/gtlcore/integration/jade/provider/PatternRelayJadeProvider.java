package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.patternrelay.PatternRelayPart;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import appeng.blockentity.networking.CableBusBlockEntity;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.IElement;

public final class PatternRelayJadeProvider implements IBlockComponentProvider {

    private static final ResourceLocation UID = GTLCore.id("me_pattern_relay_mode");
    private static final String MODE_TOOLTIP_KEY = "tooltip.gtlcore.me_pattern_relay.mode";
    private static final ResourceLocation SUPPLIER_ICON = GTLCore.id("textures/block/me_pattern_relay_supplier.png");
    private static final ResourceLocation ACCESS_ICON = GTLCore.id("textures/block/me_pattern_relay_access.png");

    @Override
    public IElement getIcon(BlockAccessor accessor, IPluginConfig config, IElement defaultIcon) {
        PatternRelayPart relay = getTargetedRelay(accessor);
        if (relay == null) {
            return defaultIcon;
        }

        return new RelayTextureElement(relay.isAccessMode() ? ACCESS_ICON : SUPPLIER_ICON);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        PatternRelayPart relay = getTargetedRelay(accessor);
        if (relay != null) {
            tooltip.add(Component.translatable(
                    MODE_TOOLTIP_KEY,
                    Component.translatable(relay.getModeNameTranslationKey()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static @Nullable PatternRelayPart getTargetedRelay(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof CableBusBlockEntity cableBus)) {
            return null;
        }

        BlockHitResult hitResult = accessor.getHitResult();
        if (hitResult == null) {
            return null;
        }

        Vec3 hitInBlock = hitResult.getLocation().subtract(Vec3.atLowerCornerOf(accessor.getPosition()));
        return cableBus.getCableBus().selectPartLocal(hitInBlock).part instanceof PatternRelayPart relay ? relay : null;
    }

    private static final class RelayTextureElement extends Element {

        private static final float ELEMENT_SIZE = 18.0F;
        private static final int TEXTURE_SIZE = 16;

        private final ResourceLocation texture;

        private RelayTextureElement(ResourceLocation texture) {
            this.texture = texture;
        }

        @Override
        public Vec2 getSize() {
            return new Vec2(ELEMENT_SIZE, ELEMENT_SIZE);
        }

        @Override
        public void render(GuiGraphics graphics, float x, float y, float maxX, float alpha) {
            graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.blit(texture, (int) x + 1, (int) y + 1, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE,
                    TEXTURE_SIZE, TEXTURE_SIZE);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
