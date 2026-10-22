# 命令

**提示：可以使用 `Ctrl+F` 快速查找自己想要的命令**

命令分为两个根：`/cannon` 在服务端执行，需要 OP（1.21.11 起的 `commands/moderator`，之前为权限等级 2）；`/cannonClient` 在客户端本地执行，不需要 OP。

## 配置器 (cannon)

&emsp;标定珍珠炮的各组阵列推力，并把配置保存在存档目录的 `pearlcannon/` 下（一炮一个 json）。

### 配置

&emsp;&emsp;- `/cannon config start <名称> - 开始配置；之后只记录以“名称:”开头的命名珍珠/雪球`

&emsp;&emsp;- `/cannon config stop - 结束当前配置`

&emsp;&emsp;- `/cannon config list - 列出服务端所有配置`

&emsp;&emsp;- `/cannon config info - 查看当前配置`

&emsp;&emsp;- `/cannon config delete <名称> - 删除配置`

&emsp;&emsp;- `/cannon config maxTnt <数量> - 设置最大单侧药量（0 表示不限）`

&emsp;&emsp;- `/cannon config resetTick - 清空基准 tick 与全部样本`

&emsp;&emsp;- `/cannon config selectTick <tick> - 在聊天栏点击选择基准 tick`

&emsp;&emsp;- `/cannon config give [ender_pearl|snowball] - 为当前配置发放 8 颗命名投掷物，默认末影珍珠`

### 标定

&emsp;&emsp;- 第一颗投掷物结束后，聊天栏会列出所有水平动量变化非零的 tick，点击其中一个作为基准 tick

&emsp;&emsp;- 之后每颗投掷物都在基准 tick 末记录并清理；该 tick 没有动量变化时会提示异常

&emsp;&emsp;- 样本名：`00B` `00R` `01B` `01R` `10B` `10R` `11B` `11R`；空配置用 `ZZ` 命名（可选）

### ROM

&emsp;&emsp;- `/cannon config rom order <顺序> - 设置红/蓝/方向三段的排列，顺序为 RBD/RDB/BRD/BDR/DRB/DBR 之一`

&emsp;&emsp;- `/cannon config rom red <w1,w2,...> - 逗号分隔的红权重，按牌子从左到右`

&emsp;&emsp;- `/cannon config rom red inverse <w1,w2,...> - 同上，但保存前逆序（镜像）`

&emsp;&emsp;- `/cannon config rom blue <w1,w2,...> - 逗号分隔的蓝权重`

&emsp;&emsp;- `/cannon config rom blue inverse <w1,w2,...> - 同上，但保存前逆序（镜像）`

&emsp;&emsp;- `/cannon config rom show - 查看 ROM 配置`

&emsp;&emsp;- 服务端配置保存在存档目录的 `pearlcannon/<名称>.json`

### 服务端计算与轨迹

&emsp;&emsp;- `/cannon calc normal <名称> <x> <z> [最大tick] - 直接读取服务端配置做 tick 模式计算`

&emsp;&emsp;- `/cannon calc plane <名称> <x> <z> <y> [最大tick] - 直接读取服务端配置做平面模式计算`

&emsp;&emsp;- `/cannon trail <名称|off> - 在服务端显示名称匹配的末影珍珠轨迹；每个玩家最多一条，珍珠消失后保留已画轨迹，最长 1000 tick；off 关闭（仅 26.1，需 Ryan's Server Rendering Kit）`

## 计算器 (cannonClient)

&emsp;计算可以在服务端直接执行（读取服务端配置），也可以拉取到客户端本地执行。客户端读取 `.minecraft/config/pearlcannon/<名称>.json`，不需要 OP。

### 同步配置

&emsp;&emsp;- `/cannonClient push <名称> - 把本地配置发送到服务端（需要 OP），名称使用客户端本地补全`

&emsp;&emsp;- `/cannonClient pull [名称] - 从服务端拉取一个或全部配置，名称支持服务端补全`

&emsp;&emsp;- `/cannonClient list - 列出本地已有配置及其基准 tick / 样本数`

&emsp;&emsp;- 也可以直接把服务端的 `pearlcannon/<名称>.json` 复制到 `.minecraft/config/pearlcannon/<名称>.json`，下次计算自动生效

### 计算

&emsp;&emsp;- `/cannonClient normal <名称> <x> <z> [最大tick] - tick 模式：按目标 tick 反解装药方案`

&emsp;&emsp;- `/cannonClient plane <名称> <x> <z> <y> [最大tick] - 平面模式：以珍珠向下穿过高度 y 的平面作为落点，取命中那一 tick 开始时的传送坐标`

&emsp;&emsp;- `/cannonClient expand <序号> - 展开某条结果的详细信息（含配置编码、红阵列、蓝阵列）；配置编码按红/蓝/方向着色，可点 [复制] 或 [HUD]`

&emsp;&emsp;- `/cannonClient hud <序号|off> - 把某条结果的配置编码持续显示在动作栏，off 关闭`

&emsp;&emsp;- 结果按误差排序，短行包含：序号、tick、落点、误差、总数；`[展开]` 可看方向、蓝/红药量、初速度与配置编码

&emsp;&emsp;- 配置了 ROM 时，`[展开]` 里的配置编码可直接点击复制

## 两个根互不干扰

&emsp;&emsp;- 客户端只注册 `/cannonClient`；输入 `/cannon ...` 时会正常发送到服务端，由服务端判断 OP 权限
