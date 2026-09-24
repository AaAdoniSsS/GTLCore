# Graph crafting implementation record

## Baseline

Worktree `core-ae-cycle`, branch `feat/ae-cycle-support`, fork
`autumn-2-net/GTLCore`, baseline `13a7328e1da18d04df41dceeef3ec4f3efee79b6`
(`origin/gtl-1431-skyblock`). Previous performance experiments remain in their
original worktrees. The design's older `208cbe92` baseline is not used.

Minecraft 1.20.1; Forge 47.3.7; GTCEu 1.4.4; LDLib 1.0.29.b;
AE dependency `curse.maven:applied-energistics-2-223794:7148487`.
AE entry points are checked against this dependency's bytecode, decompiled into
the ignored project-local `.local/ae-sources` directory.

## Differences from the design's reference baseline

- Legacy calculation defaults to `MAX_FAST`, with compiled execution of the
  planning graph. Keep this setting independent of engine selection.
- There is a second CPU implementation (`TransfiniteCraftingLogic`), as well as
  pattern relays, capacity-limited auto expansion, manual inventory locking,
  missing-material jobs, suspension and dispatch-reason UI.
- `CraftingPatternAutoExpand` negotiates the actual operation limit. Expanded
  dispatch power is the scaled input quote divided by the batch's operations
  (`CraftingPatternPower`); this is existing GTL gameplay behavior.
- The molecular-assembler IO part accepts inputs into a refund buffer and
  returns true if shared tools are absent. Graph dispatch needs a preflight.

## Lifecycle and ownership

`CraftingService.beginCraftingCalculation` -> snapshot on the server thread ->
pure graph planner -> `ICraftingPlan` view. Submission still uses AE's CPU
selection and existing capacity/preferences, then routes by **plan/task kind**.
Native CPU shell: `CraftingCPUCluster.craftingLogic`. Alternate CPU shell:
`TransfiniteCraftingCPU.craftingLogic`. Graph tasks must route tick, output,
queries, cancellation, inventory disposal and NBT to the same graph owner.
Changing the global option only changes new calculations.

The service's existing outer update interval is the only service throttle.
Graph's bounded dispatch work is measured per invocation, not another copy of
that interval. Waiting provider polls use server tick timestamps.

## Validation status

独立规划器和执行器、原生/跨并 CPU 路由、预览、存档以及故障处理已实现，
核心、真实 AE 依赖上的自动测试及隔离生产服务端订单已经执行。
逐项证据和仍需客户端验收的范围见 `graph-addon-compatibility.md`，不将某个样例通过等同于整包全部功能通过。
测试和基准见 [graph-crafting-validation.md](graph-crafting-validation.md)。
本地构建配置、下载工具、缓存不属于功能补丁。

## 配置与使用

沿用 `ConfigHolder` / configuration 2.2.0，配置为游戏目录下的 `config/gtlcore.yaml`。
实际 YAML 库读写测试确认枚举写作大写、下划线分词：

```yaml
ae2CraftingEngine: GRAPH
ae2GraphSeedPolicy: PRESERVE
ae2GraphPlannerNoticeAfterMs: 2000
ae2GraphPlannerTimeoutMs: 0
ae2GraphPlannerThreads: 4
ae2GraphPlannerMaxRequests: 16
ae2GraphPlannerMaxSteps: 10000000
ae2GraphPlannerMemoryMiB: 128
ae2GraphDiagnosticLogging: true
```

默认引擎 `LEGACY`、保种策略 `PRESERVE`、诊断关闭。工作线程默认不超过 4，并按处理器数量调整。
2 秒是慢规划提示；硬超时默认 0，表示禁用。队列、搜索和内存限制仍独立生效。
单请求估算内存预算范围为 16–1024 MiB，默认 128 MiB；256 MiB 是旧配置上限，不是算法容量限制。
预算按需累计，超过后返回内存限制结果，不会预先分配整个额度，也不是 JVM 堆或 AE 合成 CPU 字节上限。
多个请求使用独立预算；实际对象、共享缓存及其他模组内存不等于这个估算值。
已有配置中的 32 或其他值会保留；要使用新默认，修改 `ae2GraphPlannerMemoryMiB: 128` 或删除该项后重新生成。
未发布的 `ae2GraphPlannerBudgetMs` 已移除，即使旧配置保留它也不会恢复两秒硬截止。
另一种种子策略是 `ALLOW_CONSUME`。
引擎切换要求重启。`ae2CalculationMode: MAX_FAST` 仅控制旧计算器。
没有新增 `crafting-engine` 这种未经配置库支持的别名，也没有第二套 TOML 配置。

