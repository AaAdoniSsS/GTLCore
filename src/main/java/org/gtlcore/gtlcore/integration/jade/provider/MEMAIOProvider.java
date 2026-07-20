package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine;
import org.gtlcore.gtlcore.integration.ae2.MEFluidUnits;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.integration.jade.GTElementHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.google.common.primitives.Ints;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;

import static net.minecraft.network.chat.ComponentUtils.wrapInSquareBrackets;

public class MEMAIOProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        if (blockAccessor.getBlockEntity() instanceof IMachineBlockEntity blockEntity) {
            if (blockEntity.getMetaMachine() instanceof MEMolecularAssemblerIOPartMachine) {
                CompoundTag serverData = blockAccessor.getServerData();
                readBufferContents(iTooltip, serverData);
            }
        }
    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof IMachineBlockEntity blockEntity) {
            if (blockEntity.getMetaMachine() instanceof MEMolecularAssemblerIOPartMachine ioPartMachine) {
                putTag(compoundTag, ioPartMachine);
            }
        }
    }

    public static void readBufferContents(ITooltip iTooltip, CompoundTag serverData) {
        // Display pending refunds
        ListTag pending = serverData.getList("pending", Tag.TAG_COMPOUND);
        if (!pending.isEmpty()) {
            iTooltip.add(Component.translatable("gtceu.top.pending_refunds")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            for (var tag : pending) {
                if (!(tag instanceof CompoundTag keyTag) || keyTag.isEmpty()) continue;
                var key = AEKey.fromTagGeneric((CompoundTag) keyTag.get("key"));
                if (key instanceof AEItemKey itemKey) {
                    var stack = itemKey.toStack();
                    long count = keyTag.getLong("count");
                    iTooltip.add(iTooltip.getElementHelper().smallItem(stack));
                    Component text = Component.literal(" ")
                            .append(String.valueOf(count))
                            .append(" × ")
                            .append(wrapInSquareBrackets(stack.getItem().getDescription()).withStyle(ChatFormatting.WHITE))
                            .withStyle(ChatFormatting.WHITE);
                    iTooltip.append(text);
                } else if (key instanceof AEFluidKey fluidKey) {
                    long count = keyTag.getLong("count");
                    var fluid = fluidKey.toStack(Ints.saturatedCast(count));
                    iTooltip.add(GTElementHelper.smallFluid(JadeFluidObject.of(fluid.getFluid(), count)));
                    Component text = Component.literal(" ")
                            .append(MEFluidUnits.formatDisplayAmount(count))
                            .append(" ")
                            .append(wrapInSquareBrackets(fluid.getDisplayName()).withStyle(ChatFormatting.WHITE))
                            .withStyle(ChatFormatting.WHITE);
                    iTooltip.append(text);
                }
            }
        }
    }

    public static void putTag(CompoundTag compoundTag, MEMolecularAssemblerIOPartMachine maMachine) {
        // Add pending refund data
        ListTag listTag = new ListTag();
        for (var it = maMachine.getBuffer().object2LongEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            var count = entry.getLongValue();
            if (count > 0) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.put("key", entry.getKey().toTagGeneric());
                itemTag.putLong("count", count);
                listTag.add(itemTag);
            }
        }
        compoundTag.put("pending", listTag);
    }

    @Override
    public ResourceLocation getUid() {
        return GTCEu.id("me_molecular_assembler_io");
    }
}
