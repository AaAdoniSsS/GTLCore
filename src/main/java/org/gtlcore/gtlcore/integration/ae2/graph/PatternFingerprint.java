package org.gtlcore.gtlcore.integration.ae2.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PatternFingerprint {

    private PatternFingerprint() {}

    public static String of(IPatternDetails pattern) {
        return new Context().of(pattern);
    }

    /** Request-local immutable-key serialization cache, never a cache of mutable pattern semantics. */
    public static final class Context {

        private final Map<AEKey, String> keys = new LinkedHashMap<>(16, 0.75f, true);
        private final MessageDigest digest;
        private int retainedCharacters;

        public Context() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        public String key(AEKey key) {
            String old = keys.get(key);
            if (old != null) return old;
            String value = PatternFingerprint.key(key);
            if (value.length() <= 1_048_576) {
                // A large catalog must not permanently fill this cache with its
                // first definitions. Nearby recipes reuse the current frontier's
                // input/output keys; evict old encodings while keeping both caps.
                while (!keys.isEmpty() && (keys.size() >= 8192 || retainedCharacters + value.length() > 1_048_576)) {
                    var oldest = keys.entrySet().iterator();
                    retainedCharacters -= oldest.next().getValue().length();
                    oldest.remove();
                }
                keys.put(key, value);
                retainedCharacters += value.length();
            }
            return value;
        }

        public String hash(String value) {
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        }

        public String of(IPatternDetails pattern) {
            StringBuilder text = new StringBuilder(pattern.getClass().getName());
            // Encoded pattern NBT is normally unique and can have highly colliding
            // hashes (adjacent input/output variants). Do not fill the reusable
            // material-key LRU with definitions that are serialized only once.
            text.append('|').append(PatternFingerprint.key(pattern.getDefinition())).append('|').append(pattern.supportsPushInputsToExternalInventory());
            for (var input : pattern.getInputs()) {
                text.append(";i:").append(input.getMultiplier());
                for (var possible : input.getPossibleInputs()) {
                    text.append('|').append(key(possible.what())).append(':').append(possible.amount());
                    AEKey remaining = input.getRemainingKey(possible.what());
                    text.append('>').append(remaining == null ? "-" : key(remaining));
                }
            }
            for (var output : pattern.getOutputs()) text.append(";o:").append(key(output.what())).append(':').append(output.amount());
            return hash(text.toString());
        }
    }

    public static String key(AEKey key) {
        return canonical(key.toTagGeneric());
    }

    private static String canonical(Tag tag) {
        StringBuilder value = new StringBuilder();
        canonical(tag, value);
        return value.toString();
    }

    private static void canonical(Tag tag, StringBuilder value) {
        if (tag instanceof CompoundTag compound) {
            value.append('{');
            String[] keys = compound.getAllKeys().toArray(String[]::new);
            Arrays.sort(keys);
            for (String key : keys) {
                value.append(key.length()).append(':').append(key).append('=');
                canonical(compound.get(key), value);
            }
            value.append('}');
        } else if (tag instanceof ListTag list) {
            value.append('[');
            for (Tag entry : list) {
                canonical(entry, value);
                value.append(';');
            }
            value.append(']');
        } else value.append(tag.getId()).append(':').append(tag);
    }

    public static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
