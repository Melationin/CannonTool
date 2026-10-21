## Pearl Cannon Calculator / 珍珠炮计算器

一个独立运行的 Fabric 模组：在游戏内标定珍珠炮各组阵列的推力，并按目标坐标反解装药方案、输出二进制码。

不依赖 Carpet。服务端配置器（`/pearlCannonConfig`）负责标定与配置，客户端计算器（`/pearlCannonCalc`）负责计算；两侧的配置分别存储，格式都是「一炮一个 json」。

## 前置模组

| 名称 | 类型 | 链接 | 备注 |
|------|------|------|------|
| Fabric API | 必须 | [Modrinth](https://modrinth.com/mod/fabric-api) | - |

## 版本支持

| MC 版本 | 状态 |
|---------|------|
| 1.21.5 / 1.21.6 / 1.21.8 / 1.21.9 / 1.21.11 | 支持 |
| 26.1 / 26.2 / 26.3 | 支持 |

## 快速开始

服务端（需要 OP，在世界里标定）：

```
/pearlCannonConfig start zzp1           # 开始配置
/pearlCannonConfig give                 # 领取 8 颗已命名的末影珍珠（give snowball 换雪球）
# 投掷第一颗珍珠/雪球 → 在聊天栏点击选择一个基准 tick
# 依次投掷其余 7 颗（以及可选的 zzp1:ZZ 空配置）
/pearlCannonConfig maxTnt 3000          # 设置最大单侧药量
/pearlCannonConfig rom order RBD        # 设置红/蓝/方向三段的排列
/pearlCannonConfig rom red  8,4,2,1,4480,2240,1120,560,10,20,40,80,160,240
/pearlCannonConfig rom blue 240,160,80,40,20,10,560,1120,2240,4480,1,2,4,8 inverse
```

客户端（不需要 OP，把配置拉到本地后计算）：

```
/pearlCannonCalc pull zzp1              # 从服务端拉取配置到本地
/pearlCannonCalc list                   # 查看本地已有配置
/pearlCannonCalc normal zzp1 1635 -122 256
/pearlCannonCalc plane  zzp1 1635 -122 64 256
/pearlCannonCalc hud 1                  # 把第 1 条结果的配置编码持续显示在动作栏
```

也可以不走拉取：把服务端的 `<world>/pearlcannon/zzp1.json` 手动复制到 `.minecraft/config/pearlcannon/zzp1.json`，下次计算会自动读取。

## 存储

| 侧 | 位置 | 说明 |
|----|------|------|
| 服务端 | `<world>/pearlcannon/<名称>.json` | 标定产生的配置 |
| 客户端 | `.minecraft/config/pearlcannon/<名称>.json` | 计算使用的配置；`pull` 或手动复制 |

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
```

产物在 `versions/<版本>/build/libs/`。
