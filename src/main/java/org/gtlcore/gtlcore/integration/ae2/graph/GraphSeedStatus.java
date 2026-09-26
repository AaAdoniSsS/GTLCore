package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.graph.core.GraphPlan;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Display proof scope without presenting an incumbent as a certified optimum. */
public final class GraphSeedStatus {

    private GraphSeedStatus() {}

    public static void write(FriendlyByteBuf buffer, GraphPlan.SeedOptimality proof) {
        buffer.writeBoolean(proof != null);
        if (proof == null) return;
        buffer.writeVarInt(proof.lowerTypeBound());
        buffer.writeVarInt(proof.types());
        buffer.writeBoolean(proof.cardinalityProven());
        buffer.writeBoolean(proof.quantitiesParetoProven());
        buffer.writeBoolean(proof.fundedPreview());
    }

    public static GraphPlan.SeedOptimality read(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return null;
        int lower = buffer.readVarInt(), types = buffer.readVarInt();
        if (types < 0 || types > 100_000) throw new IllegalArgumentException("Invalid seed type count");
        return new GraphPlan.SeedOptimality(lower, types, buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }

    public static List<Component> tooltip(GraphPlan.SeedOptimality proof) {
        if (proof == null) return List.of(Component.translatable("gtlcore.ae.graph.seed_unproven"));
        List<Component> lines = new ArrayList<>();
        lines.add(proof.cardinalityProven() ? Component.translatable("gtlcore.ae.graph.seed_types_proven", proof.types()) :
                Component.translatable("gtlcore.ae.graph.seed_types_bound", proof.types(), proof.lowerTypeBound()));
        lines.add(Component.translatable(proof.quantitiesParetoProven() ? "gtlcore.ae.graph.seed_quantities_proven" : "gtlcore.ae.graph.seed_quantities_unproven"));
        lines.add(Component.translatable(proof.fundedPreview() ? "gtlcore.ae.graph.seed_scope_refill" : "gtlcore.ae.graph.seed_scope_stock"));
        return lines;
    }
}
