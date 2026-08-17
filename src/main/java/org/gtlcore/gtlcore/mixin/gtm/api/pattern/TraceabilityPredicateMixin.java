package org.gtlcore.gtlcore.mixin.gtm.api.pattern;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(TraceabilityPredicate.class)
public class TraceabilityPredicateMixin {

    @Shadow(remap = false)
    public List<SimplePredicate> common;
    @Shadow(remap = false)
    public List<SimplePredicate> limited;

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public boolean test(MultiblockState blockWorldState) {
        blockWorldState.io = IO.BOTH;
        boolean flag = false;

        for (var p : this.limited)
            if (p.testLimited(blockWorldState)) {
                flag = true;
            }

        if (!flag) for (var p : this.common)
            if (p.test(blockWorldState)) {
                flag = true;
                break;
            }

        if (flag) blockWorldState.setError(null);

        return flag;
    }
}
