package org.gtlcore.gtlcore.integration.ae2.graph;

import net.minecraft.nbt.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/** Existing task bindings must survive changes to the fingerprint encoder. */
final class PatternFingerprintTest {

    static void run() {
        var random = new Random(93152);
        var context = new PatternFingerprint.Context();
        List<String> strings = new ArrayList<>(List.of("", "plain", "\\", "\"", "'", "\"'", "'\"", "a\\'b\"c", "\n\r\t", "中文🙂"));
        String alphabet = "abc\\\"'\n:;[]{}=中文";
        for (int sample = 0; sample < 200; sample++) {
            var value = new StringBuilder();
            for (int i = 0, length = random.nextInt(80); i < length; i++) value.append(alphabet.charAt(random.nextInt(alphabet.length())));
            strings.add(value.toString());
        }
        for (int sample = 0; sample < strings.size(); sample++) {
            CompoundTag data = new CompoundTag();
            data.putString("text", strings.get(sample));
            data.putByte("byte", (byte) random.nextInt());
            data.putShort("short", (short) random.nextInt());
            data.putInt("int", random.nextInt());
            data.putLong("long", random.nextLong());
            data.putFloat("float", Float.intBitsToFloat(random.nextInt()));
            data.putDouble("double", Double.longBitsToDouble(random.nextLong()));
            data.putByteArray("bytes", new byte[] { -1, 0, 127 });
            data.putIntArray("ints", new int[] { Integer.MIN_VALUE, 0, Integer.MAX_VALUE });
            data.putLongArray("longs", new long[] { Long.MIN_VALUE, 0, Long.MAX_VALUE });
            var compounds = new ListTag();
            for (int fields = 0; fields < 5; fields++) {
                var compound = new CompoundTag();
                for (int i = fields; i > 0; i--) compound.putString("field" + i, strings.get(sample));
                compounds.add(compound);
            }
            data.put("compounds", compounds);
            checkKey(AEFluidKey.of(Fluids.WATER, data), context);
            checkKey(AEItemKey.of(Items.PAPER, data), context);

            // Include Forge capability NBT, which is separate from ordinary item NBT.
            var serialized = new CompoundTag();
            serialized.putString("id", "minecraft:paper");
            serialized.put("tag", data);
            serialized.put("caps", data.copy());
            checkKey(AEItemKey.fromTag(serialized), context);
        }
        for (double value : new double[] { 0.0, -0.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.MIN_VALUE, Double.MAX_VALUE }) {
            CompoundTag data = new CompoundTag();
            data.putDouble("double", value);
            data.putFloat("float", (float) value);
            checkKey(AEFluidKey.of(Fluids.LAVA, data), context);
        }
        CompoundTag pattern = new CompoundTag();
        var inputs = new ListTag();
        var outputs = new ListTag();
        inputs.add(GenericStack.writeTag(new GenericStack(AEItemKey.of(Items.PAPER), 3_000_000_000L)));
        outputs.add(GenericStack.writeTag(new GenericStack(AEItemKey.of(Items.BOOK), 2)));
        pattern.put("in", inputs);
        pattern.put("out", outputs);
        IPatternDetails details = new AEProcessingPattern(AEItemKey.of(Items.PAPER, pattern));
        if (!oldFingerprint(details).equals(context.of(details))) throw new AssertionError("Persisted pattern binding changed");
        details.getOutputs()[0] = new GenericStack(AEItemKey.of(Items.BOOK), 4);
        if (!oldFingerprint(details).equals(context.of(details))) throw new AssertionError("Effective pattern mutation was hidden");
        System.out.println("Fingerprint format: prior encoder matches nested NBT, scalar boundaries, escaping, Forge caps and effective patterns");
    }

    private static void checkKey(AEKey key, PatternFingerprint.Context context) {
        String expected = oldCanonical(key.toTagGeneric());
        if (!expected.equals(PatternFingerprint.key(key)) || !expected.equals(context.key(key)))
            throw new AssertionError("Serialized key changed: " + expected);
    }

    private static String oldFingerprint(IPatternDetails pattern) {
        StringBuilder text = new StringBuilder(pattern.getClass().getName());
        text.append('|').append(oldCanonical(pattern.getDefinition().toTagGeneric())).append('|').append(pattern.supportsPushInputsToExternalInventory());
        for (var input : pattern.getInputs()) {
            text.append(";i:").append(input.getMultiplier());
            for (var possible : input.getPossibleInputs()) {
                text.append('|').append(oldCanonical(possible.what().toTagGeneric())).append(':').append(possible.amount());
                AEKey remaining = input.getRemainingKey(possible.what());
                text.append('>').append(remaining == null ? "-" : oldCanonical(remaining.toTagGeneric()));
            }
        }
        for (var output : pattern.getOutputs()) text.append(";o:").append(oldCanonical(output.what().toTagGeneric())).append(':').append(output.amount());
        return PatternFingerprint.hash(text.toString());
    }

    /** Original canonical format, relying on Minecraft's own scalar SNBT writer. */
    private static String oldCanonical(Tag tag) {
        if (tag instanceof CompoundTag compound) return compound.getAllKeys().stream().sorted(Comparator.naturalOrder())
                .map(key -> key.length() + ":" + key + '=' + oldCanonical(compound.get(key))).collect(Collectors.joining("", "{", "}"));
        if (tag instanceof ListTag list) {
            StringBuilder value = new StringBuilder("[");
            for (Tag entry : list) value.append(oldCanonical(entry)).append(';');
            return value.append(']').toString();
        }
        return tag.getId() + ":" + tag;
    }
}
