# 开发与测试

适用范围：Linux 下的构建、JVM 回归及隔离 Forge 测试。命令由仓库根目录的 `dev` 脚本提供；通配符行为说明见 [专项测试](testing/wildcard.md)。

## 环境准备

需要 JDK 17、Bash、GNU coreutils、Python 3.11+。Gradle 使用仓库的 Wrapper。

复制 `.dev.env.example` 为 `.dev.env`，填写本机路径。`dev` 将它作为 Bash 配置读取并导出变量；该文件被忽略，不进入源码提交。也可以直接设置环境变量。

| 配置 | 用途 |
| --- | --- |
| `GTL_JAVA_HOME` | JDK 17 目录；未指定时使用 `JAVA_HOME` |
| `GTL_GRADLE_USER_HOME` | Gradle 缓存；默认 `.gradle/user-home` |
| `GTL_OFFLINE` | 默认 `1`；首次准备依赖或更新依赖时设为 `0` |
| `GTL_TIMEOUT` | Gradle 超时秒数；默认 `1200` |
| `GTL_MODS_DIR` | 隔离服务端所需补充模组的来源目录 |
| `GTL_ACCEPT_EULA` | 阅读并接受 Minecraft EULA 后，设为 `true` 以启动测试服务端 |

```bash
./dev doctor
GTL_OFFLINE=0 ./dev build
```

模板默认允许联网。依赖齐备后可以将本机配置中的 `GTL_OFFLINE` 改为 `1`。模板使用默认值赋值，命令行传入的环境变量优先。

## 执行入口

| 命令 | 内容 |
| --- | --- |
| `./dev doctor` | 检查 JDK、Gradle、源码及工作区状态 |
| `./dev test` | 执行布局和 AE 的 4 个既有 `main()` 回归入口 |
| `./dev check` | Gradle `check`、上述 4 个回归、标签表达式回归、Spotless 和 Mixin 包检查 |
| `./dev build` | 完整检查并打包，版本附加 `-local.<HEAD短哈希>` |
| `./dev gradle <任务或选项>` | 使用同一环境执行 Gradle 任务 |
| `./dev smoke-server` | 构建并启动隔离服务端，检查正常启动、停服及存档 |
| `./dev test wildcard` | 执行 10 项真实控件、保存、NBT 和样板展开回归 |
| `./dev test wildcard --without-wildcard` | 验证没有可选 Wildcard Pattern 依赖时的启动和停服 |

`dev` 加载 [初始化脚本](../scripts/dev.init.gradle)，将 4 个既有 JVM 回归接入 `check`。直接调用 `gradlew` 不会自动加载该脚本。通配符 Forge 回归独立运行，不包含在 `check` 中。

## 隔离服务端依赖

[运行清单](../scripts/dev-runtime.json) 固定了补充依赖的文件名和 SHA-256。准备包含这些 JAR 的目录并设置 `GTL_MODS_DIR`；脚本将缺少的文件复制到 `.gradle/dev-mods/`，每次运行都会验证缓存的校验值。已有完整缓存时无需读取来源目录。

运行器使用 Forge `47.4.16`，首次运行会从官方 Maven 下载并校验安装器。补充运行依赖包括 LDLib `1.0.33.b`、AE2WTLib `15.3.3`、MAE2 及其依赖；仓库 `libs/` 提供 Wildcard Pattern `0.1.2-gtl` 和 Re-Avaritia。构建声明的依赖保持原版本，运行器替换所需版本并排除部分物理客户端模组。

启动测试服务端前需要明确设置 `GTL_ACCEPT_EULA=true`。每次运行创建独立世界，仅监听 `127.0.0.1` 临时端口，不使用已有游戏世界。启动或专项测试超时为 10 分钟，基础 smoke 的正常停服超时为 90 秒。

## 输出与验证边界

Gradle 日志位于 `build/dev/logs/`；测试模组位于 `build/dev/testmod/`；服务端实例及报告位于 `run/dev-smoke-*` 或 `run/dev-wildcard-*`。这些目录均被忽略。保留需要交接的日志和产物后再清理。

正式 GTLCore JAR 不包含测试模组。服务端回归不覆盖客户端鼠标事件、双端网络同步或完整整合包加工链。隔离实例未复制整合包全部脚本和配置，相关缺失日志应结合被测行为判断；实际客户端验收步骤见 [专项测试](testing/wildcard.md)。

未提交修改不会体现在 HEAD 哈希中。验收时应同时记录源码差异和 JAR 的 SHA-256，确保部署的是已测试产物。