`PRESERVE` 留下已选循环顺序的一组可重启材料；`ALLOW_CONSUME` 可以在内部最后一次使用后把种子交给下游。
两种模式都禁止使用尚未返回的物料。预览显示实际初始提料和最终保种数量；
现有催化剂用于扩大批量时同样计入初始用料。

AE 手动请求根产物沿用“制造本次请求数量”的语义，不直接把根产物现货抵作新制造数量。
库存可以满足中间依赖和提供种子。纯核心另有允许根现货满足目标的模式，设计 C01/C02 的直接目标示例使用后者。
无配方的直接 emitable 目标等待实际回流。

预览包增加了保种信息，客户端和服务端需要安装相同构建。

## 实际依赖核对补充

- AE JAR 的 `META-INF/mods.toml` 确认版本 **15.4.10**。
- 电路抽取现在由 `MEBufferPatternHelper` / 槽位缓存处理，旧设计中的 `PatternCircuitHandler` 已不是当前类名。
- AE 流体能量的操作单位为 **125 mB**，与显示用的 1000 mB/B 分开。实际适配器测试核对了这个单位。
- 当前基线还存在虚拟材料包装；graph 继续使用原提供者解释包装和机器内部配置。
- 对 AE、ExtendedAE 7144417、无线终端 5162352 / 15.3.3、MAE2 1.6.1、GTMThings 1.3.5.b、GTCEu 1.4.4、wildcard 0.1.2-gtl
  的已声明 mixin 做了类目标扫描。发现 wildcard 和 MAE2 修改样板提供者，MAE2 包含 P2P 派发/返料路径；未发现这些外部依赖另一个直接覆盖
  `CraftingCpuLogic` 的已声明 mixin。静态扫描不替代 Forge 变换后的运行验证。

## 生命周期入口

| 入口 | graph 归属 |
| --- | --- |
| 计算、取消计算、CRAFT_LESS | `CraftingEngineRouter`，主线程分片快照后进入独立有界规划池 |
| CPU 选择、容量、提料、提交 | 保留外层选择，`GraphCpuController` 重验并取得物料 |
| 原生/跨并 CPU tick | `GraphJobRuntime`，旧覆盖方法内显式路由，避免两个执行器同时操作 |
| insert、waiting、pending、进度 | graph 的实际库存和预计输出索引；SIMULATE 不修改状态 |
| 请求者 link、最终交付 | 实际接收多少才扣多少；内部步骤和回流结算前不提前交付种子 |
| 取消、退料、网络满 | 保留真实持有物；机器已接收的输入不凭空退款；晚到输出进入网络 |
| 原生 CPU 拆除 | 取消后显式把剩余持有物交给 AE 原有掉落库存 |
| NBT | `gtlcoreGraphJob` / `engineKind=graph` / schemaVersion=3，与 legacy job 分开 |

已有 graph 任务按自身引擎类型恢复，与当前全局选项无关。协处理器预算沿用三次调用的操作历史，
一次批量推送算一次操作，不按物料数计操作。CPU bytes 用精确分数累计资源/执行次数费用，
并保留每个逻辑依赖出现的 8 bytes 节点费用；共享 DAG 以记忆计数计算路径重数，不真正展开树。
旧算法没有合法循环计费树，graph 将循环回边计作一次叶引用，再加全部实际次数和物料费用。
不会因为计划压缩就给出免费 CPU 容量折扣。

## 算法与缓存

规范化实际注册样板的输入槽、候选、multiplier、返回容器和全部声明输出；派发仍引用原样板对象。
不从机器、供料仓或代理视图里额外复制网络物料。库存锁和 AE 模拟提取选项沿用原接口。

库存捕获已经引用 AE 的缓存库存，按本次相关资源读取数量，再把独立数量表交给后台；
并不每次复制整个 ME 库存。`SNAPSHOT` 还包含依赖发现、实际样板查询、候选有效性校验、
返还物读取和变体组合展开，因此它的耗时不等于“复制库存”的耗时。
指纹编码、目录索引、图构建、求解与计划处理已在后台执行。

