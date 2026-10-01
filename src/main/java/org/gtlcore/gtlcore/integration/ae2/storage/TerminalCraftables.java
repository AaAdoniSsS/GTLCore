package org.gtlcore.gtlcore.integration.ae2.storage;

import appeng.api.stacks.AEKey;

import java.util.Set;

/** An immutable snapshot, shared until the provider set actually changes. */
public interface TerminalCraftables {

    Set<AEKey> gtlcore$terminalCraftables();
}
