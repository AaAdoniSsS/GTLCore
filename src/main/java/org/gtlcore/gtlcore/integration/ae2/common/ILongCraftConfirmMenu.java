package org.gtlcore.gtlcore.integration.ae2.common;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;

public interface ILongCraftConfirmMenu {

    boolean gtlcore$planLongAmountJob(AEKey whatToCraft, long amount, CalculationStrategy strategy);
}
