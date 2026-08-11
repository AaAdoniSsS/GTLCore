package org.gtlcore.gtlcore.utils;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.regex.Pattern;

import static net.minecraft.ChatFormatting.*;

public class TextUtil {

    public static final ChatFormatting[] GTL_CORE$VC = {
            DARK_GRAY,
            GRAY,
            AQUA,
            GOLD,
            DARK_PURPLE,
            BLUE,
            LIGHT_PURPLE,
            RED,
            DARK_AQUA,
            DARK_RED,
            GREEN,
            DARK_GREEN,
            YELLOW,
            BLUE,
            RED
    };

    public static String formatting(String input, ChatFormatting[] colours, double delay) {
        StringBuilder sb = new StringBuilder(input.length() * 3);
        if (delay <= 0.0D)
            delay = 0.001D;
        int offset = (int) Math.floor((System.currentTimeMillis() & 0x3FFFL) / delay) % colours.length;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            sb.append(colours[(colours.length + i - offset) % colours.length].toString());
            sb.append(c);
        }
        return sb.toString();
    }

    public static String full_color(String input) {
        return formatting(input, new ChatFormatting[] { RED, GOLD, YELLOW, GREEN, AQUA, BLUE, LIGHT_PURPLE }, 80.0D);
    }

    public static String dark_purplish_red(String input) {
        return formatting(input, new ChatFormatting[] { DARK_PURPLE, DARK_RED }, 160.0D);
    }

    public static String white_blue(String input) {
        return formatting(input, new ChatFormatting[] { BLUE, BLUE, BLUE, BLUE, WHITE, BLUE, WHITE, WHITE, BLUE,
                WHITE, WHITE, BLUE, RED, WHITE }, 80.0D);
    }

    public static String purplish_red(String input) {
        return formatting(input, new ChatFormatting[] { LIGHT_PURPLE, DARK_PURPLE }, 160.0D);
    }

    public static String golden(String input) {
        return formatting(input, new ChatFormatting[] { YELLOW, GOLD }, 160.0D);
    }

    public static String dark_green(String input) {
        return formatting(input, new ChatFormatting[] { GREEN, DARK_GREEN }, 160.0D);
    }

    /**
     * 查询某个内容是否出现在数组中，支持通配符
     * 
     * @param array  要查询的数组
     * @param target 要查询的内容，支持通配符
     * @return 如果内容存在于数组中，返回true；否则返回false
     */
    public static boolean containsWithWildcard(String[] array, String target) {
        for (String element : array) {
            String regex = element.replace("*", ".*");
            if (Pattern.matches(regex, target)) {
                return true;
            }
        }
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    public static void appendChemicalFormulaTooltip(Fluid fluid, List<Component> tooltips) {
        var material = ChemicalHelper.getMaterial(fluid);
        if (material == null) return;

        String formula = material.getChemicalFormula();
        if (formula == null || formula.isEmpty() || tooltips.stream().anyMatch(line -> line.getString().equals(formula))) {
            return;
        }
        tooltips.add(Math.min(1, tooltips.size()), Component.literal(formula).withStyle(ChatFormatting.YELLOW));
    }

    @OnlyIn(Dist.CLIENT)
    public static void appendIngotConversionTooltip(
                                                    FluidStack fluidStack,
                                                    List<Component> tooltips,
                                                    long amount) {
        var material = ChemicalHelper.getMaterial(fluidStack.getFluid());
        if (material == null) return;

        if (!material.hasProperty(PropertyKey.INGOT)) return;
        if (!GTUtil.isShiftDown()) return;
        if (amount < 144L) return;

        long ingots = amount / 144L;
        long remainder = amount % 144L;
        String fluidAmount = String.format(" %,d mB = %,d * %d mB", amount, ingots, 144);

        if (remainder != 0L) {
            fluidAmount = fluidAmount + String.format(" + %d mB", remainder);
        }

        tooltips.add(
                Component.translatable("gtceu.gui.fluid_amount")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(fluidAmount)));
    }
}
