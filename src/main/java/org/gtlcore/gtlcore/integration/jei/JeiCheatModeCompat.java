package org.gtlcore.gtlcore.integration.jei;

import org.gtlcore.gtlcore.utils.FluidBucketUtil;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IJeiKeyMapping;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class JeiCheatModeCompat {

    private static final String JEI_INTERNAL_CLASS = "mezz.jei.common.Internal";
    private static final String JEI_COMMAND_UTIL_CLASS = "mezz.jei.gui.util.CommandUtil";
    private static final String JEI_GIVE_AMOUNT_CLASS = "mezz.jei.gui.util.GiveAmount";

    private JeiCheatModeCompat() {}

    public static boolean isCheatStackInputActive(int mouseButton) {
        try {
            Object toggleState = invokeStatic("getClientToggleState");
            if (!(boolean) invoke(toggleState, "isCheatItemsEnabled")) {
                return false;
            }
            Object keyMappings = invokeStatic("getKeyMappings");
            Object mapping = invoke(keyMappings, "getCheatItemStack");
            InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(mouseButton);
            return mapping instanceof IJeiKeyMapping jeiKeyMapping &&
                    jeiKeyMapping.isActiveAndMatches(mouseKey);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    public static boolean reservesInputForEditMode(int mouseButton) {
        try {
            Object toggleState = invokeStatic("getClientToggleState");
            if ((boolean) invoke(toggleState, "isEditModeEnabled")) {
                return true;
            }
            Object keyMappings = invokeStatic("getKeyMappings");
            Object mapping = invoke(keyMappings, "getToggleEditMode");
            InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(mouseButton);
            return mapping instanceof IJeiKeyMapping jeiKeyMapping &&
                    jeiKeyMapping.isActiveAndMatches(mouseKey);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    public static boolean executeCheatStackFallback(@Nullable AEKey key) {
        ItemStack stack = toCheatStack(key);
        if (stack.isEmpty()) {
            return false;
        }
        try {
            Object clientConfigs = invokeStatic("getJeiClientConfigs");
            Object clientConfig = invoke(clientConfigs, "getClientConfig");
            Object serverConnection = invokeStatic("getServerConnection");

            Class<?> commandUtilClass = Class.forName(JEI_COMMAND_UTIL_CLASS);
            Constructor<?> constructor = findTwoArgumentConstructor(commandUtilClass);
            Object commandUtil = constructor.newInstance(clientConfig, serverConnection);

            Class<?> giveAmountClass = Class.forName(JEI_GIVE_AMOUNT_CLASS);
            Object maxAmount = enumConstant(giveAmountClass, "MAX");
            Method giveStack = commandUtilClass.getMethod("giveStack", ItemStack.class, giveAmountClass);
            giveStack.invoke(commandUtil, stack, maxAmount);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static Object invokeStatic(String methodName) throws ReflectiveOperationException {
        Class<?> internal = Class.forName(JEI_INTERNAL_CLASS);
        Method method = internal.getMethod(methodName);
        return method.invoke(null);
    }

    private static Object invoke(Object owner, String methodName) throws ReflectiveOperationException {
        Method method = owner.getClass().getMethod(methodName);
        if (!method.canAccess(owner)) {
            method.setAccessible(true);
        }
        return method.invoke(owner);
    }

    private static Constructor<?> findTwoArgumentConstructor(Class<?> type) throws NoSuchMethodException {
        for (Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == 2) {
                return constructor;
            }
        }
        throw new NoSuchMethodException(type.getName() + " constructor with two arguments");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object enumConstant(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }

    private static ItemStack toCheatStack(@Nullable AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            return itemKey.toStack(itemKey.getMaxStackSize());
        }
        if (key instanceof AEFluidKey fluidKey) {
            return FluidBucketUtil.getFilledBucket(new FluidStack(
                    fluidKey.getFluid(), AEFluidKey.AMOUNT_BUCKET, fluidKey.copyTag()));
        }
        return ItemStack.EMPTY;
    }
}
