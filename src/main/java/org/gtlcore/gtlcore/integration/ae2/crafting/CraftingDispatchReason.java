package org.gtlcore.gtlcore.integration.ae2.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public enum CraftingDispatchReason {

    JOB_SUSPENDED(1, "gtlcore.ae2.crafting.dispatch_reason.job_suspended"),
    CPU_INACTIVE(2, "gtlcore.ae2.crafting.dispatch_reason.cpu_inactive"),
    CPU_OPERATION_LIMIT(4, "gtlcore.ae2.crafting.dispatch_reason.cpu_operation_limit"),
    NO_PROVIDER(8, "gtlcore.ae2.crafting.dispatch_reason.no_provider"),
    PROVIDERS_BUSY(16, "gtlcore.ae2.crafting.dispatch_reason.providers_busy"),
    WAITING_FOR_INPUTS(32, "gtlcore.ae2.crafting.dispatch_reason.waiting_for_inputs"),
    INSUFFICIENT_POWER(64, "gtlcore.ae2.crafting.dispatch_reason.insufficient_power"),
    PROVIDER_REJECTED(128, "gtlcore.ae2.crafting.dispatch_reason.provider_rejected");

    public static final String HEADING_TRANSLATION_KEY = "gtlcore.ae2.crafting.dispatch_reason.heading";
    public static final String NOT_CHECKED_TRANSLATION_KEY = "gtlcore.ae2.crafting.dispatch_reason.not_checked";

    private static final List<CraftingDispatchReason> DISPLAY_ORDER = List.of(values());

    private final int mask;
    private final String translationKey;

    CraftingDispatchReason(int mask, String translationKey) {
        this.mask = mask;
        this.translationKey = translationKey;
    }

    public int mask() {
        return mask;
    }

    public String translationKey() {
        return translationKey;
    }

    public static List<CraftingDispatchReason> decode(int mask) {
        if (mask == 0) {
            return Collections.emptyList();
        }
        List<CraftingDispatchReason> reasons = new ArrayList<>();
        for (CraftingDispatchReason reason : DISPLAY_ORDER) {
            if ((mask & reason.mask) != 0) {
                reasons.add(reason);
            }
        }
        return reasons;
    }
}
