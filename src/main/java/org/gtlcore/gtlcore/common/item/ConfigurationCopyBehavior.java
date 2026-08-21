package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.IAutoConfiguratioGravityPart;
import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.IAutoConfigurationMaintenanceHatch;
import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.IGravityPartMachine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.item.component.ICustomDescriptionId;
import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.item.tool.behavior.IToolBehavior;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine;
import com.gregtechceu.gtceu.common.machine.storage.QuantumTankMachine;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import java.util.Objects;

import static com.gregtechceu.gtceu.api.item.tool.ToolHelper.getBehaviorsTag;

public class ConfigurationCopyBehavior implements IToolBehavior, IInteractionItem, ICustomDescriptionId {

    public static final ConfigurationCopyBehavior INSTANCE = new ConfigurationCopyBehavior();

    private static final String QUANTUM_CHEST_CONFIGURATION = "QuantumChestConfiguration";
    private static final String QUANTUM_TANK_CONFIGURATION = "QuantumTankConfiguration";
    private static final String FLUID_OUTPUT_HATCH_CONFIGURATION = "FluidOutputHatchConfiguration";
    private static final String COPIED_CONFIGURATION_TYPE = "CopiedConfigurationType";
    private static final String COPIED_NAME_SUFFIX = "item.gtlcore.cfg_copy.copied_suffix";
    private static final int CONFIGURATION_SLOT = 0;

    private static final String[] LEGACY_CONFIGURATION_KEYS = {
            "Configuration",
            "hasAutoOutputItem",
            "hasAutoOutputFluid",
            "outputFacingItems",
            "outputFacingFluids",
            "allowInputFromOutputSideItems",
            "allowInputFromOutputSideFluids",
            "circuit",
            "isDistinct",
            "isWorkingEnabled",
            "durationMultiplier",
            "currentGravity"
    };

