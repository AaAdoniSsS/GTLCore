package org.gtlcore.gtlcore.client;

import org.gtlcore.gtlcore.client.renderer.machine.HarmonyAnimationRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientUtil {

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static BakedModel getBakedModel(ResourceLocation resourceLocation) {
        return mc().getModelManager().getModel(resourceLocation);
    }

    public static BlockRenderDispatcher blockRenderer() {
        return mc().getBlockRenderer();
    }

    public static ModelBlockRenderer modelRenderer() {
        return HarmonyAnimationRenderer.modelRenderer(blockRenderer().getModelRenderer());
    }
}
