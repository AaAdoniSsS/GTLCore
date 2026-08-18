package org.gtlcore.gtlcore.mixin.forge;

import net.minecraftforge.common.world.ForgeChunkManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ForgeChunkManager.TicketOwner.class)
public interface ForgeChunkTicketOwnerAccessor {

    @Accessor(value = "modId", remap = false)
    String gtlcore$getModId();
}
