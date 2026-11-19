# 项目协作说明

## 项目与源码

- 本项目是一个独立运行的 Fabric 模组（不依赖 Carpet），用于在游戏内标定珍珠炮并反解装药方案；标定可使用命名末影珍珠或雪球。
- 架构按端拆分：**服务端**负责标定、配置与计算（`/cannon ...`），**客户端**也可以拉取配置到本地计算（`/cannonClient ...`）并保存自己的配置副本；客户端还可以用 `/cannonClient pull [名称]` 从服务端拉取配置，用 `/cannonClient hud <序号|off>` 把配置编码持续显示在动作栏。
- 服务端 `/cannon calc normal|plane ...` 直接读取服务端配置计算，`/cannon calc expand|hud` 查看结果和显示配置编码；`/cannon tracker` 追踪每个玩家最后投出的珍珠/雪球，提供 HUD 与轨迹（全版本；server-side kit 通过 JiJ 内嵌在 CannonTool jar 里）。`/cannon config` 无参数等同 `/cannon config info`，没有 `config start` 时显示简单教程。
- 服务端计算器 `calc/ServerPearlCalc` 与客户端共用 `calc/CalcOutput` 的表头、结果行、expand、HUD 和诊断输出；结果按玩家 UUID 保存，`[展开]` 指向 `/cannon calc expand`，`[HUD]` 指向 `/cannon calc hud`。
- 计算源点不写进配置：服务端用执行命令的玩家当前坐标，客户端用本地玩家当前坐标；`PearlCannonSolver.solve/solvePlane` 显式接收 origin，配置里的 `reference.pos` 仍是相对投掷者的偏移。标定只按第一颗投掷物结束后点击选中的基准 tick 记录。
- Minecraft 及各 Mod 的源码在 `F:\source`。确认原版行为、方法签名、Mixin 注入点时，优先查阅与目标版本匹配的源码。
- 代码在 `src/main/java/com/rof/pearlcannon/`：`config/` 为配置数据与两端存储，`solver/` 为求解器，`calibration/` 为珍珠/雪球标定，`calc/` 为客户端/服务端共用的结果展示与服务端计算器，`command/` 为服务端命令，`client/` 为客户端入口/命令/计算/HUD/本地存储，`net/` 为配置同步 payload，`util/` 为工具，`mixin/` 为注入。
- 通用入口为 `PearlCannon`（`main`），客户端入口为 `client.PearlCannonClient`（`client`）；服务端命令通过 Fabric API 的 `CommandRegistrationCallback` 注册，客户端命令通过 `ClientCommandRegistrationCallback` 注册，服务器生命周期用 `ServerLifecycleEvents`，投掷物清理用 `ServerTickEvents`。
- 存储位置：服务端 `<world>/pearlcannon/<名称>.json`；服务端 tracker 设置 `<world>/pearlcannon/trackers/<uuid>.json`；客户端 `.minecraft/config/pearlcannon/<名称>.json`。
- 服务端与客户端命令根互不重叠（`/cannon` 与 `/cannonClient`），客户端不需要转发服务端命令；服务端仍按 OP 权限校验。
- 投掷物标定：`ThrowableProjectileCalibrationMixin` 统一在 `ThrowableProjectile.tick` 采样，`ProjectileHitCalibrationMixin` 同时注入 `ThrownEnderpearl` 和 `Snowball` 的 `onHit`；已移除的投掷物由 `PearlCalibration.onServerTick` 兜底结束。
- 帮助只显示完整命令，说明放在命令的悬停提示里（`Messages` 的 `^` 段），不做单行表格对齐。
- 服务端在玩家加入和配置增删时通过 `ConfigNamesPayload` 同步配置名，客户端缓存到 `ServerCannonNames`，供 `/cannonClient pull <名称>` 的补全使用。
- `/cannonClient push <名称>` 读取客户端本地 json，经 `PushConfigPayload` 发到服务端；服务端校验 OP 后写入服务端配置，并通过 `PushResultPayload` 回执。
- 追踪：`trail/ServerProjectileTracker` 每个玩家 UUID 对应一个 `Tracker`，其中记录当前追踪的单个投掷物 UUID、最后投出 UUID、模式（OFF/ONCE/ALWAYS）、HUD/连线/方块标记开关。每 tick 扫描 `ThrowableItemProjectile`；ONCE 在下一颗投出时关闭，ALWAYS 切换到新 UUID；UUID 找不到时只隐藏 HUD、保留轨迹并继续重试。HUD 通过 `ServerPlayer.sendSystemMessage(..., true)` 的动作栏显示 `Pos:[x,y,z]`（4 位小数，Pos: 白色、坐标蓝色）。路径记录原始轨迹：首次发现时用 `oldPosition()` 补上第一 tick；连线渲染只取主人 256 格内可见部分，动态切 16 格小段分配给每维度最多 256 个可复用 `VirtualDisplay` 槽位。线段和方块 display 都用实体 yaw/pitch 指向前进方向（kit 新增 `VirtualDisplay.yawPitch`，add packet 发送实体 yaw/pitch）：线段局部 +Z 对齐 `start → end`，方块按 `segment.start → segment.end` 朝向；方块大小/中心按投掷物碰撞箱计算。珍珠消失后进入 frozen 状态：不再追加点，但每 tick 仍无条件 `trail.update`，继续同步可见范围内的 display。颜色取投掷物 UUID 的 `hashCode()` 色相。`/cannon tracker trail` 与 `/cannon tracker box` 是两个独立开关。每玩家设置由 `trail/TrackerSettings` 持久化到 `<world>/pearlcannon/trackers/<uuid>.json`，默认连线开、方块关、HUD 开、模式 off；`on` 存为 always，`once` 是一次性动作存为 off。玩家加入时若持久化模式为 always 会自动创建 tracker 恢复追踪。刷新同时挂在 `END_SERVER_TICK` 和（26.x）`END_LEVEL_TICK` 上，按 `tickCount` 去重；tick freeze 时 level tick 仍会跑，因此 HUD/轨迹继续更新。全版本编译；kit runtime jar 由 `build.gradle` 通过 JiJ 嵌入最终 CannonTool jar（1.21.x 编译用 `-dev.jar`，26.x 用普通 jar；1.21.8 用 1.21.6，26.3 用 26.2），已把发送范围和 display view range 调到 256 格。

