# P03 附属兼容与 Crafting Ring

2026-09-23；基线 `13a7328e1da18d04df41dceeef3ec4f3efee79b6`，分支 `feat/ae-cycle-support`。
本文记录实际证据，未执行的组合不标为通过。T01–T58 见 [修订验收登记](graph-consolidated-review.md)。

按用户后续决定，AE2 Crafting Tree 只保留强转修复和有限循环展开；复杂图由 Core 的 **Crafting Ring** 显示。
旧树不是多来源、多产物的完整核算页面。没有为了显示树而调用旧求解器。

## 实际组合与注入边界

`tools/audit-graph-addons.py` 审计实际启用的 75 个 JAR，输出文件名、SHA-256、Mixin 配置和合成边界引用；
原始结果在 `.local/addon-audit-pack.json`。它不代替行为测试，条件加载与嵌套 JAR 也不能仅靠字符串扫描证明安全。
P03 阶段的隔离服务器为 34 个外层 JAR（含测试探针），另有 Forge 加载的嵌套依赖；
清单及哈希在 `.local/addon-audit-server-final.json`。这是核心附属组合测试，不是 75 个客户端模组全部装入服务端。

| 模组 / 功能 | 版本 | 接入与保留行为 | 状态、证据与边界 |
| --- | --- | --- | --- |
| AE2 普通 CPU | 15.4.10 | `CraftingService.beginCraftingCalculation` 统一受理；CPU 按计划/存档 engineKind 路由 | 已适配并验证：分子装配室完成 16 木板；Requester 在途保存继续及取消；旧核心计数为零 |
| Core 超限阵列 | 当前 sky 基线 | `TransfiniteCraftingLogic` 委派同一图控制器，保留容量 CPU 和外部接口 | 已适配并验证：实际成型阵列、ExtendedAE 提供者、普通订单；三步回收环的在途恢复单独记录在下文 |
| AE2 Crafting Tree | 1.1.1 | `AE2CraftingPlanSummary.fromJob` 的具体 CraftingPlan 强转只接收 UI 兼容视图；任务仍持 AeGraphPlan | 已适配并验证服务端摘要包、自环/双环/共享 DAG、两种布局；客户端最终绘制待测 |
| Crafting Ring | 本次 Core | 确认菜单的图计划 UUID、原生只读视图与独立子页面 | 分页、精确数量、压缩步骤、图拓扑和缓存测试通过；实际客户端交互待测 |
| AE2 Wireless Terminals | 15.3.3-forge | 公共 AE 菜单/请求入口；未发现其配置 Mixin 进入旧执行核心 | 待验证：服务端组合加载通过，手持无线终端发单与取消尚未实际操作 |
| ExtendedAE | 1.4.19-forge | 保留 Core 的方块/部件提供者容量修改、样板实现与自身路由 | 已适配并验证方块提供者的实际加工投料、超限订单；其合成矩阵及部件提供者整机仍待测 |
| MAE2 | 1.6.1 | CPU 注入位于 `addBlockEntity`，线程容量行为保留；派发经过实际覆盖后的 PatternProviderLogic 与 1.x P2P 能力适配 | 已适配并验证单输出 P2P 远端箱子收到 3 石头并回料完成；多输出隧道拓扑变化待测 |
| ME Requester | 1.1.5 | RequestState 调公共异步规划，PlanState 提交，LinkState 读完成/取消；Core long batch 修改保留 | 已适配并验证自动发单、完成、在途重启后继续/取消及 link 身份；超 long 实体存储量未做游戏灌入测试 |
| wildcard_pattern | 0.1.2-gtl | 按有效 IPatternDetails 展开，Core 仓按 `patternToSlotMap` 派发；预检调用同一内部槽映射 | 已适配并验证 258 种展开；真实内部槽 257 投料 3 次，其他槽为空；取消改样板退款准确且新输出量注册生效 |
| GTCEu ME 样板仓 / Core 中继 / 分子 IO 工具 | GTCEu 1.4.4 / 当前 Core | 公共提供者协议、真实槽位和工具能力预检；未绕过提供者直塞机器 | 源码接入及 AE 类型适配测试通过；完整 GT 机器、虚拟供应作用域及中继连线待验证 |
| GTL Additions | 3.2.8Custom_SubSpace-fix2，Java 21 | 有效样板、原提供者、真实回料接口 | 后续实际加载：多装配机循环、在途重载、FOA 容器/倍率、旧计划拒绝通过；无限分子装配机与另一上游分支待测，见下文 |

