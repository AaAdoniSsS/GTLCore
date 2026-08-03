package org.gtlcore.gtlcore.integration.ae2.emitter;

import org.gtlcore.gtlcore.mixin.ae2.AbstractLevelEmitterPartAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Setting;
import appeng.api.stacks.AEKey;
import appeng.api.util.IConfigManager;
import appeng.core.definitions.AEItems;
import appeng.helpers.IConfigInvHost;
import appeng.parts.automation.AbstractLevelEmitterPart;
import appeng.parts.automation.EnergyLevelEmitterPart;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class EmitterManagerSupport {

    private static final List<ThresholdMethodNames> THRESHOLD_METHOD_NAMES = List.of(
            new ThresholdMethodNames("getLowerValue", "setLowerValue", "getUpperValue", "setUpperValue"),
            new ThresholdMethodNames("getMinValue", "setMinValue", "getMaxValue", "setMaxValue"),
            new ThresholdMethodNames(
                    "getLowerThreshold",
                    "setLowerThreshold",
                    "getUpperThreshold",
                    "setUpperThreshold"));
    private static final ClassValue<Optional<ThresholdAccess>> THRESHOLD_ACCESS = new ClassValue<>() {

        @Override
        protected Optional<ThresholdAccess> computeValue(Class<?> type) {
            for (ThresholdMethodNames names : THRESHOLD_METHOD_NAMES) {
                try {
                    return Optional.of(new ThresholdAccess(
                            type.getMethod(names.lowerGetter()),
                            type.getMethod(names.lowerSetter(), long.class),
                            type.getMethod(names.upperGetter()),
                            type.getMethod(names.upperSetter(), long.class)));
                } catch (NoSuchMethodException ignored) {
                    // Try the next common public threshold convention.
                }
            }
            return Optional.empty();
        }
    };

    private EmitterManagerSupport() {}

    static EmitterManagerTerminalMenu.Entry snapshot(AbstractLevelEmitterPart part) {
        BlockPos pos = part.getHost().getLocation().getPos();
        ResourceLocation dimension = part.getLevel().dimension().location();
        EmitterManagerTerminalMenu.Address address = new EmitterManagerTerminalMenu.Address(
                dimension,
                pos,
                part.getSide());
        ItemStack icon = new ItemStack(part.getPartItem().asItem());
        boolean hasConfig = part instanceof IConfigInvHost configHost && configHost.getConfig().size() > 0;
        AEKey configuredKey = hasConfig ?
                ((IConfigInvHost) part).getConfig().getKey(0) :
                null;
        Optional<ThresholdAccess> thresholdAccess = THRESHOLD_ACCESS.get(part.getClass());
        EmitterManagerTerminalMenu.Function function = thresholdAccess.isPresent() ?
                EmitterManagerTerminalMenu.Function.THRESHOLD :
                (part instanceof EnergyLevelEmitterPart ?
                        EmitterManagerTerminalMenu.Function.ENERGY :
                        (part instanceof IConfigInvHost ?
                                EmitterManagerTerminalMenu.Function.STORAGE :
                                EmitterManagerTerminalMenu.Function.GENERIC));
        long lowerValue = thresholdAccess.map(access -> access.getLower(part)).orElse(0L);
        long upperValue = thresholdAccess.map(access -> access.getUpper(part)).orElse(0L);
        List<EmitterManagerTerminalMenu.SettingValue> settings = readSettings(part.getConfigManager());
        boolean craftingCard = part.getUpgrades().isInstalled(AEItems.CRAFTING_CARD);
        boolean fuzzyCard = part.getUpgrades().isInstalled(AEItems.FUZZY_CARD);
        int upgradeSlots = part.getUpgrades().size();
        long monitoredValue = ((AbstractLevelEmitterPartAccessor) part).gtlcore$getLastReportedValue();
        return new EmitterManagerTerminalMenu.Entry(
                address,
                icon,
                part.getName(),
                function,
                configuredKey,
                monitoredValue,
                part.getReportingValue(),
                lowerValue,
                upperValue,
                part.isProvidingWeakPower() > 0,
                part.isActive(),
                craftingCard,
                fuzzyCard,
                upgradeSlots,
                hasConfig,
                settings);
    }

    static boolean setSetting(AbstractLevelEmitterPart part, String settingName, String valueName) {
        IConfigManager manager = part.getConfigManager();
        for (Setting<?> setting : manager.getSettings()) {
            if (setting.getName().equals(settingName)) {
                return putSetting(manager, setting, valueName);
            }
        }
        return false;
    }

    static boolean setValue(AbstractLevelEmitterPart part, EmitterManagerTerminalMenu.ValueKind kind, long value) {
        long sanitizedValue = Math.max(0L, value);
        if (kind == EmitterManagerTerminalMenu.ValueKind.REPORTING) {
            part.setReportingValue(sanitizedValue);
            return true;
        }
        Optional<ThresholdAccess> access = THRESHOLD_ACCESS.get(part.getClass());
        if (access.isEmpty()) {
            return false;
        }
        if (kind == EmitterManagerTerminalMenu.ValueKind.LOWER_THRESHOLD) {
            return access.get().setLower(part, sanitizedValue);
        }
        return access.get().setUpper(part, sanitizedValue);
    }

    private static List<EmitterManagerTerminalMenu.SettingValue> readSettings(IConfigManager manager) {
        List<EmitterManagerTerminalMenu.SettingValue> values = new ArrayList<>();
        for (Setting<?> setting : manager.getSettings()) {
            values.add(new EmitterManagerTerminalMenu.SettingValue(
                    setting.getName(),
                    getSettingName(manager, setting)));
        }
        values.sort(Comparator.comparing(EmitterManagerTerminalMenu.SettingValue::name));
        return List.copyOf(values.subList(0, Math.min(values.size(), EmitterManagerTerminalMenu.MAX_SYNC_SETTINGS)));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static String getSettingName(IConfigManager manager, Setting<?> setting) {
        return manager.getSetting((Setting) setting).name();
    }

    private static boolean putSetting(IConfigManager manager, Setting<?> setting, String valueName) {
        return putCapturedSetting(manager, setting, valueName);
    }

    private static <T extends Enum<T>> boolean putCapturedSetting(IConfigManager manager, Setting<T> setting,
                                                                  String valueName) {
        for (T candidate : setting.getValues()) {
            if (candidate.name().equals(valueName)) {
                manager.putSetting(setting, candidate);
                return true;
            }
        }
        return false;
    }

    private record ThresholdMethodNames(String lowerGetter, String lowerSetter, String upperGetter,
                                        String upperSetter) {}

    private record ThresholdAccess(Method lowerGetter, Method lowerSetter, Method upperGetter, Method upperSetter) {

        private long getLower(Object target) {
            return invokeGetter(lowerGetter, target);
        }

        private long getUpper(Object target) {
            return invokeGetter(upperGetter, target);
        }

        private boolean setLower(Object target, long value) {
            return invokeSetter(lowerSetter, target, value);
        }

        private boolean setUpper(Object target, long value) {
            return invokeSetter(upperSetter, target, value);
        }

        private static long invokeGetter(Method method, Object target) {
            try {
                return ((Number) method.invoke(target)).longValue();
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException ignored) {
                return 0L;
            }
        }

        private static boolean invokeSetter(Method method, Object target, long value) {
            try {
                method.invoke(target, value);
                return true;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // A failed optional add-on adapter leaves the emitter unchanged.
                return false;
            }
        }
    }
}
