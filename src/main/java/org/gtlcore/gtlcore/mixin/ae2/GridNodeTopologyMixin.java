package org.gtlcore.gtlcore.mixin.ae2;

import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime;

import appeng.api.networking.IGridConnection;
import appeng.me.GridNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Invalidates cached wireless topology whenever AE2 changes a node's edges. */
@Mixin(value = GridNode.class, remap = false)
public abstract class GridNodeTopologyMixin {

    @Inject(method = "addConnection", at = @At("TAIL"), remap = false)
    private void gtlcore$invalidateTopology(IGridConnection connection, CallbackInfo ci) {
        invalidateIfWired(connection);
    }

    @Inject(method = "removeConnection", at = @At("TAIL"), remap = false)
    private void gtlcore$invalidateTopologyOnRemove(IGridConnection connection, CallbackInfo ci) {
        invalidateIfWired(connection);
    }

    @Inject(method = "destroy", at = @At("HEAD"), remap = false)
    private void gtlcore$invalidateTopologyOnDestroy(CallbackInfo ci) {
        WirelessAeNetworkRuntime.invalidateTopology();
    }

    private static void invalidateIfWired(IGridConnection connection) {
        try {
            // Wireless GridHelper connections are virtual edges and do not alter the in-world BFS topology.
            if (connection != null && !connection.isInWorld()) return;
        } catch (RuntimeException ignored) {
            // An indeterminate edge is treated conservatively as a topology change.
        }
        WirelessAeNetworkRuntime.invalidateTopology();
    }
}