上述“已适配并验证”仅覆盖同一行列明的用例，不代表该模组所有功能或未来版本。
没有降低必需 Mixin 注入数量；测试探针反而将三个旧核心注入设为 required/defaultRequire=1。

## 实际服务端证据

隔离世界、模组和日志都在项目 `.local`，未使用用户实际存档。箱子加工用例由测试命令模拟回料，
验证的是实际 CPU、Requester、提供者、在途与存储协议，不能描述成真实 GT 配方加工。

| 日志 | 事件与断言 |
| --- | --- |
| `server-p03-ring-replan.log` 18:05–18:11 | Ring 与 AE2CT 包往返；普通 CPU / 分子装配室交付；第一步已投料后替换未承诺后缀，最终 1 金锭、共 3 次投料；Requester 完成后 0 link |
| `server-p03-lifecycle-before.log` / `after.log` | link `9e0eb804-a302-4b48-9445-3fd6b41e42e5`，铁锭在途保存；重启保持 UUID / waiting=1，返回后完成，重启后的追加派发为 0 |
| `server-p03-routing.log` / `routing-final.log` | link `76400de9-7c95-4a2b-8c21-0d353a568f14`，在途恢复后取消；晚到铁锭留在网络，CPU 空闲、Requester 无 link，没有伪报取消订单完成 |
| `server-p03-routing-v3.log` 18:42 | 通配仓内部槽 257 实际接收 3 轮输入；修改样板后旧物退款、新注册输出量为 2；实际成型的超限阵列交付 16 木板 |
| `server-p03-routing-v3.log` 18:42–18:43 | MAE2 P2P 远端箱子收到 3 石头，回料后完成；显式 A/B 之前旧核心三个计数均为 0 |
| `server-p03-cycle-before.log` / `cycle-after.log` | Requester → 超限阵列 → ExtendedAE 三步催化剂回收；link `98d46ce0-ef03-4ceb-9873-86794fbc9c00` 在第二段金锭在途时保存，重启 UUID 及 waiting=1 保留 |
| `server-p03-verified-before.log` / `verified-after.log` | 移走旧包后，核对类加载路径和最终 JAR 哈希；Ring/AE2CT/通配规划重新通过；link `86ead7eb-4ed2-42e3-b8cd-31d927849481` 重跑三步回收、重启继续、最终 P=3/C=1，旧核心三个计数均为 0 |

早期日志加载过残留旧测试包，不能当作最终包的所有改动已验证；最终构建的重跑证据以上表最后一行为准。
新的节点费用、主线程即时快照与 Ring 分片实现均包含在最终包及自动回归中。

`tools/graph-server-probe` 提供可复现探针与脚本，探针 JAR 不进入 Core 发布包。
探针的明确旧算法 A/B 会增加旧核心计数，必须与生产链路的零调用验收分开。

测试中修正过三类夹具问题：Rhino 循环内 const 绑定复用、P2P 输入侧网络未供电、
回收样板误把催化剂列为主输出。AE 15.4.10 仅注册主输出为可请求物，Requester 的目标必须符合这一注册规则。
`routing-final.log` 内的探针 AssertionError 属于错误夹具导致的测试服务器退出，不能当成产品崩溃证据。

## Ring 的显示与性能边界

- 从合成确认页面左侧工具栏打开 **Crafting Ring**，返回原确认菜单；查看不会提交、提料或修改任务。
- 资源、循环、配方、压缩步骤四页；共享资源使用稳定身份；循环用显式 SCC 区分，不把共享 DAG 当环。
- 每张配方只记录一个执行次数，多来源和多产物保留；启动物料、保种量、缺料量分开显示。
- 物料以 long 传输，显示总量乘法使用 BigInteger；缩写可悬停看准确数量；Repeat 不按生产次数展开。
- 服务端每计划缓存一次只读视图，通过独立有界规划池分片生成；分页每次最多 32 行，总量和内存有限制。
- 请求校验当前玩家、菜单及 UUID；后台完成后重新校验；错误页明确返回，不让界面永久等待失效计划。
- 客户端后台建立拓扑及布局，SCC 展开成可见有向环，其余依赖按凝聚 DAG 分层；物料和配方节点分开。每帧通过空间索引查询可见节点与边，支持拖动、缩放、聚焦循环及 Shift 点击配方。
- 当前是**确认计划视图**。运行中 CPU 的状态来自新账本，但 Ring 尚未提供运行中动态图页面。

