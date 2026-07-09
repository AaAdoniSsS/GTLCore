package org.gtlcore.gtlcore.integration.ae2.throughput;

import appeng.api.storage.MEStorage;

import java.util.Collection;
import java.util.List;

public interface ThroughputStorageView {

    default Collection<MEStorage> gtlcore$getChildStorages() {
        return List.of();
    }

    default long gtlcore$getTopologyVersion() {
        return 0L;
    }
}
