package org.gtlcore.gtlcore.client.renderer.item;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;

import com.lowdragmc.lowdraglib.client.model.ModelFactory;
import com.lowdragmc.lowdraglib.client.renderer.IItemRendererProvider;
import com.lowdragmc.lowdraglib.client.renderer.IRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;

/**
 * Draws what a virtual ingredient stands for, with the wrapper's own sprite laid over it as a frame.
 * <p>
 * The payload is the main icon rather than a badge: a pattern encodes the wrapper, so a stack of them is otherwise
 * indistinguishable at a glance. Fluid payloads are drawn as a flat sprite instead of borrowing their bucket, because
 * a bucket is itself a legitimate item payload and the two must not look alike.
 */
public class VirtualIngredientRenderer implements IRenderer {

    public static final VirtualIngredientRenderer INSTANCE = new VirtualIngredientRenderer();

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderItem(ItemStack stack, ItemDisplayContext transformType,
                           boolean leftHand, PoseStack poseStack,
                           MultiBufferSource buffer, int combinedLight,
                           int combinedOverlay, BakedModel model) {
        Minecraft mc = Minecraft.getInstance();
        AEItemKey payloadItem = VirtualIngredientBehavior.payloadItemKey(stack);
        AEFluidKey payloadFluid = VirtualIngredientBehavior.payloadFluidKey(stack);

        poseStack.pushPose();

        if (payloadItem != null) {
            ItemStack payload = payloadItem.toStack(1);
            BakedModel payloadModel = mc.getItemRenderer().getModel(payload, mc.level, mc.player, 0);
            mc.getItemRenderer().render(payload, transformType, leftHand, poseStack, buffer, combinedLight,
                    combinedOverlay, payloadModel);
        } else if (payloadFluid == null) {
            // Nothing configured yet, so the wrapper's own model is all there is to show. Rendering our own stack
            // dispatches straight back into this renderer, so the provider has to be muted for the duration.
            BakedModel own = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
            IItemRendererProvider.disabled.set(true);
            try {
                mc.getItemRenderer().render(stack, transformType, leftHand, poseStack, buffer, combinedLight,
                        combinedOverlay, own);
            } finally {
                IItemRendererProvider.disabled.set(false);
            }
        }

        if (transformType == ItemDisplayContext.GUI) {
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            if (payloadFluid != null) {
                var extensions = IClientFluidTypeExtensions.of(payloadFluid.getFluid());
                blit(poseStack, buffer, combinedLight,
                        ModelFactory.getBlockSprite(extensions.getStillTexture()), extensions.getTintColor());
            }
            blit(poseStack, buffer, combinedLight,
                    ModelFactory.getBlockSprite(GTLCore.id("item/virtual_ingredient")), -1);
        }

        poseStack.popPose();
    }

    /**
     * Emits the quad into the same buffer source the item itself was drawn with.
     * <p>
     * Drawing it immediately with a Tesselator instead puts it outside that batch, which leaves its ordering against
     * the item at the mercy of whoever flushes the batch. That differs between screens, so the overlay would appear in
     * some slots and vanish in others.
     */
    @OnlyIn(Dist.CLIENT)
    private static void blit(PoseStack poseStack, MultiBufferSource buffer, int light,
                             TextureAtlasSprite sprite, int tint) {
        int a = tint >> 24 & 0xFF;
        int r = tint >> 16 & 0xFF;
        int g = tint >> 8 & 0xFF;
        int b = tint & 0xFF;
        // A tint with no alpha means the sprite carries its own colour; multiplying by zero would erase it.
        if (a == 0) {
            a = 0xFF;
            r = 0xFF;
            g = 0xFF;
            b = 0xFF;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.text(InventoryMenu.BLOCK_ATLAS));
        float minU = sprite.getU0();
        float maxU = sprite.getU1();
        float minV = sprite.getV0();
        float maxV = sprite.getV1();
        Matrix4f pos = poseStack.last().pose();
        consumer.vertex(pos, 1, 1, 0).color(r, g, b, a).uv(maxU, minV).uv2(light).endVertex();
        consumer.vertex(pos, 0, 1, 0).color(r, g, b, a).uv(minU, minV).uv2(light).endVertex();
        consumer.vertex(pos, 0, 0, 0).color(r, g, b, a).uv(minU, maxV).uv2(light).endVertex();
        consumer.vertex(pos, 1, 0, 0).color(r, g, b, a).uv(maxU, maxV).uv2(light).endVertex();
    }
}