## T59–T64 验收登记

| ID | 已执行部分 | 尚未完成部分 |
| --- | --- | --- |
| T59 | 实际目标服务端组合启动、必需 Mixin 应用、资源/打包检查；移除 AE2CT 后同一世界启动通过 | 实际客户端启动/操作待测；未声明任意可选模组移除组合全覆盖 |
| T60 | Requester 发单、实际交付、在途恢复、取消、晚到物归属；真实 AE 接收量适配测试 | 手持无线终端操作及请求者部分接收的真实整机用例 |
| T61 | AE2CT 强转与有限展开；MAE2 原注入边界；普通/超限实际 graph 订单旧核心零调用 | 完整虚拟能力、工具与全部拓扑组合 |
| T62 | 一物品槽展开 258 配方，内部槽 257 真投料，改样板退款与重新注册；通配回收环规划 | 完整 GT 加工机处理全部通配材料未穷举 |
| T63 | 精确数量 >2^53、15,000 层拓扑、压缩步骤、分页、异步工作片与内存上限、缓存视图 | 客户端连续操作与运行中动态图界面 |
| T64 | 普通 CPU 的 Requester 在途重载继续/取消；超限三步回收链重载身份与批次 | 跨区块外部机器保存时序、网络分裂重连的完整组合 |

不能把表中“尚未完成”改写成全部验收通过。

`server-p03-no-ae2ct.log` 19:18:55：移走 AE2CT 及依赖它的测试探针后服务器达到 Done，世界正常加载。
日志中有 @Pseudo 目标不存在的警告及测试世界旧 datapack 缺席提示，无 fatal Mixin/硬类加载失败。
测试结束后已恢复隔离目录中的附属与探针；没有修改用户游戏实例。

## 2026-09-23：ADD 两条源码分支复核

本次单独核对 AaAdoniSsS `72c3d9f061647c942fa7ecaa219f49d0861a6031` 与 Dragonators
`8caff5e93a5e65914d10dd176d48d66e7ec8c329`，原始源码保存在 `.local/addon-audit`。
用户 21:16 日志未加载 ADD。以下先记录两条公开源码的边界；用户后来添加的实际 JAR 已单独测试，
实际结果见下一节，不能把两个分支混为同一份整包验收。

| 边界 | 当前实现和证据 | 尚未验证的部分 |
| --- | --- | --- |
| FOA 有效样板 | 从 CraftingService 获取最终 IPatternDetails。指纹重新读取输入、输出、倍率和余物，不只按样板物品判断；捕获阶段按对象身份访问样板，再按完整规范化变体去重。实际 FOA 切换已通过，见下文 | 副产物单独下单 |
| 倍率/模式切换 | ADD 调用 Core `refreshAllByProduct()`，它重建样板并设置 `needPatternSync`；提供者代际变化触发图检查。即使遗漏通知，提交/派发仍核对有效样板，旧绑定拒绝后失效 | ADD 各版本全部开关组合 |
| 库存缓存 | Graph 未调用 ADD GridStockCache 作为取料凭证；提交用实际 MODULATE 提取，短取回退只归还实际取得的材料。催化剂在 CPU 自有库存中保管 | 带 ADD 的机器竞争及 5/20 tick 缓存失效整机测试 |
| 真实库存/供应作用域 | 不把图的预计产物或预留直接塞进样板总成槽位；继续调用已有提供者，保留槽位、电路和不消耗供应语义 | ADD SlotCache 的全部特殊供应组合 |
| 产物结算 | 提供者接受、机器领取待处理条目都不算订单完成；只有真实回料减少 expected。适配回归已覆盖 30 亿 mB 派发、10 亿 mB 部分返回及余量 | 特殊分子装配机普通/无限模式、运行中取消和重载 |
| 数量/身份 | 使用 AEKey 保留 NBT，数量使用 long 和受检运算；有效输出发生变化时旧配方拒绝。真实 AE API 测试覆盖大数量/NBT | ADD LongFluidStack 的 JEI 导入界面链路 |

