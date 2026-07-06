package org.gtlcore.gtlcore.client.gui;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

/**
 * @author EasterFG on 2024/9/13
 */
public interface PatterEncodingTermMenuModify {

    default void gTLCore$modifyPatter(Integer value) {}

    default void gTLCore$quickUploadPattern() {}

    default void gTLCore$undoQuickUploadPattern() {}

    default void gTLCore$setQuickUploadRecipeType(@Nullable ResourceLocation recipeTypeId) {}

    default void gTLCore$selectQuickUploadTarget(int index) {}
}
