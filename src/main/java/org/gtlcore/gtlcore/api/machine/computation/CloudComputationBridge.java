package org.gtlcore.gtlcore.api.machine.computation;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Optional Additions integration; keeps its existing team ownership and binding rules. */
public final class CloudComputationBridge {

    private static Method teamState;
    private static Method markCacheDirty;
    private static Field providers;

    private CloudComputationBridge() {}

    public static void invalidate() {
        try {
            if (markCacheDirty == null) {
                Class<?> monitor = Class.forName("com.gtladd.gtladditions.common.machine.CloudOpticalComputationMonitorMachine");
                markCacheDirty = monitor.getDeclaredMethod("markCacheDirty");
            }
            // The monitor hook also invalidates our routes and reservations. Its own provider
            // roster must be refreshed when a transmitter changes owner through the setter.
            markCacheDirty.invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported GTL Additions computation interface", failure);
        }
    }

    public static List<IOpticalComputationProvider> providers(UUID owner) {
        if (owner == null) return List.of();
        try {
            if (teamState == null) {
                Class<?> monitor = Class.forName("com.gtladd.gtladditions.common.machine.CloudOpticalComputationMonitorMachine");
                teamState = monitor.getDeclaredMethod("getTeamState", UUID.class);
                teamState.setAccessible(true);
                providers = teamState.getReturnType().getDeclaredField("providers");
                providers.setAccessible(true);
            }
            Object state = teamState.invoke(null, owner);
            List<IOpticalComputationProvider> result = new ArrayList<>();
            if (providers.get(state) instanceof Iterable<?> sources) {
                for (Object source : sources) if (source instanceof IOpticalComputationProvider provider) result.add(provider);
            }
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported GTL Additions computation interface", failure);
        }
    }
}
