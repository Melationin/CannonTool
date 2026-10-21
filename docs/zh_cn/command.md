# 命令

**提示：可以使用 `Ctrl+F` 快速查找自己想要的命令**

命令分为两个根：`/pearlCannonConfig` 在服务端执行，需要 OP（1.21.11 起的 `commands/moderator`，之前为权限等级 2）；`/pearlCannonCalc` 在客户端本地执行，不需要 OP。

## 配置器 (pearlCannonConfig)

&emsp;标定珍珠炮的各组阵列推力，并把配置保存在存档目录的 `pearlcannon/` 下（一炮一个 json）。

### 配置

&emsp;&emsp;- `/pearlCannonConfig start <名称> - 开始配置；之后只记录以“名称:”开头的命名珍珠/雪球`

&emsp;&emsp;- `/pearlCannonConfig stop - 结束当前配置`

&emsp;&emsp;- `/pearlCannonConfig list - 列出服务端所有配置`

&emsp;&emsp;- `/pearlCannonConfig info - 查看当前配置`

&emsp;&emsp;- `/pearlCannonConfig delete <名称> - 删除配置`

&emsp;&emsp;- `/pearlCannonConfig maxTnt <数量> - 设置最大单侧药量（0 表示不限）`

&emsp;&emsp;- `/pearlCannonConfig resetTick - 清空基准 tick 与全部样本`

&emsp;&emsp;- `/pearlCannonConfig selectTick <tick> - 在聊天栏点击选择基准 tick`

&emsp;&emsp;- `/pearlCannonConfig give [ender_pearl|snowball] - 为当前配置发放 8 颗命名投掷物，默认末影珍珠`

### 标定

&emsp;&emsp;- 第一颗投掷物结束后，聊天栏会列出所有水平动量变化非零的 tick，点击其中一个作为基准 tick

&emsp;&emsp;- 之后每颗投掷物都在基准 tick 末记录并清理；该 tick 没有动量变化时会提示异常

&emsp;&emsp;- 样本名：`00B` `00R` `01B` `01R` `10B` `10R` `11B` `11R`；空配置用 `ZZ` 命名（可选）

### ROM

&emsp;&emsp;- `/pearlCannonConfig rom order <顺序> - 设置红/蓝/方向三段的排列，顺序为 RBD/RDB/BRD/BDR/DRB/DBR 之一`

&emsp;&emsp;- `/pearlCannonConfig rom red <w1,w2,...> - 逗号分隔的红权重，按牌子从左到右`

&emsp;&emsp;- `/pearlCannonConfig rom red inverse <w1,w2,...> - 同上，但保存前逆序（镜像）`

&emsp;&emsp;- `/pearlCannonConfig rom blue <w1,w2,...> - 逗号分隔的蓝权重`

&emsp;&emsp;- `/pearlCannonConfig rom blue inverse <w1,w2,...> - 同上，但保存前逆序（镜像）`

&emsp;&emsp;- `/pearlCannonConfig rom show - 查看 ROM 配置`

&emsp;&emsp;- 服务端配置保存在存档目录的 `pearlcannon/<名称>.json`

## 计算器 (pearlCannonCalc)

&emsp;计算完全在客户端执行，读取 `.minecraft/config/pearlcannon/<名称>.json`。不需要 OP，也不需要服务端在线（配置已经在本地时）。

### 同步配置

&emsp;&emsp;- `/pearlCannonCalc pull [名称] - 从服务端拉取一个或全部配置，保存到客户端配置目录`

&emsp;&emsp;- `/pearlCannonCalc list - 列出本地已有配置及其基准 tick / 样本数`

&emsp;&emsp;- 也可以直接把服务端的 `pearlcannon/<名称>.json` 复制到 `.minecraft/config/pearlcannon/<名称>.json`，下次计算自动生效

### 计算

&emsp;&emsp;- `/pearlCannonCalc normal <名称> <x> <z> [最大tick] - tick 模式：按目标 tick 反解装药方案`

&emsp;&emsp;- `/pearlCannonCalc plane <名称> <x> <z> <y> [最大tick] - 平面模式：以珍珠向下穿过高度 y 的平面作为落点，取命中那一 tick 开始时的传送坐标`

&emsp;&emsp;- `/pearlCannonCalc expand <序号> - 展开某条结果的详细信息（含配置编码、红阵列、蓝阵列）；配置编码按红/蓝/方向着色，可点 [复制] 或 [HUD]`

&emsp;&emsp;- `/pearlCannonCalc hud <序号|off> - 把某条结果的配置编码持续显示在动作栏，off 关闭`

&emsp;&emsp;- 结果按误差排序，短行包含：序号、tick、落点、误差、总数；`[展开]` 可看方向、蓝/红药量、初速度与配置编码

&emsp;&emsp;- 配置了 ROM 时，`[展开]` 里的配置编码可直接点击复制

## 两个根互不干扰

&emsp;&emsp;- 客户端只注册 `/pearlCannonCalc`；输入 `/pearlCannonConfig ...` 时会正常发送到服务端，由服务端判断 OP 权限
