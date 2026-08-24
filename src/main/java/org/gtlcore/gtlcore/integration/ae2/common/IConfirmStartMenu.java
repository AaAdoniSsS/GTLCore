package org.gtlcore.gtlcore.integration.ae2.common;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.menu.me.common.IClientRepo;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IConfirmStartMenu {

    short GUI_SYNC_MISSING_CRAFT_AVAILABLE = 100;

    IClientRepo gtlcore$getClientRepo();

    /**
     * 客户端：计划相关键的实时网络库存（服务端持续同步），按 repo 更新惰性重建。
     * 首个同步包到达前返回 null，此时不能判定库存已被消耗。
     */
    @Nullable
    KeyCounter gtlcore$getLiveStored();

    /**
     * 客户端：当前实际缺失的材料（计划缺失 + 算料后库存被其他任务消耗的条目），带缓存。
     */
    List<AEKey> gtlcore$getMissingNow();

    /** 客户端：当前选中的 CPU 是否支持在材料缺失时创建等待任务。 */
    boolean gtlcore$isMissingCraftAvailable();
}
