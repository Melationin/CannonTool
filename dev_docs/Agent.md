# 项目协作说明

## 项目与源码

- 本项目是一个独立运行的 Fabric 模组（不依赖 Carpet），用于在游戏内标定珍珠炮并反解装药方案；标定可使用命名末影珍珠或雪球。
- 架构按端拆分：**服务端**负责标定与配置（`/cannon ...`），**客户端**负责计算（`/cannonClient ...`）并保存自己的配置副本；客户端还可以用 `/cannonClient pull [名称]` 从服务端拉取配置，用 `/cannonClient hud <序号|off>` 把配置编码持续显示在动作栏。
- 服务端 `/cannon calc normal|plane ...` 直接读取服务端配置计算；`/cannon trail <名称|off>` 用 Ryan's Server Rendering Kit 在服务端画珍珠轨迹（仅 26.1）。
- Minecraft 及各 Mod 的源码在 `F:\source`。确认原版行为、方法签名、Mixin 注入点时，优先查阅与目标版本匹配的源码。
- 代码在 `src/main/java/com/rof/pearlcannon/`：`config/` 为配置数据与两端存储，`solver/` 为求解器，`calibration/` 为珍珠/雪球标定，`command/` 为服务端命令，`client/` 为客户端入口/命令/计算/HUD/本地存储，`net/` 为配置同步 payload，`util/` 为工具，`mixin/` 为注入。
- 通用入口为 `PearlCannon`（`main`），客户端入口为 `client.PearlCannonClient`（`client`）；服务端命令通过 Fabric API 的 `CommandRegistrationCallback` 注册，客户端命令通过 `ClientCommandRegistrationCallback` 注册，服务器生命周期用 `ServerLifecycleEvents`，投掷物清理用 `ServerTickEvents`。
- 存储位置：服务端 `<world>/pearlcannon/<名称>.json`；客户端 `.minecraft/config/pearlcannon/<名称>.json`。
- 服务端与客户端命令根互不重叠（`/cannon` 与 `/cannonClient`），客户端不需要转发服务端命令；服务端仍按 OP 权限校验。
- 投掷物标定：`ThrowableProjectileCalibrationMixin` 统一在 `ThrowableProjectile.tick` 采样，`ProjectileHitCalibrationMixin` 同时注入 `ThrownEnderpearl` 和 `Snowball` 的 `onHit`；已移除的投掷物由 `PearlCalibration.onServerTick` 兜底结束。
- 帮助只显示完整命令，说明放在命令的悬停提示里（`Messages` 的 `^` 段），不做单行表格对齐。
- 服务端在玩家加入和配置增删时通过 `ConfigNamesPayload` 同步配置名，客户端缓存到 `ServerCannonNames`，供 `/cannonClient pull <名称>` 的补全使用。
- `/cannonClient push <名称>` 读取客户端本地 json，经 `PushConfigPayload` 发到服务端；服务端校验 OP 后写入服务端配置，并通过 `PushResultPayload` 回执。
- 轨迹显示：`trail/ServerPearlTrail` 在服务端按名称过滤 `ThrownEnderpearl`，用 Ryan's Server Rendering Kit 的 `StripLineShape` 和虚拟 display 画线；仅 26.1 编译，库 jar 在 `libs/`，其它版本是空实现。

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
