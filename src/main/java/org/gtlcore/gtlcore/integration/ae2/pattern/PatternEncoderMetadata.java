package org.gtlcore.gtlcore.integration.ae2.pattern;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

public final class PatternEncoderMetadata {

    private static final String ROOT_KEY = GTLCore.MOD_ID;
    private static final String ENCODER_ID_KEY = "patternEncoderId";
    private static final String ENCODER_NAME_KEY = "patternEncoderName";

    private PatternEncoderMetadata() {}

    public static void writeEncoder(ItemStack patternStack, UUID encoderId, String encoderName) {
        if (patternStack.isEmpty()) {
            return;
        }

        CompoundTag tag = patternStack.getOrCreateTag();
        CompoundTag gtlcoreTag = tag.contains(ROOT_KEY, Tag.TAG_COMPOUND) ?
                tag.getCompound(ROOT_KEY) :
                new CompoundTag();
        gtlcoreTag.putUUID(ENCODER_ID_KEY, encoderId);
        if (encoderName != null && !encoderName.isBlank()) {
            gtlcoreTag.putString(ENCODER_NAME_KEY, encoderName);
        } else {
            gtlcoreTag.remove(ENCODER_NAME_KEY);
        }
        tag.put(ROOT_KEY, gtlcoreTag);
    }

    public static Optional<Encoder> readEncoder(ItemStack patternStack) {
        CompoundTag tag = patternStack.getTag();
        if (tag == null || !tag.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        CompoundTag gtlcoreTag = tag.getCompound(ROOT_KEY);
        if (!gtlcoreTag.hasUUID(ENCODER_ID_KEY)) {
            return Optional.empty();
        }
        String encoderName = gtlcoreTag.contains(ENCODER_NAME_KEY, Tag.TAG_STRING) ?
                gtlcoreTag.getString(ENCODER_NAME_KEY) :
                "";
        return Optional.of(new Encoder(gtlcoreTag.getUUID(ENCODER_ID_KEY), encoderName));
    }

    public record Encoder(UUID id, String name) {

        public String displayName() {
            return name.isBlank() ? id.toString() : name;
        }
    }
}
