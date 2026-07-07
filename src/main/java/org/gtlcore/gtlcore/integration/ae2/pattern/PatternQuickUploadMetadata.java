package org.gtlcore.gtlcore.integration.ae2.pattern;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PatternQuickUploadMetadata {

    private static final String ROOT_KEY = "gtlcore";
    private static final String RECIPE_TYPES_KEY = "patternQuickUploadRecipeTypes";

    private PatternQuickUploadMetadata() {}

    public static void writeRecipeTypes(ItemStack patternStack, Collection<GTRecipeType> recipeTypes) {
        if (patternStack.isEmpty()) {
            return;
        }

        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (GTRecipeType recipeType : recipeTypes) {
            if (recipeType != null && isValidRecipeTypeId(recipeType.registryName)) {
                seen.add(recipeType.registryName);
            }
        }
        writeRecipeTypeIds(patternStack, seen);
    }

    public static void writeRecipeTypeId(ItemStack patternStack, ResourceLocation recipeTypeId) {
        if (!isValidRecipeTypeId(recipeTypeId)) {
            writeRecipeTypeIds(patternStack, List.of());
            return;
        }
        writeRecipeTypeIds(patternStack, List.of(recipeTypeId));
    }

    public static void writeRecipeTypeIds(ItemStack patternStack, Collection<ResourceLocation> recipeTypeIds) {
        if (patternStack.isEmpty()) {
            return;
        }

        ListTag recipeTypeTags = new ListTag();
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (ResourceLocation recipeTypeId : recipeTypeIds) {
            if (isValidRecipeTypeId(recipeTypeId) && seen.add(recipeTypeId)) {
                recipeTypeTags.add(StringTag.valueOf(recipeTypeId.toString()));
            }
        }
        if (recipeTypeTags.isEmpty()) {
            removeRecipeTypes(patternStack);
            return;
        }

        CompoundTag tag = patternStack.getOrCreateTag();
        CompoundTag gtlcoreTag = tag.contains(ROOT_KEY, Tag.TAG_COMPOUND) ?
                tag.getCompound(ROOT_KEY) :
                new CompoundTag();
        gtlcoreTag.put(RECIPE_TYPES_KEY, recipeTypeTags);
        tag.put(ROOT_KEY, gtlcoreTag);
    }

    public static Set<ResourceLocation> readRecipeTypeIds(ItemStack patternStack) {
        CompoundTag tag = patternStack.getTag();
        if (tag == null || !tag.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return Set.of();
        }
        CompoundTag gtlcoreTag = tag.getCompound(ROOT_KEY);
        if (!gtlcoreTag.contains(RECIPE_TYPES_KEY, Tag.TAG_LIST)) {
            return Set.of();
        }

        ListTag recipeTypeIds = gtlcoreTag.getList(RECIPE_TYPES_KEY, Tag.TAG_STRING);
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (int i = 0; i < recipeTypeIds.size(); i++) {
            ResourceLocation id = parseRecipeTypeId(recipeTypeIds.getString(i));
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    public static ResourceLocation parseRecipeTypeId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            ResourceLocation recipeTypeId = new ResourceLocation(value);
            if (!isValidRecipeTypeId(recipeTypeId)) {
                return null;
            }
            return recipeTypeId;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Component recipeTypeName(ResourceLocation recipeTypeId) {
        return Component.translatable(recipeTypeId.getNamespace() + "." + recipeTypeId.getPath());
    }

    private static boolean isValidRecipeTypeId(ResourceLocation recipeTypeId) {
        return recipeTypeId != null && !recipeTypeId.getNamespace().isEmpty() && !recipeTypeId.getPath().isEmpty();
    }

    private static void removeRecipeTypes(ItemStack patternStack) {
        CompoundTag tag = patternStack.getTag();
        if (tag == null || !tag.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag gtlcoreTag = tag.getCompound(ROOT_KEY);
        gtlcoreTag.remove(RECIPE_TYPES_KEY);
        if (gtlcoreTag.isEmpty()) {
            tag.remove(ROOT_KEY);
        } else {
            tag.put(ROOT_KEY, gtlcoreTag);
        }
    }
}
