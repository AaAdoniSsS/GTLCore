package org.gtlcore.gtlcore.integration.ae2.crafting;

import java.util.Map;

public interface ICraftingStatusReasons {

    Map<Long, Integer> gtlcore$getReasonMasks();

    void gtlcore$setReasonMasks(Map<Long, Integer> reasonMasks);
}
