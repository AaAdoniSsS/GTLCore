package org.gtlcore.gtlcore.mixin.mc.client;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MultiBufferSource.BufferSource.class)
public interface AnimationBufferSourceAccessor {

    @Accessor("builder")
    BufferBuilder gtlcore$getAnimationBuilder();

    @Accessor("fixedBuffers")
    Map<RenderType, BufferBuilder> gtlcore$getAnimationFixedBuffers();
}
