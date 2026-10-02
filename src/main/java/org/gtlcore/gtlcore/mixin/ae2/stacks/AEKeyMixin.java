package org.gtlcore.gtlcore.mixin.ae2.stacks;

import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AEKey.class)
public abstract class AEKeyMixin {

    @Unique
    private volatile CompoundTag gTLCore$tagGenericCache;

    @Shadow(remap = false)
    public abstract CompoundTag toTag();

    @Shadow(remap = false)
    public abstract AEKeyType getType();

    /**
     * @author Dragons
     * @reason Performance
     */
    @Overwrite(remap = false)
    public final CompoundTag toTagGeneric() {
        CompoundTag cached = gTLCore$tagGenericCache;
        if (cached == null) cached = gTLCore$saveAndReturnTagGeneric();
        // Callers append stack counts and persistence fields to this tag.
        // Returning the cached instance mutates later key serializations and
        // pattern fingerprints even though the resource itself is unchanged.
        return cached.copy();
    }

    @Unique
    private CompoundTag gTLCore$saveAndReturnTagGeneric() {
        CompoundTag tag = this.toTag();
        tag.putString("#c", this.getType().getId().toString());
        this.gTLCore$tagGenericCache = tag;
        return tag;
    }
}
