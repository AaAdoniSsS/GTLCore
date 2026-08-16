package org.gtlcore.gtlcore.config;

import org.gtlcore.gtlcore.GTLCore;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;

@Config(id = GTLCore.MOD_ID)
public class ConfigHolder {

    public static ConfigHolder INSTANCE;
    public static final int DEFAULT_MACHINE_STARTUP_TICK_BUDGET_PER_LEVEL = 32;
    public static final int DEFAULT_MACHINE_STARTUP_AE_TICK_BUDGET_PER_LEVEL = 32;
    public static final int DEFAULT_MACHINE_STARTUP_TICK_TIME_BUDGET_MILLIS = 10;
    public static final int DEFAULT_GTCEU_JEI_SLOW_RECIPE_TYPE_WARNING_MILLIS = 100;
    private static final Object LOCK = new Object();

    public static void init() {
        synchronized (LOCK) {
            if (INSTANCE == null) {
                INSTANCE = Configuration.registerConfig(ConfigHolder.class, ConfigFormats.yaml()).getConfigInstance();
            }
        }
    }

    @Configurable
    public boolean disableDrift = true;
    @Configurable
    public boolean enableSkyBlokeMode = false;
    @Configurable
    @Configurable.Range(min = 1)
    public int oreMultiplier = 4;
    @Configurable
    @Configurable.Range(min = 1)
    public int cellType = 4;
    @Configurable
    @Configurable.Range(min = 1)
    public int spacetimePip = Integer.MAX_VALUE;
    @Configurable
    @Configurable.Range(min = 0)
    public double durationMultiplier = 1;
    @Configurable
    @Configurable.Range(min = 1)
    public int travelStaffCD = 2;
    @Configurable
    @Configurable.Comment({ "更大的数值会让界面显示有问题，推荐在样板管理终端管理" })
    @Configurable.Range(min = 36, max = 360)
    public int exPatternProvider = 36;
    @Configurable
    @Configurable.Comment("Pattern box pages; each page stores 36 encoded patterns")
    @Configurable.Range(min = 1, max = 10)
    @Configurable.Synchronized
    public int patternBoxPages = 2;
    @Configurable
    public boolean enablePrimitiveVoidOre = false;
    @Configurable
    @Configurable.Comment("连锁黑名单,支持通配符*")
    @Configurable.Synchronized
    public String[] blackBlockList = { "ae2:cable_bus", "minecraft:grass_block" };
    @Configurable
    @Configurable.Comment("可能会极小地影响性能")
    public boolean enableSmoothAnimations = true;
    @Configurable
    @Configurable.Comment("ME样板总成输出最小间隔")
    @Configurable.Range(min = 1, max = 100)
    public int MEPatternOutputMin = 5;
    @Configurable
    @Configurable.Comment("ME样板总成输出最大间隔")
    @Configurable.Range(min = 1, max = 200)
    public int MEPatternOutputMax = 80;
    @Configurable
    @Configurable.Comment("是否启用ME库存极限拉取模式(保证机器不会停机, 但是会大幅降低TPS!)")
    public boolean enableUltimateMEStocking = false;
    @Configurable
    @Configurable.Comment("AE2合成更新间隔(tick), 值越大性能越好但响应越慢, 必须是2的幂次(1,2,4,8,16)")
    @Configurable.Range(min = 1, max = 16)
    public int ae2CraftingServiceUpdateInterval = 4;
    @Configurable
    @Configurable.Comment("AE2库存更新间隔(tick), 值越大性能越好但响应越慢, 必须是2的幂次(1,2,4,8,16)")
    @Configurable.Range(min = 1, max = 16)
    public int ae2StorageServiceUpdateInterval = 8;
    @Configurable
    @Configurable.Comment({
            "测试功能：玩家进入手动合成结算界面后，锁定该计划使用的ME库存，直至取消、关闭界面或订单提交。",
            "Experimental: Reserve ME inventory used by a manual crafting plan until it is cancelled, closed, or submitted."
    })
    public boolean enableAe2ManualCraftingInventoryLock = false;
    @Configurable
    @Configurable.Comment({
            "测试功能：将手动合成库存锁的申请、冲突、提取限制、提交和释放效果写入独立日志。",
            "日志文件：logs/gtlcore/ae2-manual-crafting-inventory-lock-*.log",
            "Experimental: Write manual crafting inventory lock acquisition, conflicts, extraction limits, submission, and release effects to a dedicated log.",
            "Log file: logs/gtlcore/ae2-manual-crafting-inventory-lock-*.log"
    })
    public boolean enableAe2ManualCraftingInventoryLockLogging = false;
    @Configurable
    @Configurable.Comment("AE2合成计算模式: LEGACY(原版), FAST(快速), ULTRA_FAST(极快), MAX_FAST(需求聚合)")
    public AE2CalculationMode ae2CalculationMode = AE2CalculationMode.MAX_FAST;
    @Configurable
    @Configurable.Comment("是否启用 MAX_FAST 独立计算性能日志")
    public boolean enableMaxFastCalculationLogging = false;
    @Configurable
    @Configurable.Comment("是否将原生 AE2 CPU 与超限演算阵列的慢发配写入独立日志；关闭时不执行性能计时")
    public boolean enableAe2CraftingDispatchPerformanceLogging = false;
    @Configurable
    @Configurable.Comment({
            "将超限演算阵列的结构检测、成型、失效与 AE 联网检测写入独立日志。",
            "日志文件：logs/gtlcore/transfinite-computation-array-lifecycle-*.log"
    })
    public boolean enableTransfiniteComputationArrayLifecycleLogging = false;
    @Configurable
    @Configurable.Comment({
            "世界加载时分批执行普通 GT 机器的首次 tick，避免大量强加载区块在同一 tick 集中激活。",
            "不延迟 NBT、能力、结构与 AE 节点加载；超限演算阵列本体优先执行。"
    })
    public boolean enableMachineStartupTickBudget = true;
    @Configurable
    @Configurable.Comment("每个维度每 tick 最多首次激活的普通 GT 机器数量")
    @Configurable.Range(min = 1, max = 4096)
    public int machineStartupTickBudgetPerLevel = DEFAULT_MACHINE_STARTUP_TICK_BUDGET_PER_LEVEL;
    @Configurable
    @Configurable.Comment("每个维度每 tick 最多首次激活的 AE 联网 GT 机器数量；不影响 AE 节点加载与拓扑计算")
    @Configurable.Range(min = 1, max = 4096)
    public int machineStartupAeTickBudgetPerLevel = DEFAULT_MACHINE_STARTUP_AE_TICK_BUDGET_PER_LEVEL;
    @Configurable
    @Configurable.Comment("每个维度每 tick 用于首次机器 tick 的最长累计时间；达到后延迟剩余机器到下一 tick")
    @Configurable.Range(min = 1, max = 50)
    public int machineStartupTickTimeBudgetMillis = DEFAULT_MACHINE_STARTUP_TICK_TIME_BUDGET_MILLIS;
    @Configurable
    @Configurable.Comment("延迟 GTCEu JEI 配方注册期间不参与配方索引的文本与按钮，降低客户端启动分配开销")
    public boolean optimizeGtceuJeiRegistration = true;
    @Configurable
    @Configurable.Comment("单个 GTCEu JEI 配方类型注册超过此毫秒数时记录警告")
    @Configurable.Range(min = 1, max = 60000)
    public int gtceuJeiSlowRecipeTypeWarningMillis = DEFAULT_GTCEU_JEI_SLOW_RECIPE_TYPE_WARNING_MILLIS;
    @Configurable
    @Configurable.Comment("单个 AE2 CPU 调度超过此微秒数时记录性能警告")
    @Configurable.Range(min = 1)
    public int ae2CraftingDispatchPerformanceWarningMicros = 5000;
    @Configurable
    @Configurable.Comment("同一个 AE2 CPU 两次性能警告之间的最短 tick 间隔")
    @Configurable.Range(min = 1)
    public int ae2CraftingDispatchPerformanceLogIntervalTicks = 200;
    @Configurable
    @Configurable.Comment("新放置的普通 AE2 / 扩展样板供应器是否默认开启智能翻倍（ME 样板总成仍强制开启）")
    public boolean ae2PatternProviderAutoExpandDefault = false;
    @Configurable
    @Configurable.Comment("编写多方块结构样板过滤的仓室")
    public String[] filterHatch = new String[] { "input_bus", "output_bus", "item_import_bus", "item_export_bus", "input_hatch", "output_hatch", "energy_input_hatch", "energy_output_hatch", "laser_target_hatch", "laser_source_hatch", "computation_transmitter_hatch", "computation_receiver_hatch", "data_transmitter_hatch", "data_receiver_hatch", "maintenance", "muffler", "rotor_holder" };
    @Configurable
    @Configurable.Comment({ "连锁挖掘（不连续模式）时，检查相邻方块的范围", "The range to check adjacent blocks during chain mining (non-continuous mode)" })
    @Configurable.Range(min = 1, max = 20)
    @Configurable.Synchronized
    public int ftbUltimineRange = 4;
    @Configurable
    public boolean sendUpdateMessages = true;

