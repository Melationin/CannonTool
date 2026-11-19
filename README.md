## CannonTool / 珍珠炮计算器

一个独立运行的 Fabric 模组：在游戏内标定珍珠炮各组阵列的推力，并按目标坐标反解装药方案、输出二进制码。

不依赖 Carpet。服务端配置器（`/cannon`）负责标定、配置与服务端计算，客户端计算器（`/cannonClient`）负责本地计算；两侧的配置分别存储，格式都是「一炮一个 json」。

## 前置模组

| 名称 | 类型 | 链接 | 备注 |
|------|------|------|------|
| Fabric API | 必须 | [Modrinth](https://modrinth.com/mod/fabric-api) | - |
| [Ryan's Server Rendering Kit](https://github.com/hotpad100c/ryansrenderingkit) | 内嵌 | GitHub | `/cannon tracker` 的服务端 display 渲染；构建时通过 JiJ 嵌入 CannonTool，无需单独安装 |

## 版本支持

| MC 版本 | 状态 |
|---------|------|
| 1.21.5 / 1.21.6 / 1.21.8 / 1.21.9 / 1.21.11 | 支持 |
| 26.1 / 26.2 / 26.3 | 支持 |

服务端追踪使用构建时通过 JiJ 内嵌的 Ryan's Server Rendering Kit，只装 CannonTool 即可；1.21.8 用 1.21.6、26.3 用 26.2 的 kit 构建。不要额外把 kit jar 放进 `mods/`，避免重复 mod。

## 快速开始

服务端（需要 OP，在世界里标定）：

```
/cannon config start zzp1           # 开始配置
/cannon config give                 # 领取 8 颗已命名的末影珍珠（give snowball 换雪球）
# 投掷第一颗珍珠/雪球 → 在聊天栏点击选择一个基准 tick
# 依次投掷其余 7 颗（以及可选的 zzp1:ZZ 空配置）
/cannon config maxTnt 3000          # 设置最大单侧药量
/cannon config rom order RBD        # 设置红/蓝/方向三段的排列
/cannon config rom red  8,4,2,1,4480,2240,1120,560,10,20,40,80,160,240
/cannon config rom blue 240,160,80,40,20,10,560,1120,2240,4480,1,2,4,8 inverse

/cannon calc normal zzp1 1635 -122 256   # 服务端直接读取服务端配置计算
/cannon calc plane  zzp1 1635 -122 64 256
# 计算源点始终是执行命令时所在坐标；配置不保存 origin
/cannon calc expand 1                    # 展开第 1 条结果
/cannon calc hud 1                       # 动作栏持续显示配置编码；off 关闭
/cannon config                           # 同 /cannon config info；没有 start 时显示教程
/cannon tracker once                    # 只追踪当前最后一颗珍珠/雪球，再丢一颗自动关闭
/cannon tracker on                      # 持续追踪之后每一颗珍珠/雪球
/cannon tracker off                     # 关闭追踪
/cannon tracker hud [on|off]            # HUD 开关（Pos:[x,y,z]，4 位小数）
/cannon tracker trail [on|off]          # 连线开关（只有主人可见，UUID 着色，线段 yaw/pitch 沿线方向，256 格内复用 display 池；tick freeze 时仍刷新）
/cannon tracker box [on|off]            # 白色混凝土方块标记开关（每 tick，碰撞箱大小，不描边，yaw/pitch 沿前进方向）
```

每个玩家的 tracker 设置（HUD/连线/方块/模式）保存在 `<world>/pearlcannon/trackers/<uuid>.json`。默认：连线开、方块关、HUD 开、模式 off；`on` 会持久化为 always，`once` 是一次性动作、存盘为 off。

客户端（不需要 OP，把配置拉到本地后计算）：

```
/cannonClient push zzp1              # 把本地配置推送到服务端（需要 OP）
/cannonClient pull zzp1              # 从服务端拉取配置到本地
/cannonClient list                   # 查看本地已有配置
/cannonClient normal zzp1 1635 -122 256
/cannonClient plane  zzp1 1635 -122 64 256
# 客户端同样以本地玩家当前坐标为源点
/cannonClient hud 1                  # 把第 1 条结果的配置编码持续显示在动作栏
```

也可以不走拉取：把服务端的 `<world>/pearlcannon/zzp1.json` 手动复制到 `.minecraft/config/pearlcannon/zzp1.json`，下次计算会自动读取。

## 存储

| 侧 | 位置 | 说明 |
|----|------|------|
| 服务端 | `<world>/pearlcannon/<名称>.json` | 标定产生的配置 |
| 客户端 | `.minecraft/config/pearlcannon/<名称>.json` | 计算使用的配置；`pull` 或手动复制 |

> 配置目录沿用 `pearlcannon`，避免已有存档的配置失效。

## 文档

- [命令](docs/zh_cn/command.md)
- [项目结构](docs/zh_cn/project-structure.md)

English:
- [Commands](docs/en_us/command.md)

## 构建

```powershell
# 26.x 需要 JDK 25，1.21.x 需要 JDK 21
.\gradlew.bat :26.1:build -x test
.\gradlew.bat :1.21.5:build -x test
# 其它版本同理：:1.21.6 / :1.21.8 / :1.21.9 / :1.21.11 / :26.2 / :26.3
```

构建时 `build.gradle` 会从 `libs/` 选择对应版本的 runtime kit jar 并通过 JiJ 嵌入 CannonTool；开发时如果要在 `run/mods/` 单独放 kit，注意不要和 JiJ 版本重复。

产物在 `versions/<版本>/build/libs/`。
