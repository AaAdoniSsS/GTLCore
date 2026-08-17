package org.gtlcore.gtlcore.api.machine.multiblock;

import org.gtlcore.gtlcore.api.machine.trait.AECraft.IMECraftSpeedCore;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class MolecularAssemblerMultiblockMachine extends MolecularAssemblerMultiblockMachineBase implements IFancyUIMachine, IDisplayUIMachine, IInteractedMachine {

    public MolecularAssemblerMultiblockMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    // ========================================
    // GUI
    // ========================================

    @Override
    public void addDisplayText(List<Component> textList) {
        IDisplayUIMachine.super.addDisplayText(textList);
        if (isFormed()) {
            int coreCount = 0;
            for (IMultiPart part : this.getParts()) {
                if (part instanceof IMECraftSpeedCore) {
                    coreCount++;
                }
            }
            textList.add(Component.translatable(getRecipeType().registryName.toLanguageKey())
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("gtceu.gui.machinemode.title")))));
            if (!isWorkingEnabled()) {
                textList.add(Component.translatable("gtceu.multiblock.work_paused"));
            } else if (isActive()) {
                textList.add(Component.translatable("gtceu.multiblock.running"));
                int currentProgress = (int) (recipeLogic.getProgressPercent() * 100);
                textList.add(Component.translatable("gtceu.multiblock.progress", currentProgress));
            } else {
                textList.add(Component.translatable("gtceu.multiblock.idling"));
            }
            if (recipeLogic.isWaiting()) {
                textList.add(Component.translatable("gtceu.multiblock.waiting")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
            }
            if (maxParallel > 1) {
                textList.add(Component.translatable("gtceu.multiblock.parallel", Component.literal(FormattingUtil.formatNumbers(maxParallel)).withStyle(ChatFormatting.DARK_PURPLE))
                        .withStyle(ChatFormatting.GRAY));
            }
            textList.add(Component.translatable("gtlcore.multiblock.tick_Duration", Component.literal(FormattingUtil.formatNumbers(tickDuration)).withStyle(ChatFormatting.BLUE))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gtlcore.multiblock.contains_Patttern", Component.literal(FormattingUtil.formatNumbers(patternSize)).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gui.gtlcore.molecular_assembler_0", Component.literal(FormattingUtil.formatNumbers(maxParallel / 4194304)).withStyle(ChatFormatting.DARK_GREEN))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gui.gtlcore.molecular_assembler_2", Component.literal(FormattingUtil.formatNumbers(coreCount)).withStyle(ChatFormatting.DARK_GREEN))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gui.gtlcore.molecular_assembler_1", Component.literal(FormattingUtil.formatNumbers(patternSize / 108)).withStyle(ChatFormatting.DARK_GREEN))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            Component tooltip = Component.translatable("gtceu.multiblock.invalid_structure.tooltip")
                    .withStyle(ChatFormatting.GRAY);
            textList.add(Component.translatable("gtceu.multiblock.invalid_structure")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip))));
        }
        getDefinition().getAdditionalDisplay().accept(this, textList);
    }

    @Override
    public Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 182 + 8, 117 + 8);
        group.addWidget(new DraggableScrollableWidgetGroup(4, 4, 182, 117)
                .setBackground(getScreenTexture())
                .addWidget(new LabelWidget(4, 5, self().getBlockState().getBlock().getDescriptionId()))
                .addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText)
                        .setMaxWidthLimit(150)
                        .clickHandler(this::handleDisplayClick)));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(198, 208, this, entityPlayer)
                .widget(new FancyMachineUIWidget(this, 198, 208));
    }

    @Override
    public List<IFancyUIProvider> getSubTabs() {
        return getParts().stream()
                .filter(Objects::nonNull)
                .map(IFancyUIProvider.class::cast)
                .toList();
    }

    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        for (IMultiPart part : getParts()) {
            part.attachFancyTooltipsToController(this, tooltipsPanel);
        }
    }
}
