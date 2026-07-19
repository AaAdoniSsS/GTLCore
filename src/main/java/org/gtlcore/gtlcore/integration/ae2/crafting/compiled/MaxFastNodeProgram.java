package org.gtlcore.gtlcore.integration.ae2.crafting.compiled;

import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeNode;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeProcess;

import appeng.crafting.CraftingTreeNode;

public final class MaxFastNodeProgram {

    public enum Kind {
        SINGLE_PATH,
        TERMINAL,
        BASELINE_TAIL
    }

    private final Kind kind;
    private final ICraftingTreeNode owner;
    private final ICraftingTreeProcess process;
    private final CraftingTreeNode[] childNodes;
    private final long[] childMultipliers;
    private final long nodeAmount;
    private final long outputPerPattern;

    private MaxFastNodeProgram(Kind kind, ICraftingTreeNode owner, ICraftingTreeProcess process,
                               CraftingTreeNode[] childNodes, long[] childMultipliers, long nodeAmount,
                               long outputPerPattern) {
        this.kind = kind;
        this.owner = owner;
        this.process = process;
        this.childNodes = childNodes;
        this.childMultipliers = childMultipliers;
        this.nodeAmount = nodeAmount;
        this.outputPerPattern = outputPerPattern;
    }

    public static MaxFastNodeProgram singlePath(ICraftingTreeNode owner, ICraftingTreeProcess process,
                                                CraftingTreeNode[] childNodes, long[] childMultipliers,
                                                long nodeAmount, long outputPerPattern) {
        return new MaxFastNodeProgram(
                Kind.SINGLE_PATH,
                owner,
                process,
                childNodes,
                childMultipliers,
                nodeAmount,
                outputPerPattern);
    }

    public static MaxFastNodeProgram terminal(ICraftingTreeNode owner, long nodeAmount) {
        return new MaxFastNodeProgram(Kind.TERMINAL, owner, null, null, null, nodeAmount, 0);
    }

    public static MaxFastNodeProgram baselineTail(ICraftingTreeNode owner, long nodeAmount) {
        return new MaxFastNodeProgram(Kind.BASELINE_TAIL, owner, null, null, null, nodeAmount, 0);
    }

    public Kind kind() {
        return this.kind;
    }

    public ICraftingTreeNode owner() {
        return this.owner;
    }

    public ICraftingTreeProcess process() {
        return this.process;
    }

    public int childCount() {
        return this.childNodes.length;
    }

    public CraftingTreeNode childNode(int index) {
        return this.childNodes[index];
    }

    public long childMultiplier(int index) {
        return this.childMultipliers[index];
    }

    public long nodeAmount() {
        return this.nodeAmount;
    }

    public long outputPerPattern() {
        return this.outputPerPattern;
    }
}
