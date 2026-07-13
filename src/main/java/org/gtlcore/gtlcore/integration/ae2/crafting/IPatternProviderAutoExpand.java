package org.gtlcore.gtlcore.integration.ae2.crafting;

import appeng.api.crafting.IPatternDetails;

public interface IPatternProviderAutoExpand {

    long gtlcore$getMaxPatternOperations(IPatternDetails pattern, long requestedOperations);
}
