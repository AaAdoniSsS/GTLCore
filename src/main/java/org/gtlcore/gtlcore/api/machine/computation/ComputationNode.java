package org.gtlcore.gtlcore.api.machine.computation;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;

import java.util.List;

/** Adjacency is cached separately from the current power/structure state. */
public interface ComputationNode {

    List<IOpticalComputationProvider> gtlcore$computationLinks();

    default List<ComputationSource> gtlcore$localComputationSources() {
        return List.of();
    }

    default boolean gtlcore$computationOnline() {
        return true;
    }

    default boolean gtlcore$requiresComputationBridge() {
        return false;
    }

    default void gtlcore$computationTransferred(long amount) {}
}
