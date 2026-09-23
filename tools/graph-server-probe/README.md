# 隔离 Forge 服务端验收探针

只用于一次性测试世界。脚本会在固定坐标放置或替换方块、存储盘和样板，不能复制进实际存档。
测试模组不进入 Core 的 Gradle source set，也不随发布 JAR 打包。

要求 Java 17+、Python 3.9+、Forge 47.3.7、AE 15.4.10 及兼容矩阵中列出的附属。
此探针本身直接依赖 AE2CT / wildcard；测试可选附属缺席时同时移走探针和两份 KubeJS 脚本。

服务端停止后，在仓库目录执行：

```powershell
python tools/graph-server-probe/build.py --core-jar build/libs/gtlcore-1.2.3.2-fix2.jar
```

默认服务器目录 `.local/graph-server`，JDK 从 `JAVA_HOME` 读取；可用 `--server`、`--java-home` 指定。
全部编译输出与临时文件留在当前仓库。将本目录的两份 JS 复制到隔离服务器的
`kubejs/server_scripts`，完整重启服务器，配置 `ae2CraftingEngine: GRAPH`、`ae2CalculationMode: MAX_FAST`、
`ae2GraphDiagnosticLogging: true`。

编译前要求 mods 中恰好只有一个 Core JAR，其 SHA-256 必须与 `--core-jar` 相同；
启动后检查 `[Graph Probe] Core code_source=` 与 `node_fee_class=` 两行，确认实际加载包。

`graph_probe.js` 使用 y=65 的原版 CPU 测试台；具体布局和命令内有坐标。
`graph_array_probe.js` 可通过 `grapharrayplace` 建造实际注册预览中的超限阵列，再以
`grapharraywire` 连接 ExtendedAE 提供者与存储。每次改变样板或拓扑后至少等待一秒，再执行下一步。

回收环的执行顺序：

1. `grapharraystatus`、`grapharraycycleload`，等待，再 `grapharraycyclestart`。
2. `grapharraycyclewaiting` 应为 iron=1；`grapharraycycleadvance` 后等待，状态应为 gold=1。
3. `save-all`、`stop`；用同一 Core 和存档重启。核对 link UUID 与 gold=1 均保留。
4. 再执行八次 `grapharraycycleadvance`，每次之间等待实际下一次投料。
5. `grapharraycycledone` 断言最终 3 绿宝石、1 石头催化剂、燃料和中间体为零、CPU 完成。

箱子承担可观察的机器输入缓冲，`cycleadvance` 手动模拟加工回料；不能把它描述为真实 GT 配方加工测试。
样板最后一步的主输出必须为绿宝石，石头为副产物，符合 AE 的可请求物注册规则。

`graphisolation` 检查实际旧 CraftingCalculation 构造、CraftingCpuLogic.executeCrafting、
MaxFastExecutor.execute 的必需 Mixin 计数均为零。**先做这个检查，再运行显式旧算法 A/B**。
`graphbenchmark` 在已有 500 橡木原木、橡木板样板的原版测试台上比较 10 次 2,000 木板规划；
两算法从主线程发起，比较产物、投入、缺料、配方次数和 bytes，单独记录 graph 全流程与求解时间。
它是测试专用旧算法调用，不能计入 graph 生产路径的旧核心计数。
`grapharraybenchload` 在超限测试网络安装 32 层 NBT 区分的处理链；等待注册/库存更新后，
`grapharraybenchmark` 对同一 1,000 单位库存和请求执行 10 次 A/B。

其他命令覆盖实际通配符展开槽位、MAE2 P2P、Requester 继续/取消、在途后缀重规划以及 AE2CT 包往返。
以 `docs/graph-addon-compatibility.md` 中的日志和断言为准，不把未运行的命令当成验收通过。

`graph_reuse_probe.js` 为第三份可选脚本，覆盖锻造模板自复制环和 CPU 重用。它新增两张测试配方，必须完整重启加载。
沿用原版测试台的 CPU / 提供者 / 分子装配室；命令只允许在隔离世界使用。

1. `graphreuseload` 重置库存和两张真实合成样板，等待注册和库存更新。
2. `graphreusefirst` 请求 100 模板，在第 86 次派发后取消；等待后执行 `graphreusesecond`，应正常完成。
3. `graphreuserefill` 只补充库存；`graphreuseexpand` 在第 12 次派发后追加协处理器，触发 AE 原有 CPU 重组和掉料。
4. `graphreuserecover` 把附近掉落物重新存回 ME；等待在途装配完成，使用 `graphreusestatus` 检查库存。
5. 再运行 `graphreusesecond`，完成后再 `graphreuseone`，不重进世界或重装样板。
6. `graphreusestale` 临时更改有效样板输出且不发 provider 更新事件，断言提交拒绝、零物料转移、缓存失效和未完成快照不能重新发布旧缓存；随后 `graphreuseone` 应成功。

`graphreusestatus` 输出空闲时的 `diamond + 9×diamond_block + 7×template` 与 `netherrack + template`，
这两项在测试配方加工、取消、CPU 重组后回收掉料时应守恒；补充库存会改变基准，须单独记录。
故障注入覆盖缓存失效和诊断能力，不等于复现用户报告的未知偶发现象。

