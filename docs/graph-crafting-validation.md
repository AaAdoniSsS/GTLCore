# Graph 验证与测量记录

2026-09-23；基线 `13a7328e1da18d04df41dceeef3ec4f3efee79b6`，分支 `feat/ae-cycle-support`。
这是一份实现与测试记录，不是 T01–T64 全部通过的声明。
逐项状态见 [T01–T58](graph-consolidated-review.md) 和 [T59–T64 / 附属](graph-addon-compatibility.md)。

## 构建与自动回归

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test-graph.ps1
```

使用真实 AE/Minecraft/Forge 编译依赖：

```text
gradlew -I tools/graph-tests.init.gradle graphCoreTest graphIntegrationTest checkMixinPackageClasses spotlessCheck build
```

最新完整输出 `.local/validation-stress-delivery.log`：构建成功，9,343 项核心断言、166 项运行时断言，
调度时钟/公平续算/取消/并行 SCC 等价性通过；真实 AEKey/NBT、派发适配、能量、流体单位、
输入槽、拒绝/同步回料、Ring 分页与精确数量测试通过；Mixin 包检查、格式和重映射打包通过。
保留基线已有 3 项注入处理器警告，没有新增 graph 注入警告。

核心测试含独立短序列解释器及小状态空间 BFS oracle；不是用待测求解器的答案作为唯一正确性依据。
Ring 测试包含 15,000 层拓扑、>2^53 和 long 边界、共享 DAG、自环、压缩 Repeat、
跨多工作片视图生成、有限内存拒绝以及真实 AE 编解码。

`graphIntegrationTest` 没有执行完整 Forge lifecycle，不能替代下述真实服务端测试。
本机的 build-local.ps1、映射恢复、Gradle/Maven 缓存均在项目目录树；本地 build.gradle 调整不属于补丁。

## 实际 Forge 服务器

项目 `.local/graph-server` 中的独立平坦测试世界，Forge 47.3.7、AE 15.4.10、GTCEu 1.4.4、
ExtendedAE 1.4.19、MAE2 1.6.1、Requester 1.1.5、wildcard 0.1.2-gtl、AE2CT 1.1.1 等，
只监听 127.0.0.1:25575。已获得服务端/EULA 授权，没有改动实际游戏存档。

已完成：普通 CPU 与成型超限阵列交付、Requester 在途保存后继续及取消、
通配符内部槽位实际投料及更新退款、MAE2 P2P 远端实际投料、未承诺后缀重规划、AE2CT/Ring 摘要包往返。
日志与时间索引见附属矩阵。

超限阵列三步循环：C+X→I、I+Y→J、J+Z→P+C，目标 P=3，初始 C=1、X/Y/Z 各 3。
Requester 发单，在 J 在途时保存并停止服务器；重启 UUID 与 J=1 保留。
最终恰好 P=3、C=1，燃料/中间体为零，旧核心三个计数全为 0。
重启前已派发 2 次，重启后 7 次，共 9 次，没有重复前缀。
箱子作为真实输入缓冲，测试命令模拟加工回料；不将其描述为真实 GT 机器加工。

探针可在 [tools/graph-server-probe](../tools/graph-server-probe/README.md) 复现；
测试模组、脚本、世界和日志都不打入 Core JAR。

P03 首次交付包 SHA-256：`fd80078010b3126ce23ea41a95198606930ded63887d7588c7f948e252121f97`。
该哈希对应实际验收服务器唯一安装的 Core；JAR 内容检查确认含 Ring、节点费用类与界面资源，且不含测试探针。
AE2CT 缺席启动也已通过，日志 `server-p03-no-ae2ct.log`。

随后确认页与重复提交修订的调查、构建及实测边界见 [CPU 重组调查](graph-reuse-investigation.md)。
上述首次交付包的测试记录保留原有哈希，不混作随后新包的测试。
大规模新旧对照、后续优化及最终测试包见 [深链与共享分支压测](graph-stress-performance.md)。

## 性能口径

机器：Windows x64、AMD Ryzen 9 7945HX、Corretto 20.0.2.1，Java 17 目标字节码。
实际 A/B 在同一 JVM、同一网络、库存、样板和配置中只规划，不消耗物料。
基线直接运行同一 sky 代码的 MAX_FAST；graph 仍走公共请求入口。
两边从主线程发起，分别计基线构造/求解、graph 全流程/纯求解，逐次核对投入、缺料、目标、配方次数和 bytes。
这是规划测量，不能代替真实机器生产时间或进入世界耗时。

旧 `.local/server-p03-routing-v3.log` 的初步 10 次 A/B 不能作为最终加速数据：
当时 graph 从后台回到主线程，包含约一 tick 的额外等待；同时发现少收 16 bytes 的逻辑节点费用。
当时补了节点计费与小快照直接推进；后续大规模共享 DAG 测试发现节点计费重复展开，
已改为请求模板共享及终端计数，详见上述压测记录，不能把最初简单测试视为所有大图的保证。
Forge tick 起点统一刷新快照额度，避免 AE 在 tick 末尾递增计数导致同一轮重复获得额度。

简单规划不保证比 MAX_FAST 更快；冷/热、深链和混合边界要分别测量。
旧算法不能完成的循环只报告新算法的绝对耗时和正确性，不计算虚假的加速倍数。

最终包校验与服务器验收以 `server-p03-verified-before.log` / `server-p03-verified-after.log` 为准。
19:12 的检查发现隔离 mods 中曾残留旧 `gtlcore-...-graph-test.jar`，早期生产日志实际加载旧构建；
因此旧日志只保留其对应实现的回归证据，不作为最终构建性能验收。
现已移走残留包，探针启动记录实际 Core 类加载路径；构建脚本也检查唯一 Core 包和 SHA-256 一致性。

最终同服 A/B（诊断计时开启，每组 10 次，以下为后 9 次中位数 / 最近秩 p95，毫秒）：

| 请求 | MAX_FAST 构造 | MAX_FAST run | GRAPH 全流程 | GRAPH 求解区间 | 两边 bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| 2,000 木板，500 原木 | 0.057 / 0.060 | 0.255 / 0.338 | 0.811 / 1.206 | 0.190 / 0.388 | 3,016 |
| 32 层处理链，1,000 单位 | 0.056 / 0.144 | 0.865 / 0.956 | 1.817 / 3.076 | 0.747 / 1.293 | 65,264 |

两边每次投入、缺料、目标、配方次数和 bytes 均相等。基线构造与 run 分项记录，
不能把各列中位数之和冒充逐样本总耗时中位数。样本量小，不作为稳定尾延迟承诺。
这两组 graph 求解区间中位数较低，但包含快照、验证、生成和调度后的总耗时较高，**普通订单整体加速尚未通过**。
第一笔木板包含类加载：旧构造 60.716 ms/run 29.312 ms，graph 全流程 40.580 ms；
32 层新目录首笔旧 run 0.967 ms，graph 全流程 89.844 ms（求解 1.862 ms，包含分片快照等待）。
首笔数据不与热中位数混合，也不从这个单样本推导普遍冷启动加速。
原始日志和 `tools/summarize-graph-benchmark.py` 生成的 `.local/graph-ab-verified.json` 均保留。

## 计时日志

启用 `ae2GraphDiagnosticLogging`：

```text
[Graph Crafting] plan result=... amount=... snapshot_ms=... planner_ms=... queue_ms=... patterns=... nodes=... cache_hit=... bytes=...
[Graph Crafting] phases=Metrics[activeNanos=..., cpuNanos=..., peakActiveWorkers=..., maxWorkSliceNanos=..., peakReservedBytes=...] wall_ms=...
[Graph Crafting] job=... state=... dispatches=... rejects=... checks=... tick_calls=... tick_ms=...
```

`nodes` 是累计预算工作计数，不是最终图顶点数。`planner_ms` 是求解器记录区间，
分阶段 activeNanos 才用于判断构图/分析/求解/验证的具体工作量；多工作线程累计时间不等同于墙钟。
Windows 线程 CPU 时间可能有约 15.6 ms 量化粒度，短工作片的零值不表示没有工作。
`peakReservedBytes` 是预算模型的保留量，不是 JVM 实测堆峰值；未提供真实分配/GC 数据时不能混称。
`tick_ms` 是 CPU 控制器累计执行时间，排除了机器等待；重启后计时重新开始，派发账本则持久化。
`.local/server-p03-ring-replan.log` 是修正 CPU 复用计时清零之前的早期日志，不能跨订单比较其累计 tick_ms。

## 尚未完成的验收

客户端 Ring / AE2CT 实际打开和交互、无线终端、GT 混合流体回收与工具/虚拟供料作用域、
Core 中继整机、异步 GT 输出队列、网络分裂、完整多用户高负载性能仍未全部验证。
跨 CPU 闭合等待目前只有有证据的证明内核，生产责任收集尚未接入。
有限后备搜索不保证任意复杂循环都可求解或最优；资源受限/未知不会提交未验证方案。
