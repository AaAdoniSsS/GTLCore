package org.gtlcore.gtlcore.mixin.gtm.registry;

import org.gtlcore.gtlcore.api.data.tag.GTLItemTags;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.core.MixinHelpers;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(MixinHelpers.class)
public abstract class GTDynamicTagsMixin {

    private static final String TAG_SOURCE = "GTLCore dynamic siftable ore tags";

    @Inject(method = "generateGTDynamicTags", at = @At("TAIL"), remap = false)
    private static <T> void gtlcore$addSiftableOreItems(
                                                        Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags,
                                                        Registry<T> registry,
                                                        CallbackInfo ci) {
        if (registry != BuiltInRegistries.ITEM) {
            return;
        }

        for (Material material : GTCEuAPI.materialManager.getRegisteredMaterials()) {
            if (!gtlcore$isSiftableOre(material)) {
                continue;
            }
            for (TagPrefix orePrefix : TagPrefix.ORES.keySet()) {
                gtlcore$addMaterialItem(tags, orePrefix, material);
            }
            gtlcore$addMaterialItem(tags, TagPrefix.rawOre, material);
            gtlcore$addMaterialItem(tags, TagPrefix.crushed, material);
        }
    }

    private static boolean gtlcore$isSiftableOre(Material material) {
        return material.hasProperty(PropertyKey.ORE) &&
                material.hasProperty(PropertyKey.GEM) &&
                !ChemicalHelper.get(TagPrefix.crushedPurified, material).isEmpty();
    }

    private static void gtlcore$addMaterialItem(
                                                Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags,
                                                TagPrefix prefix,
                                                Material material) {
        ItemStack stack = ChemicalHelper.get(prefix, material);
        if (stack.isEmpty()) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return;
        }

        tags.computeIfAbsent(GTLItemTags.SIFTABLES.location(), ignored -> new ArrayList<>())
                .add(new TagLoader.EntryWithSource(TagEntry.element(itemId), TAG_SOURCE));
    }
}