    @Override
    public Component getItemName(ItemStack stack) {
        CopiedConfiguration copiedConfiguration = getCopiedConfiguration(getBehaviorsTag(stack));
        if (copiedConfiguration == null) {
            return null;
        }
        return Component.translatable(stack.getItem().getDescriptionId())
                .append(Component.translatable(COPIED_NAME_SUFFIX,
                        Component.translatable(copiedConfiguration.translationKey)));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        CompoundTag tags = getBehaviorsTag(stack);
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof MetaMachineBlockEntity machineBlock) {
            MetaMachine metaMachine = machineBlock.getMetaMachine();
            if (metaMachine instanceof SimpleTieredMachine simpleTieredMachine) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.SIMPLE_MACHINE);
                    getSTMCfg(tags, simpleTieredMachine);
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else {
                    if (tags.getBoolean("Configuration")) {
                        setSTMCfg(tags, simpleTieredMachine);
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                    } else {
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                    }
                }
            } else if (metaMachine instanceof QuantumChestMachine quantumChestMachine) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.QUANTUM_CHEST);
                    tags.put(QUANTUM_CHEST_CONFIGURATION, getQuantumChestCfg(quantumChestMachine));
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else if (tags.get(QUANTUM_CHEST_CONFIGURATION) instanceof CompoundTag configuration) {
                    setQuantumChestCfg(configuration, quantumChestMachine);
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                } else {
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                }
            } else if (metaMachine instanceof QuantumTankMachine quantumTankMachine) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.QUANTUM_TANK);
                    tags.put(QUANTUM_TANK_CONFIGURATION, getQuantumTankCfg(quantumTankMachine));
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else if (tags.get(QUANTUM_TANK_CONFIGURATION) instanceof CompoundTag configuration) {
                    setQuantumTankCfg(configuration, quantumTankMachine);
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                } else {
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                }
            } else if (metaMachine instanceof FluidHatchPartMachine fluidHatchPartMachine && fluidHatchPartMachine.tank.handlerIO == IO.OUT) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.FLUID_OUTPUT_HATCH);
                    tags.put(FLUID_OUTPUT_HATCH_CONFIGURATION, getFluidOutputHatchCfg(fluidHatchPartMachine));
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else if (tags.get(FLUID_OUTPUT_HATCH_CONFIGURATION) instanceof CompoundTag configuration) {
                    setFluidOutputHatchCfg(configuration, fluidHatchPartMachine);
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                } else {
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                }
            } else if (metaMachine instanceof ItemBusPartMachine itemBusPartMachine) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.ITEM_BUS);
                    getBusCfg(tags, itemBusPartMachine);
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else {
                    if (tags.getBoolean("Configuration")) {
                        setBusCfg(tags, itemBusPartMachine);
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                    } else {
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                    }
                }
            } else if (metaMachine instanceof IAutoConfiguratioGravityPart acgp) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.GRAVITY_CONFIGURATION_HATCH);
                    tags.putBoolean("Configuration", true);
                    tags.putFloat("durationMultiplier", acgp.getDurationMultiplier());
                    tags.putInt("currentGravity", acgp.getCurrentGravity());
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else {
                    if (tags.getBoolean("Configuration")) {
                        acgp.setDurationMultiplier(tags.getFloat("durationMultiplier"));
                        acgp.setCurrentGravity(tags.getInt("currentGravity"));
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                    } else {
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                    }
                }
            } else if (metaMachine instanceof IAutoConfigurationMaintenanceHatch acmh) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.CONFIGURATION_MAINTENANCE_HATCH);
                    tags.putBoolean("Configuration", true);
                    tags.putFloat("durationMultiplier", acmh.getDurationMultiplier());
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else {
                    if (tags.getBoolean("Configuration")) {
                        acmh.setDurationMultiplier(tags.getFloat("durationMultiplier"));
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                    } else {
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                    }
                }
            } else if (metaMachine instanceof IGravityPartMachine gpm) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.GRAVITY_HATCH);
                    tags.putBoolean("Configuration", true);
                    tags.putInt("currentGravity", gpm.getCurrentGravity());
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else {
                    if (tags.getBoolean("Configuration")) {
                        gpm.setCurrentGravity(tags.getInt("currentGravity"));
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                    } else {
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                    }
                }
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private void prepareCopy(CompoundTag tags, CopiedConfiguration copiedConfiguration) {
        tags.remove(QUANTUM_CHEST_CONFIGURATION);
        tags.remove(QUANTUM_TANK_CONFIGURATION);
        tags.remove(FLUID_OUTPUT_HATCH_CONFIGURATION);
        for (String key : LEGACY_CONFIGURATION_KEYS) {
            tags.remove(key);
        }
        tags.putString(COPIED_CONFIGURATION_TYPE, copiedConfiguration.id);
    }

    private CopiedConfiguration getCopiedConfiguration(CompoundTag tags) {
        CopiedConfiguration copiedConfiguration = CopiedConfiguration.fromId(tags.getString(COPIED_CONFIGURATION_TYPE));
        if (copiedConfiguration != null) {
            return copiedConfiguration;
        }
        if (tags.contains(QUANTUM_CHEST_CONFIGURATION)) {
            return CopiedConfiguration.QUANTUM_CHEST;
        }
        if (tags.contains(QUANTUM_TANK_CONFIGURATION)) {
            return CopiedConfiguration.QUANTUM_TANK;
        }
        if (tags.contains(FLUID_OUTPUT_HATCH_CONFIGURATION)) {
            return CopiedConfiguration.FLUID_OUTPUT_HATCH;
        }
        if (!tags.getBoolean("Configuration")) {
            return null;
        }
        if (tags.contains("durationMultiplier") && tags.contains("currentGravity")) {
            return CopiedConfiguration.GRAVITY_CONFIGURATION_HATCH;
        }
        if (tags.contains("durationMultiplier")) {
            return CopiedConfiguration.CONFIGURATION_MAINTENANCE_HATCH;
        }
        if (tags.contains("currentGravity")) {
            return CopiedConfiguration.GRAVITY_HATCH;
        }
        if (tags.contains("isDistinct")) {
            return CopiedConfiguration.ITEM_BUS;
        }
        return tags.contains("hasAutoOutputItem") ? CopiedConfiguration.SIMPLE_MACHINE : null;
    }

    private CompoundTag getQuantumChestCfg(QuantumChestMachine metaMachine) {
        CompoundTag configuration = new CompoundTag();
        configuration.putBoolean("autoOutputItems", metaMachine.isAutoOutputItems());
        configuration.putString("outputFacingItems", metaMachine.getOutputFacingItems().getName());
        configuration.putBoolean("allowInputFromOutputSideItems", metaMachine.isAllowInputFromOutputSideItems());
        configuration.putBoolean("isVoiding", metaMachine.isVoiding());
        configuration.putBoolean("isLocked", metaMachine.isLocked());
        if (metaMachine.isLocked()) {
            ItemStack lockedItem = metaMachine.getLockedItem().getStackInSlot(CONFIGURATION_SLOT).copy();
            lockedItem.setCount(1);
            configuration.put("lockedItem", lockedItem.save(new CompoundTag()));
        }
        return configuration;
    }

    private void setQuantumChestCfg(CompoundTag configuration, QuantumChestMachine metaMachine) {
        metaMachine.setAutoOutputItems(configuration.getBoolean("autoOutputItems"));
        metaMachine.setOutputFacingItems(Direction.byName(configuration.getString("outputFacingItems")));
        metaMachine.setAllowInputFromOutputSideItems(configuration.getBoolean("allowInputFromOutputSideItems"));
        metaMachine.setVoiding(configuration.getBoolean("isVoiding"));
        ItemStack lockedItem = configuration.getBoolean("isLocked") ?
                ItemStack.of(configuration.getCompound("lockedItem")) : ItemStack.EMPTY;
        metaMachine.getLockedItem().setStackInSlot(CONFIGURATION_SLOT, lockedItem);
    }

    private CompoundTag getQuantumTankCfg(QuantumTankMachine metaMachine) {
        CompoundTag configuration = new CompoundTag();
        configuration.putBoolean("autoOutputFluids", metaMachine.isAutoOutputFluids());
        configuration.putString("outputFacingFluids", metaMachine.getOutputFacingFluids().getName());
        configuration.putBoolean("allowInputFromOutputSideFluids", metaMachine.isAllowInputFromOutputSideFluids());
        configuration.putBoolean("isVoiding", metaMachine.isVoiding());
        configuration.putBoolean("isLocked", metaMachine.isLocked());
        if (metaMachine.isLocked()) {
            configuration.put("lockedFluid", saveFluid(metaMachine.getCache().getLockedFluid().getFluid()));
        }
        return configuration;
    }

    private void setQuantumTankCfg(CompoundTag configuration, QuantumTankMachine metaMachine) {
        metaMachine.setAutoOutputFluids(configuration.getBoolean("autoOutputFluids"));
        metaMachine.setOutputFacingFluids(Direction.byName(configuration.getString("outputFacingFluids")));
        metaMachine.setAllowInputFromOutputSideFluids(configuration.getBoolean("allowInputFromOutputSideFluids"));
        metaMachine.setVoiding(configuration.getBoolean("isVoiding"));
        metaMachine.getCache().setLocked(false);
        if (configuration.getBoolean("isLocked")) {
            metaMachine.getCache().setLocked(true, FluidStack.loadFromTag(configuration.getCompound("lockedFluid")));
        }
    }

    private CompoundTag getFluidOutputHatchCfg(FluidHatchPartMachine metaMachine) {
        CompoundTag configuration = new CompoundTag();
        configuration.putBoolean("isWorkingEnabled", metaMachine.isWorkingEnabled());
        configuration.putInt("circuit", getCircuitConfiguration(metaMachine.getCircuitInventory()));
        configuration.putBoolean("isLocked", metaMachine.tank.isLocked());
        if (metaMachine.tank.isLocked()) {
            configuration.put("lockedFluid", saveFluid(metaMachine.tank.getLockedFluid().getFluid()));
        }
        return configuration;
    }

    private void setFluidOutputHatchCfg(CompoundTag configuration, FluidHatchPartMachine metaMachine) {
        metaMachine.setWorkingEnabled(configuration.getBoolean("isWorkingEnabled"));
        setCircuitConfiguration(metaMachine.getCircuitInventory(), configuration.getInt("circuit"));
        metaMachine.tank.setLocked(false);
        if (configuration.getBoolean("isLocked")) {
            metaMachine.tank.setLocked(true, FluidStack.loadFromTag(configuration.getCompound("lockedFluid")));
        }
    }

    private CompoundTag saveFluid(FluidStack fluid) {
        FluidStack copy = fluid.copy();
        copy.setAmount(1);
        return copy.saveToTag(new CompoundTag());
    }

    private int getCircuitConfiguration(NotifiableItemStackHandler circuitInventory) {
        return circuitInventory.handlerIO.support(IO.IN) ?
                IntCircuitBehaviour.getCircuitConfiguration(circuitInventory.getStackInSlot(CONFIGURATION_SLOT)) : 0;
    }

    private void setCircuitConfiguration(NotifiableItemStackHandler circuitInventory, int configuration) {
        if (circuitInventory.handlerIO.support(IO.IN)) {
            circuitInventory.setStackInSlot(CONFIGURATION_SLOT,
                    configuration > 0 ? IntCircuitBehaviour.stack(configuration) : ItemStack.EMPTY);
        }
    }

    private void getBusCfg(CompoundTag tags, ItemBusPartMachine metaMachine) {
        tags.putBoolean("Configuration", true);
        tags.putBoolean("isDistinct", metaMachine.isDistinct());
        tags.putBoolean("isWorkingEnabled", metaMachine.isWorkingEnabled());
        NotifiableItemStackHandler circuitInventory = metaMachine.getCircuitInventory();
        if (circuitInventory.handlerIO.support(IO.IN)) {
            int c = IntCircuitBehaviour.getCircuitConfiguration(circuitInventory.getStackInSlot(0));
            if (c > 0) {
                tags.putInt("circuit", c);
            }
        }
    }

    private void setBusCfg(CompoundTag tags, ItemBusPartMachine metaMachine) {
        metaMachine.setDistinct(tags.getBoolean("isDistinct"));
        metaMachine.setWorkingEnabled(tags.getBoolean("isWorkingEnabled"));
        int c = tags.getInt("circuit");
        NotifiableItemStackHandler circuitInventory = metaMachine.getCircuitInventory();
        if (c > 0 && circuitInventory.handlerIO.support(IO.IN)) {
            circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(c));
        }
    }

    private void getSTMCfg(CompoundTag tags, SimpleTieredMachine metaMachine) {
        tags.putBoolean("Configuration", true);
        boolean hasAutoOutputItem = metaMachine.hasAutoOutputItem();
        boolean hasAutoOutputFluid = metaMachine.hasAutoOutputFluid();
        tags.putBoolean("hasAutoOutputItem", hasAutoOutputItem);
        tags.putBoolean("hasAutoOutputFluid", hasAutoOutputFluid);
        if (hasAutoOutputItem) {
            tags.putString("outputFacingItems", metaMachine.getOutputFacingItems().toString());
        }
        if (hasAutoOutputFluid) {
            tags.putString("outputFacingFluids", metaMachine.getOutputFacingFluids().toString());
        }
        tags.putBoolean("allowInputFromOutputSideItems", metaMachine.isAllowInputFromOutputSideItems());
        tags.putBoolean("allowInputFromOutputSideFluids", metaMachine.isAllowInputFromOutputSideFluids());
        NotifiableItemStackHandler circuitInventory = metaMachine.getCircuitInventory();
        if (circuitInventory.handlerIO.support(IO.IN)) {
            int c = IntCircuitBehaviour.getCircuitConfiguration(circuitInventory.getStackInSlot(0));
            if (c > 0) {
                tags.putInt("circuit", c);
            }
        }
    }

    private void setSTMCfg(CompoundTag tags, SimpleTieredMachine metaMachine) {
        boolean hasAutoOutputItem = tags.getBoolean("hasAutoOutputItem");
        boolean hasAutoOutputFluid = tags.getBoolean("hasAutoOutputFluid");
        metaMachine.setAutoOutputItems(hasAutoOutputItem);
        metaMachine.setAutoOutputFluids(hasAutoOutputFluid);
        if (hasAutoOutputItem) {
            metaMachine.setOutputFacingItems(Direction.byName(tags.getString("outputFacingItems")));
        }
        if (hasAutoOutputFluid) {
            metaMachine.setOutputFacingFluids(Direction.byName(tags.getString("outputFacingFluids")));
        }
        metaMachine.setAllowInputFromOutputSideItems(tags.getBoolean("allowInputFromOutputSideItems"));
        metaMachine.setAllowInputFromOutputSideFluids(tags.getBoolean("allowInputFromOutputSideFluids"));
        int c = tags.getInt("circuit");
        NotifiableItemStackHandler circuitInventory = metaMachine.getCircuitInventory();
        if (c > 0 && circuitInventory.handlerIO.support(IO.IN)) {
            circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(c));
        }
    }

    private enum CopiedConfiguration {

        SIMPLE_MACHINE("simple_machine", "item.gtlcore.cfg_copy.type.simple_machine"),
        ITEM_BUS("item_bus", "item.gtlcore.cfg_copy.type.item_bus"),
        QUANTUM_CHEST("quantum_chest", "item.gtlcore.cfg_copy.type.quantum_chest"),
        QUANTUM_TANK("quantum_tank", "item.gtlcore.cfg_copy.type.quantum_tank"),
        FLUID_OUTPUT_HATCH("fluid_output_hatch", "item.gtlcore.cfg_copy.type.fluid_output_hatch"),
        GRAVITY_CONFIGURATION_HATCH("gravity_configuration_hatch", "item.gtlcore.cfg_copy.type.gravity_configuration_hatch"),
        CONFIGURATION_MAINTENANCE_HATCH("configuration_maintenance_hatch", "item.gtlcore.cfg_copy.type.configuration_maintenance_hatch"),
        GRAVITY_HATCH("gravity_hatch", "item.gtlcore.cfg_copy.type.gravity_hatch");

        private final String id;
        private final String translationKey;

        CopiedConfiguration(String id, String translationKey) {
            this.id = id;
            this.translationKey = translationKey;
        }

        private static CopiedConfiguration fromId(String id) {
            for (CopiedConfiguration copiedConfiguration : values()) {
                if (copiedConfiguration.id.equals(id)) {
                    return copiedConfiguration;
                }
            }
            return null;
        }
    }
}