## 大规模规划 A/B

`GraphStressProbe.java` 与 `graph_stress_probe.js` 是第四份可选测试脚本。
编译探针后把脚本复制进隔离服务端，完整重启，确认原版测试台 CPU 位于 `(1,65,0)`，
驱动器位于 `(0,65,1)`。开启 Graph 与 MAX_FAST 的诊断日志，再运行 `graphstress`。
命令会替换该驱动器的测试存储盘，挂载仅用于规划的合成提供者，禁止向它提交生产任务。

每组库存和配方不变，新旧算法串行执行并交替先后；每组首笔单列，再采七个热请求。
请求数量 1,048,576，链深度 128 / 512 / 2,048 / 8,192；共享分支为每层 8 路、
8 / 16 / 24 / 64 层。所有资源使用带不同 NBT 的纸，配方是真实 AEProcessingPattern。
这测量的是合成规划，不包含物品实际生产和整包脚本加载时间。

计时区间之后独立解释配方，检查每个配方执行前的库存、最终目标、原料上限。
比较 used/missing/emitted、目标、simulation、patternTimes 和 bytes。
不同取整导致的两个合法计划保留 `*_equal=false`；不会把它们冒充相同工作量的加速。
同样用料和配方次数却出现 bytes 差异，或任一计划解释失败，会停止该用例。
30 秒测试超时会取消对应请求；不代表生产配置设有 30 秒上限。

汇总命令：

```powershell
python tools/summarize-graph-stress.py .local/server-stress-final.log .local/graph-stress-final.json
```

该 JSON 保留逐样本值、首笔、热中位数、最近秩 p95 和不一致结果。只有七个热样本，p95
实际上为该组最大值，不能据此承诺稳定尾延迟。测试包哈希和性能结论见 `docs/graph-stress-performance.md`。

## 亿级数量与多装配机

- `graphstress100m` 请求 100,000,000，`graphstress3b` 请求 3,000,000,000。
  数量由探针挂载的长整型 MEStorage 提供，避免存储盘容量限制污染规划结果；实际算法仍走相同 AE 入口。
  每次完整套件结束才运行下一套，汇总脚本按拓扑及数量区分，避免混合统计。
- 第五份脚本 `graph_parallel_probe.js` 使用 `(33,65,0)` 的 64k CPU、3 个协处理器，
  `(34,65,0)` 一个样板供应器及五个相邻分子装配机。
- `graphparallelplace` 建造隔离装置；`graphparallelload` 装入 1 种子、300 钻石块、2,000 下界岩；
  `graphparallelload4` 装入 4 种子。等待注册完成后执行 `graphparallelstart`，请求 100 模板。
- 探针只观察真实装配机接受/占用，不模拟回料；记录完成 tick、成功派发、接受过任务的装配机数量、
  同 tick 接受峰值与同时忙碌峰值。完成时检查两项物料守恒。
- `graphparallelstart` 结束前不得重置盘；性能测量期间不要同时编译或运行另一套压力测试。
  重启前必须 `save-all` / `stop`，测试模组永远不随 Core 分发。

这里运行的是 **nogui 专用服务端，不是无头 Minecraft 客户端**。纯 JVM/AE 接口回归、
真实服务端规划/执行测试、用户客户端视觉验收是三个不同证据层级；服务端通过不代表客户端画面已实测。

多批次重载：`graphparallelload` 后运行 `graphparallelcheckpoint`、`graphparallelstart`。
脚本会在至少 12 次派发、至少 2 个批次在途时记录 UUID/expected，保存并停止隔离服。
同包重启后及时执行 `graphparallelresume`，核对同一个任务 UUID、最终产物及两项守恒。
恢复后的计时只包含恢复监测区间，不能与完整订单性能表混用。

## 用户 ADD 3.2.8Custom_SubSpace-fix2

需要 Java 21 和 SGJourney 0.6.44；复制用户实际启用的 JAR 到隔离目录，保存 SHA-256。
`graph_addon_probe.js` 放 server_scripts；`graph_addon_items.js` 放 startup_scripts，完整重启。
后者仅保留整包 `item.js` 中两个容器的原始 `event.create` 声明，避免缺失 ID 被替代为 barrier 污染测试。

1. `graphaddoneffective` 调用实际 ADD 的 FOA 改写，检查容器 4 入 1 出、NBT、30 亿输入和图计划。
2. `graphaddonplace`、`graphaddonwire` 建真实超级样板总成及 AE 网络；等待，再 `graphaddonload4`。
3. 等待存储盘挂载后 `graphaddonstock`，应 inserted=100 / same_grid=true。`graphaddonplan4` 应 input=2 / target=8。
4. `graphaddonmode8`，等待 provider 刷新；`graphaddonstale` 应拒绝旧计划且 stock=100；
   `graphaddonplan8` 应 input=1 / target=8。

该组验证有效样板、规划和提交复核，不模拟整座 FOA 的真实机器加工。
特殊无限分子装配机和另一条 ADD 上游分支的库存账本不在这组整机验收范围。
