package org.gtlcore.gtlcore.client.renderer.machine;

import org.gtlcore.gtlcore.mixin.mc.client.AnimationBufferSourceAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

/** Recognizes world-owned buffers without calling getBuffer, flushing or changing render order. */
final class HarmonyWorldBuffers {

    private static final ClassValue<MethodHandle> ORIGINAL_BUFFER = new ClassValue<>() {

        @Override
        protected MethodHandle computeValue(Class<?> type) {
            if (!type.getName().equals("me.jellysquid.mods.sodium.client.render.vertex.buffer.SodiumBufferBuilder")) return null;
            try {
                return MethodHandles.publicLookup().unreflect(type.getMethod("getOriginalBufferBuilder"))
                        .asType(MethodType.methodType(BufferBuilder.class, VertexConsumer.class));
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }
    };

    private MultiBufferSource.BufferSource source;
    private final Map<BufferBuilder, Object> segments = new IdentityHashMap<>();
    private Field segmentType;

    boolean owns(VertexConsumer consumer, RenderType type) {
        BufferBuilder buffer = unwrap(consumer);
        if (buffer == null) return false;
        var current = Minecraft.getInstance().renderBuffers().bufferSource();
        if (current != source) {
            clear();
            source = current;
            discoverSegments();
        }
        var access = (AnimationBufferSourceAccessor) current;
        if (buffer == access.gtlcore$getAnimationFixedBuffers().getOrDefault(type, access.gtlcore$getAnimationBuilder())) return true;
        Object segment = segments.get(buffer);
        if (segment != null && segmentType != null) {
            try {
                // Wrapped render types can carry material/outline state: leave those to Oculus.
                return segmentType.get(segment) == type;
            } catch (IllegalAccessException exception) {
                segments.clear();
            }
        }
        return false;
    }

    private void discoverSegments() {
        // Oculus swaps in its batched source only during world rendering, even without a shader
        // pack. These private fields are optional compatibility, not required linkage to Oculus.
        if (!source.getClass().getName().equals("net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource")) return;
        try {
            Field builders = source.getClass().getDeclaredField("builders");
            builders.setAccessible(true);
            for (Object segment : (Object[]) builders.get(source)) {
                Field buffer = segment.getClass().getDeclaredField("buffer");
                buffer.setAccessible(true);
                segmentType = segment.getClass().getDeclaredField("currentType");
                segmentType.setAccessible(true);
                segments.put((BufferBuilder) buffer.get(segment), segment);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // An incompatible optional renderer still gets the complete original animation.
            segments.clear();
            segmentType = null;
        }
    }

    private static BufferBuilder unwrap(VertexConsumer consumer) {
        if (consumer instanceof BufferBuilder builder) return builder;
        // Embeddium replaces its consumer on format changes (GUI/world transitions included).
        // Cache the public accessor, not a consumer instance; never unwrap outlines or decals.
        var getter = ORIGINAL_BUFFER.get(consumer.getClass());
        if (getter == null) return null;
        try {
            return (BufferBuilder) getter.invokeExact(consumer);
        } catch (Throwable exception) {
            if (exception instanceof Error error) throw error;
            return null;
        }
    }

    void clear() {
        source = null;
        segments.clear();
        segmentType = null;
    }
}