Dragonators 的 [FOA 改写](https://github.com/Dragonators/GTLAdditions/blob/8caff5e93a5e65914d10dd176d48d66e7ec8c329/src/main/java/com/gtladd/gtladditions/integration/ae2/MEBufferPatternHelperExtensions.kt)
对容器和普通产物采用不同规则；因此图不会把原样板简单统一乘倍数。
[AaAdoniSsS 库存缓存](https://github.com/AaAdoniSsS/GTLAdditions/blob/72c3d9f061647c942fa7ecaa219f49d0861a6031/src/main/java/com/gtladd/gtladditions/api/ae2/GridStockCache.java)
继续留在其显示/机器账本路径，新引擎不依赖它完成实际转移。

仍有独立的机器侧风险：[Dragonators MECraftHandlerMixin](https://github.com/Dragonators/GTLAdditions/blob/8caff5e93a5e65914d10dd176d48d66e7ec8c329/src/main/java/com/gtladd/gtladditions/mixin/gtlcore/machine/trait/MECraftHandlerMixin.java)
在 `Long.MIN_VALUE` 模式使用未经检查的 `multiply * amount`，并用 `Ingredient.of(item)` 重建产物、丢失 NBT。
当前 Core 普通对应路径也有同类写法。图引擎不会将错误身份的返回算作正确交付，但不能替机器凭空补回正确产物；
这两条机器结算路径仍需各自修复和整机验证，不能宣称单靠图适配已解决。

## 实际 ADD JAR 的后续验证

用户实际包 `gtladditions-3.2.8Custom_SubSpace-fix2.jar`，SHA-256
`c859ac4953cbc23c3acb3819b78c7d86123da479522b2548e51dbb7d5d22edd8`，包含 major 65 字节码。
Java 21.0.3、SGJourney 0.6.44，最终 Core `b9ed101c…`；全部目录、资源与存档仍在本项目。

- 实际加载 ADD 后，普通 AE CPU / 单供应器 / 多装配机完成模板增长环。初始 1 模板、目标 100，
  配置补造上限 10 且 CPU 需要 4 份时，最后 104 模板，两项守恒为 2707 / 2001。
- `server-ring-add-foa.log`：实际 `gtladditions:me_super_pattern_buffer` 的 FOA 从 4 倍切到 8 倍。
  8 产物的计划投入从 2 红石变为 1 红石；旧 4 倍计划提交被 `PATTERN_FINGERPRINT_CHANGED` 拒绝，
  库存仍 100，CPU 未开始任务，新计划重新成功。未把原样板物品直接当成有效配方。
- `server-ring-add-resume.log`：调用实际 ADD FOA 实现，容器
  `kubejs:extremely_durable_plasma_cell` 按 4 入 / 1 出处理；带 NBT 的纸产物身份保留，
  单次输入 3,000,000,000 未截断，8 产物只需 2 次有效配方，完整图验证通过。
  隔离服仅复制整包原始的两条容器 `event.create` 注册，不声称加载整包全部脚本。
- 同一最终包保存时 4 批次在途、expected=36 钻石。重启继续 link
  `ca1ac26a-0d96-4002-a815-afc2dea570c2`，最终正确交付，未重复投料或丢失催化剂。

初次容器探针遇到未注册 KubeJS ID 的 barrier 替代物，**该样本不作为容器通过证据**；
最终已补齐真实物品 ID，并让探针拒绝 barrier。最初新测试台缺少数据连线，库存为零；
补齐线缆和总成朝向后才执行有效提供者测试。这些夹具问题不记为新引擎故障。

实际 JAR 的字节码复核仍发现上述无限 `MECraftHandlerMixin` 的 `Ingredient.of(item)` 和未受检 `lmul`。
本次未验证/修复这条特殊机器生产路径；FOA 样板、图引擎回料与普通装配机通过不代表无限装配机的 NBT 安全。
AaAdoniSsS 3.3 的 GridStockCache/SlotCache 仅做源码审查，未将它冒充此次安装的 Dragonators 版本。