## 编码风格

- Java 以 4 个空格缩进。类声明的左花括号另起一行；方法与控制语句沿用相邻文件的排法。
- 类名大驼峰，方法/字段/局部变量小驼峰，常量大写下划线。名称直接表达数据或操作。
- 方法保持直接紧凑，使用普通循环、条件判断和提前返回。
- 集合常在字段声明处初始化；引用无需替换时使用 `final`。
- **只添加极为必要的注释**：仅解释代码本身无法清楚表达的约束、非直观原因或兼容性要求。
- 保留 Stonecutter 的 `//?` 条件标记；版本差异沿用现有条件分支方式处理。
- `src/main/java` 是活动版本 `26.1` 的源码，条件分支必须写成 26.1 生效的形态；其它版本由 Stonecutter 生成。

## 多版本

- Stonecutter 版本列表在 `settings.gradle`：`1.21.5`、`1.21.6`、`1.21.8`、`1.21.9`、`1.21.11`、`26.1`、`26.2`、`26.3`，活动版本 `26.1`。
- 各版本依赖在 `versions/<版本>/gradle.properties`。
- 渲染 kit 依赖按版本从 `libs/` 选择：1.21.5/1.21.6/1.21.9/1.21.11/26.1/26.2 各一份；1.21.8 复用 1.21.6，26.3 复用 26.2；1.21.x 编译用 `-dev.jar`，26.x 用普通 jar。runtime jar 通过 Gradle `include`（JiJ）嵌入最终 CannonTool jar，发布时只发 CannonTool，不需要单独安装 kit（详见 `libs/README.md`）。
- 已实现的兼容分支：
  - `1.21.11` 起命令权限改用 `PermissionSet`（`util/CommandAccess.java`）
  - `1.21.11` 前后 `ThrownEnderpearl` / `ThrowableItemProjectile` / `Snowball` 的包名不同，由 `build.gradle` 的 Stonecutter 全局替换处理
  - `1.21.11` 起 `ResourceKey` 的 `location()` 改为 `identifier()`，`ResourceLocation` 同时改名为 `Identifier`
  - 网络 payload 注册：`1.21.x` 用 `PayloadTypeRegistry.playC2S/playS2C`，`26.1+` 用 `serverboundPlay/clientboundPlay`（`net/PearlCannonPayloads.java`）
  - 客户端聊天输出：`Minecraft.getChatListener()`（`26.1` 及更早）与 `gui.chatListener()`（`26.2+`）（`client/ClientMessages.java`）
  - 动作栏 HUD：`Gui.setOverlayMessage`（`26.1` 及更早）与 `gui.hud.setOverlayMessage`（`26.2+`）（`client/PearlCannonClient.java`）

## 构建

- 项目根目录使用仓库自带的 Gradle Wrapper（Gradle 9.4.0），Fabric Loom 1.15.5。
- 26.x 需要 JDK 25，1.21.x 需要 JDK 21。
- 常用命令：

```powershell
.\gradlew.bat :26.1:compileJava
.\gradlew.bat :1.21.5:compileJava
.\gradlew.bat :26.1:build -x test
```

- 产物在 `versions/<版本>/build/libs/`。
- 编译失败时首次失败后最多再重试两次；达到上限停止并说明原因。

## 验证范围

- 用户没有要求时，不需要额外测试（单元测试、GameTest、开服功能测试等）。
- 修改 Mixin 后可以用 `.\gradlew.bat :26.1:runServer` 启动一次服务器，确认模组加载与注入没有报错；运行目录为根目录的 `run/`。
