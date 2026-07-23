package org.gtlcore.gtlcore.integration.ae2.crafting;

public interface ICraftingStatusBulkActions {

    String ACTION_SUSPEND_ALL = "suspendAllCrafting";
    String ACTION_CANCEL_ALL = "cancelAllCrafting";

    void gtlcore$suspendAllCrafting();

    void gtlcore$cancelAllCrafting();
}
