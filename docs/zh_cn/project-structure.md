# 项目结构

Java 源码位于 `src/main/java/com/rof/pearlcannon/`，按职责分包。服务端负责标定与配置存储（`/pearlCannonConfig`），客户端负责计算与本地配置（`/pearlCannonCalc`）。

```text
com/rof/pearlcannon/
├── PearlCannon.java                    # 通用入口：payload 注册、服务端命令、服务器生命周期
├── calibration/
│   └── PearlCalibration.java           # 珍珠/雪球标定（服务端）：命名解析、Δv 采样、基准 tick、清理
├── client/
│   ├── PearlCannonClient.java          # 客户端入口：本地命令、配置编码 HUD、接收服务端配置
│   ├── PearlCannonClientCommands.java  # /pearlCannonCalc normal|plane|expand|hud|pull|list
│   ├── PearlCannonCalc.java            # 客户端求解、结果展示与配置编码 HUD
│   ├── ClientCannonConfigs.java        # 客户端配置：config/pearlcannon/<名称>.json
│   ├── ServerCannonNames.java          # 服务端配置名缓存（pull 补全）
│   └── ClientMessages.java             # 客户端聊天输出
├── command/
│   └── PearlCannonConfigCommands.java  # 服务端命令树：/pearlCannonConfig ...
├── config/
│   ├── PearlCannonConfig.java          # 单个炮台的配置数据（Gson POJO）
│   ├── PearlCannonConfigs.java         # 服务端配置注册表：world/pearlcannon/<名称>.json
│   └── PearlCannonRom.java             # 二进制 ROM：三段顺序、权重表、子集和/编码
├── net/
│   ├── PearlCannonPayloads.java        # pull/push 请求、配置下发、结束标记、配置名同步
│   └── ServerConfigShare.java          # 服务端响应拉取请求
├── solver/
│   └── PearlCannonSolver.java          # 求解器：tick 模式与平面模式（客户端调用）
├── util/
│   ├── CommandAccess.java              # 版本相关的命令权限判断
│   ├── ConfigNames.java                # 配置名/文件名校验
│   ├── Format.java                     # 聊天数字格式
│   └── Messages.java                   # 分段消息工具（Carpet 风格样式码）
└── mixin/
    ├── ThrowableProjectileCalibrationMixin.java  # 投掷物 tick 边界采样（珍珠/雪球共用）
    └── ProjectileHitCalibrationMixin.java        # 标定投掷物命中清理（珍珠/雪球）
```

## 客户端与服务端

- 服务端：`PearlCannonConfigCommands` 注册 `/pearlCannonConfig ...`；配置写入 `<world>/pearlcannon/<名称>.json`
- 客户端：`PearlCannonClientCommands` 注册 `/pearlCannonCalc ...`（`normal`/`plane`/`expand`/`hud`/`pull`/`list`）；配置读取 `.minecraft/config/pearlcannon/<名称>.json`
- 两个命令根互不重叠，客户端不需要转发服务端命令；`/pearlCannonConfig` 直接由服务端处理并校验 OP
- 传输：客户端发 `pull_request`，服务端按需回 `config` 与 `pull_result`；不在同一台机器时也能同步
- `solver/` 与 `config/PearlCannonConfig.java`、`PearlCannonRom.java` 两侧共用，保证计算逻辑一致

## 多版本

使用 Stonecutter 管理 `1.21.5`、`1.21.6`、`1.21.8`、`1.21.9`、`1.21.11`、`26.1`、`26.2`、`26.3`，活动版本为 `26.1`。

- 各版本的 `loader_version`、`fabric_api_version`、`minecraft_ver` 放在 `versions/<版本>/gradle.properties`
- 版本差异用 `//?` 条件注释处理，例如 `CommandAccess` 里的权限判断（`>=1.21.11` 使用 `PermissionSet`）
- `ThrownEnderpearl` / `ThrowableItemProjectile` / `Snowball` 的包名在 `1.21.11` 前后不同，由 `build.gradle` 里的 Stonecutter 全局替换处理
- 网络 API：`PayloadTypeRegistry.playC2S/playS2C`（1.21.x）与 `serverboundPlay/clientboundPlay`（26.1+）由 `PearlCannonPayloads` 的条件注释切换
- `ResourceLocation` 在 `1.21.11` 改为 `Identifier`，同样由 `PearlCannonPayloads` 的导入条件处理
- 客户端聊天输出：`Minecraft.getChatListener()`（26.1 及更早）与 `gui.chatListener()`（26.2+）由 `ClientMessages` 的条件注释切换
- 配置编码 HUD 的动作栏：`Gui.setOverlayMessage`（26.1 及更早）与 `gui.hud.setOverlayMessage`（26.2+）由 `PearlCannonClient` 的条件注释切换
- `src/main/java` 始终是活动版本 `26.1` 状态；切换活动版本时 Stonecutter 会重写根源码里的条件分支

## 构建

```powershell
.\gradlew.bat :26.1:compileJava
.\gradlew.bat :1.21.5:compileJava
```

26.x 需要 JDK 25，1.21.x 需要 JDK 21。