    @Configurable
    @Configurable.Comment("Maximum processing time for one recipe batch, in ticks")
    @Configurable.Range(min = 1)
    public int batchProcessingTimeLimitTicks = 100;

    @Configurable
    @Configurable.Comment("Whether newly placed machines enable batch processing by default when supported")
    public boolean batchProcessingEnabledByDefault = false;

    @Configurable
    @Configurable.Comment({
            "是否将已开启批处理的机器的触发与未触发原因写入独立日志；诊断期间可能增加磁盘 I/O",
            "每台机器一个日志文件：logs/gtlcore/batch-processing/<时间戳>-<机器名称>-<维度>-<坐标>.jsonl",
            "Write batch-triggered and batch-not-triggered reasons for batch-enabled machines to dedicated logs; diagnostic use may increase disk I/O.",
            "One log per machine: logs/gtlcore/batch-processing/<timestamp>-<machine-name>-<dimension>-<position>.jsonl"
    })
    public boolean enableBatchProcessingLogging = false;

    @Configurable
    public String[] mobList1 = new String[] { "chicken", "rabbit", "sheep", "cow", "horse", "pig", "donkey", "skeleton_horse", "iron_golem", "wolf", "goat", "parrot", "camel", "cat", "fox", "llama", "panda", "polar_bear" };
    @Configurable
    public String[] mobList2 = new String[] { "ghast", "zombie", "pillager", "zombie_villager", "skeleton", "drowned", "witch", "spider", "creeper", "husk", "wither_skeleton", "blaze", "zombified_piglin", "slime", "vindicator", "enderman" };
}
