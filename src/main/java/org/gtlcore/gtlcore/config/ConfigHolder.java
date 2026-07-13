package org.gtlcore.gtlcore.config;

import org.gtlcore.gtlcore.GTLCore;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;

@Config(id = GTLCore.MOD_ID)
public class ConfigHolder {

    public static ConfigHolder INSTANCE;
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
    @Configurable.Comment("AE2合成计算模式: LEGACY(原版), FAST(快速), ULTRA_FAST(极快)")
    public AE2CalculationMode ae2CalculationMode = AE2CalculationMode.ULTRA_FAST;
    @Configurable
    @Configurable.Comment("允许普通AE2/扩展样板供应器使用智能翻倍")
    public boolean enableAe2PatternProviderAutoExpand = false;
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
    public String[] mobList1 = new String[] { "chicken", "rabbit", "sheep", "cow", "horse", "pig", "donkey", "skeleton_horse", "iron_golem", "wolf", "goat", "parrot", "camel", "cat", "fox", "llama", "panda", "polar_bear" };
    @Configurable
    public String[] mobList2 = new String[] { "ghast", "zombie", "pillager", "zombie_villager", "skeleton", "drowned", "witch", "spider", "creeper", "husk", "wither_skeleton", "blaze", "zombified_piglin", "slime", "vindicator", "enderman" };
}
