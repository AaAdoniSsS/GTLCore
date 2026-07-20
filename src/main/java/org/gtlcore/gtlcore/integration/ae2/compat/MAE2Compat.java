package org.gtlcore.gtlcore.integration.ae2.compat;

import org.gtlcore.gtlcore.integration.ae2.crafting.IPatternProviderAutoExpand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderTarget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public final class MAE2Compat {

    private static final Logger LOGGER = LogManager.getLogger(MAE2Compat.class);

    private static final String PATTERN_P2P_TUNNEL_LOGIC = "stone.mae2.parts.p2p.PatternP2PTunnelLogic";
    private static final String PATTERN_P2P_TUNNEL = "stone.mae2.parts.p2p.PatternP2PTunnelLogic$PatternP2PTunnel";
    private static final String PATTERN_P2P_TARGET = "stone.mae2.parts.p2p.PatternP2PTunnelLogic$Target";
    private static final String PATTERN_PROVIDER_TARGET_CACHE = "stone.mae2.appeng.helpers.patternprovider.PatternProviderTargetCache";

    // MAE2 1.x (e.g. 1.6.1) exposes pattern P2P tunnels as plain parts implementing this
    // interface instead of an ICraftingMachine, and overwrites pushPattern to route through
    // them. Class/method names differ from the 2.x layout above.
    private static final String LEGACY_PATTERN_P2P = "stone.mae2.parts.p2p.PatternP2PTunnel";
    private static final String LEGACY_TUNNELED_TARGET = "stone.mae2.parts.p2p.PatternP2PTunnel$TunneledPatternProviderTarget";
    private static final String LEGACY_TUNNELED_POS = "stone.mae2.parts.p2p.PatternP2PTunnel$TunneledPos";

    @Nullable
    private static final Class<?> PATTERN_P2P_TUNNEL_LOGIC_CLASS = loadClass(PATTERN_P2P_TUNNEL_LOGIC);
    @Nullable
    private static final Class<?> PATTERN_P2P_TUNNEL_CLASS = loadClass(PATTERN_P2P_TUNNEL);
    @Nullable
    private static final Class<?> PATTERN_P2P_TARGET_CLASS = loadClass(PATTERN_P2P_TARGET);
    @Nullable
    private static final Class<?> PATTERN_PROVIDER_TARGET_CACHE_CLASS = loadClass(PATTERN_PROVIDER_TARGET_CACHE);
    @Nullable
    private static final Class<?> LEGACY_PATTERN_P2P_CLASS = loadClass(LEGACY_PATTERN_P2P);
    @Nullable
    private static final Class<?> LEGACY_TUNNELED_TARGET_CLASS = loadClass(LEGACY_TUNNELED_TARGET);
    @Nullable
    private static final Class<?> LEGACY_TUNNELED_POS_CLASS = loadClass(LEGACY_TUNNELED_POS);

    @Nullable
    private static final Field P2P_TUNNEL_FIELD = getDeclaredField(PATTERN_P2P_TUNNEL_LOGIC_CLASS, "tunnel");

    @Nullable
    private static final Method TUNNEL_GET_OUTPUTS = getMethod(PATTERN_P2P_TUNNEL_CLASS, "getPatternTunnelOutputs");
    @Nullable
    private static final Method TARGET_IS_VALID = getMethod(PATTERN_P2P_TARGET_CLASS, "isValid");
    @Nullable
    private static final Method TARGET_LEVEL = getMethod(PATTERN_P2P_TARGET_CLASS, "level");
    @Nullable
    private static final Method TARGET_POS = getMethod(PATTERN_P2P_TARGET_CLASS, "pos");
    @Nullable
    private static final Method TARGET_SIDE = getMethod(PATTERN_P2P_TARGET_CLASS, "side");
    @Nullable
    private static final Method TARGET_GET_CACHE = getMethod(PATTERN_P2P_TARGET_CLASS, "getCache");
    @Nullable
    private static final Method CACHE_FIND = getMethod(PATTERN_PROVIDER_TARGET_CACHE_CLASS, "find");

    @Nullable
    private static final Method LEGACY_GET_TARGETS = getMethod(LEGACY_PATTERN_P2P_CLASS, "getTargets");
    @Nullable
    private static final Method LEGACY_TARGET_CACHE = getMethod(LEGACY_TUNNELED_TARGET_CLASS, "target");
    @Nullable
    private static final Method LEGACY_TARGET_POS = getMethod(LEGACY_TUNNELED_TARGET_CLASS, "pos");
    @Nullable
    private static final Method LEGACY_POS_POS = getMethod(LEGACY_TUNNELED_POS_CLASS, "pos");
    @Nullable
    private static final Method LEGACY_POS_DIR = getMethod(LEGACY_TUNNELED_POS_CLASS, "dir");

    private MAE2Compat() {}

    public static boolean isPatternP2PTunnelLogic(Object machine) {
        return PATTERN_P2P_TUNNEL_LOGIC_CLASS != null && PATTERN_P2P_TUNNEL_LOGIC_CLASS.isInstance(machine);
    }

    /**
     * MAE2 1.x pattern P2P tunnel parts (both single and multi variants implement the same
     * interface). Unlike 2.x they are not {@link ICraftingMachine}s, so the provider-side
     * scan has to look for the part on the adjacent cable directly.
     */
    public static boolean isLegacyPatternP2PTunnel(Object part) {
        return LEGACY_PATTERN_P2P_CLASS != null && LEGACY_PATTERN_P2P_CLASS.isInstance(part);
    }

    public static long getLegacyPatternP2PMaxOperations(Object tunnelPart, long requestedOperations,
                                                        Level level, KeyCounter baseInputs, boolean blocking,
                                                        Set<AEKey> patternInputs,
                                                        IPatternProviderAutoExpand capacityProvider) {
        if (requestedOperations <= 1) {
            return requestedOperations;
        }
        if (LEGACY_GET_TARGETS == null || LEGACY_TARGET_CACHE == null || LEGACY_TARGET_POS == null ||
                LEGACY_POS_POS == null || LEGACY_POS_DIR == null || CACHE_FIND == null) {
            return requestedOperations;
        }

        try {
            List<?> targets = (List<?>) LEGACY_GET_TARGETS.invoke(tunnelPart);
            if (targets == null || targets.isEmpty()) {
                return 1;
            }

            long minOperations = Long.MAX_VALUE;
            boolean anyValid = false;

            for (Object entry : targets) {
                if (entry == null) {
                    continue;
                }
                Object posRecord = LEGACY_TARGET_POS.invoke(entry);
                Object cache = LEGACY_TARGET_CACHE.invoke(entry);
                if (posRecord == null || cache == null) {
                    continue;
                }
                BlockPos outputPos = (BlockPos) LEGACY_POS_POS.invoke(posRecord);
                Direction partSide = (Direction) LEGACY_POS_DIR.invoke(posRecord);
                if (outputPos == null || partSide == null) {
                    continue;
                }
                anyValid = true;

                // TunneledPos points at the machine block with the part's own side,
                // so the machine's capability side is the opposite (mirrors how the
                // tunnel builds its PatternProviderTargetCache).
                Direction outputSide = partSide.getOpposite();
                BlockEntity outputBE = level.getBlockEntity(outputPos);

                long outputOperations;
                ICraftingMachine craftingMachine = ICraftingMachine.of(level, outputPos, outputSide, outputBE);
                if (craftingMachine != null && craftingMachine.acceptsPlans()) {
                    outputOperations = requestedOperations;
                } else {
                    PatternProviderTarget target = (PatternProviderTarget) CACHE_FIND.invoke(cache);
                    if (target == null) {
                        continue;
                    }
                    if (blocking && target.containsPatternInput(patternInputs)) {
                        // This output is currently blocked; the P2P tunnel will skip it when
                        // routing, so do not count it toward the minimum capacity.
                        continue;
                    }
                    outputOperations = capacityProvider.gtlcore$findMaxOperationsForTarget(
                            target, outputBE, outputSide, baseInputs, requestedOperations);
                }

                if (outputOperations < minOperations) {
                    minOperations = outputOperations;
                }
            }

            if (!anyValid || minOperations == Long.MAX_VALUE) {
                return 1;
            }
            return Math.max(1, minOperations);
        } catch (Throwable t) {
            LOGGER.warn("Failed to compute legacy MAE2 pattern P2P max operations", t);
            return 1;
        }
    }

    public static long getPatternP2PMaxOperations(Object machine, IPatternDetails pattern, long requestedOperations,
                                                  Level level, KeyCounter baseInputs, boolean blocking,
                                                  Set<AEKey> patternInputs,
                                                  IPatternProviderAutoExpand capacityProvider) {
        if (!isPatternP2PTunnelLogic(machine) || requestedOperations <= 1 || !pattern.supportsPushInputsToExternalInventory()) {
            return requestedOperations;
        }

        if (P2P_TUNNEL_FIELD == null || TUNNEL_GET_OUTPUTS == null) {
            return requestedOperations;
        }

        try {
            Object tunnel = P2P_TUNNEL_FIELD.get(machine);
            if (tunnel == null) {
                return requestedOperations;
            }

            List<?> outputs = (List<?>) TUNNEL_GET_OUTPUTS.invoke(tunnel);
            if (outputs == null || outputs.isEmpty()) {
                return 1;
            }

            long minOperations = Long.MAX_VALUE;
            boolean anyValid = false;

            for (Object output : outputs) {
                if (output == null) {
                    continue;
                }
                if (!Boolean.TRUE.equals(TARGET_IS_VALID.invoke(output))) {
                    continue;
                }
                anyValid = true;

                ServerLevel outputLevel = (ServerLevel) TARGET_LEVEL.invoke(output);
                BlockPos outputPos = (BlockPos) TARGET_POS.invoke(output);
                Direction outputSide = (Direction) TARGET_SIDE.invoke(output);
                BlockEntity outputBE = outputLevel.getBlockEntity(outputPos);

                long outputOperations;
                ICraftingMachine craftingMachine = ICraftingMachine.of(outputLevel, outputPos, outputSide, outputBE);
                if (craftingMachine != null && craftingMachine.acceptsPlans()) {
                    outputOperations = requestedOperations;
                } else {
                    Object cache = TARGET_GET_CACHE.invoke(output);
                    if (cache == null) {
                        continue;
                    }
                    PatternProviderTarget target = (PatternProviderTarget) CACHE_FIND.invoke(cache);
                    if (target == null) {
                        continue;
                    }
                    if (blocking && target.containsPatternInput(patternInputs)) {
                        // This output is currently blocked; the P2P tunnel will skip it when
                        // routing, so do not count it toward the minimum capacity.
                        continue;
                    }
                    outputOperations = capacityProvider.gtlcore$findMaxOperationsForTarget(
                            target, outputBE, outputSide, baseInputs, requestedOperations);
                }

                if (outputOperations < minOperations) {
                    minOperations = outputOperations;
                }
            }

            if (!anyValid || minOperations == Long.MAX_VALUE) {
                return 1;
            }
            return Math.max(1, minOperations);
        } catch (Throwable t) {
            LOGGER.warn("Failed to compute MAE2 pattern P2P max operations", t);
            return 1;
        }
    }

    @Nullable
    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className, false, MAE2Compat.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static Field getDeclaredField(@Nullable Class<?> clazz, String name) {
        if (clazz == null) {
            return null;
        }
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static Method getMethod(@Nullable Class<?> clazz, String name, Class<?>... parameterTypes) {
        if (clazz == null) {
            return null;
        }
        try {
            return clazz.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException | LinkageError ignored) {
            return null;
        }
    }
}
