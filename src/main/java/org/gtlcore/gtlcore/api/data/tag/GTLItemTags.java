package org.gtlcore.gtlcore.api.data.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class GTLItemTags {

    public static final TagKey<Item> SIFTABLES = TagKey.create(
            Registries.ITEM,
            new ResourceLocation("forge", "siftables"));

    private GTLItemTags() {}
}
