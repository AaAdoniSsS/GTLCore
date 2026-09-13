package org.gtlcore.gtlcore.integration.ae2.wireless;

import appeng.api.networking.IGridNode;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Per-network topology snapshot; invalidated explicitly by topology events. */
final class WirelessAeTopologySnapshot {

    private final Set<IGridNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private long version;
    private boolean complete;

    void invalidate() {
        visited.clear();
        complete = false;
        version++;
    }

    boolean contains(IGridNode node) {
        return complete && visited.contains(node);
    }

    boolean complete() {
        return complete;
    }

    void replace(Set<IGridNode> component) {
        visited.clear();
        visited.addAll(component);
        complete = true;
        version++;
    }

    long version() {
        return version;
    }

    int size() {
        return visited.size();
    }
}