进一步将变体展开移到后台是可行方向，尚未实现：主线程先捕获每槽合法候选的确定值、倍率、
配置物标记与返还物，后台再枚举混合输入和跨槽组合、合并数量。
这需要保留变体上限及枚举顺序、依赖覆盖、失效判断和跨阶段预算；
不能直接把当前规范化函数整体放到后台，因为其中仍调用附属的 `isValid`、`getRemainingKey` 等方法。
依赖发现也要决定捕获哪些样板；若完全放到后台，就需要先捕获足够完整的有效样板索引，
或在后台发现新依赖时向主线程继续申请数据，单纯复制目标样板和库存还不够。

选定资源—配方关系后用迭代 Kosaraju 分析 SCC，DAG 反向合并共享需求和多输出。
计划使用 `Batch`、`Sequence`、`Repeat`，前缀需求、净变化和库存峰值用 BigInteger 摘要计算。
执行次数不会变成相同数量的计划节点。自复制依据真实工作种子扩大批量；
单份返回催化剂仍然只支持一份同时投入。

循环先尝试起点轮换；最多 6 个配方时做至多 128 个顺序、各系数 1..4 的有界局部搜索，
总时间与节点数受预算限制。外部造种子路径排除所属 SCC 后寻找，再重新计算主计划。
每个成功计划都有前缀可执行、最终目标、保种义务和数值边界验证；不声称全局最优。

快速区域选择之后还有原生多来源分配搜索，使用稀疏撤销日志和批量见证。小分配完整枚举，
大分配使用有界断点；无法证明的失败返回 UNKNOWN 或具体资源上限，不冒充全局缺料结论。
构图、SCC、候选搜索、摘要与验证保存续算上下文。单订单较大的边构建分区共用同一线程额度，
不等待邻居 Future，不依赖旧 simulateFor 时间片。

结构快照 LRU 上限 32 个目标，编译结构 LRU 上限 128。提供者版本、RecipeManager 更换、
相关模糊键集合变化会失效；每次重读库存，提交/派发仍重验当前输入与返还语义。
这是内存结构缓存，不是可以跳过验证的物料答案缓存。

已接受批次产物、外部补料、真实库存和回收义务分别记账。失效样板只触发未承诺后缀重规划，
保留在途批次与已提交历史；失败后登记相关库存 watcher / 提供者签名，等待相关条件变化。

Crafting Ring 从确认页打开，按原生计划 UUID 查询只读分页视图。资源共享、选定来源、
SCC 环与 Batch/Sequence/Repeat 分开显示。视图构造同样使用有界后台工作片；查看不提料、不提交任务。
AE2CT 1.1.1 保留强转和无限环展开修复，复杂计划的完整信息由 Crafting Ring 提供。

普通纯 DAG 使用资源到消费者索引及去重就绪队列，可同时派发独立分支。
缺材料节点由真实返回事件唤醒，缺通知的 busy/能量/容量等待保留定时重试。
循环和保种路径保留经过验证的阶段屏障。

## 故障保证与限制

owned 只表示实物，预计输出不能取料。派发先检查数量、提供者及能量，再将输入移入 escrow。
接受后按实际批量扣次数和登记产物；明确无副作用的拒绝才退回输入。
false 却修改输入计数器、抛异常等不明情况进入 `NEEDS_ATTENTION`，不自动重发。
派发/交付回调里发生同步存档也会保存不明转移记录。退款或交付抛异常同样暂停。

普通 AE 提供者没有事务/幂等协议，无法保证 CPU 与其他区块机器在任意崩溃点端到端 exactly-once。
若提供者私下转移物料、返回 false 且不改变输入计数器，普通接口也无法判定。
未知/损坏 schema 保留原始 NBT 并暂停；不把任意损坏数据当成可自动恢复的物料证明。

- 有界搜索不完备。UNKNOWN、超时、搜索/内存/排队上限各自明确，只有统一验证通过的见证可以提交。
- 多配方循环没有通用并行流水线优化。已承诺的物理批次不能由重规划撤销或重发。
- 主线程快照每 tick 共用 2 ms 工作额度；单个外部谓词调用不能被安全抢占，其耗时仍由所属模组决定。
- 非消耗工具和电路属于机器槽位；保留原提供者语义，不模拟 GT 内部超频、催化剂或机械并行。
- 概率产出、动态 NBT、损坏工具和第三方自定义样板需要具体提供者验证；没有将概率期望当成确定输出的求解路径。
- 真实机器组合和界面验证范围见兼容矩阵；没有对未安装附属或未来版本承诺全面兼容。
