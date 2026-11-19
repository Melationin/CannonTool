# 本地依赖

Ryan's Server Rendering Kit 服务端多版本 jar（1.2.0）。

- 项目：[hotpad100c/ryansrenderingkit](https://github.com/hotpad100c/ryansrenderingkit) 的 `MultiVersion-ServerSide` 分支
- 许可证：MIT
- 用途：`/cannon tracker` 的服务端 display 渲染。`build.gradle` 会按版本选择这些 jar，并通过 **JiJ（`include`）** 把 runtime jar 嵌进 CannonTool 的 jar；发布时只发 CannonTool，不需要单独安装 kit。
- 本目录里的 jar 是本地构建的定制版：`VirtualDisplay` 的发送范围从 96 格调到 **256 格**，display 的 view range 元数据从 2.0 调到 4.0（4×64=256）；并新增 `VirtualDisplay.yawPitch(float, float)`，add packet 会发送实体的 yaw/pitch。

| 项目版本 | 编译用 jar | JiJ 内嵌 jar |
|---|---|---|
| 1.21.5 | `...+1.21.5-dev.jar` | `...+1.21.5.jar` |
| 1.21.6 | `...+1.21.6-dev.jar` | `...+1.21.6.jar` |
| 1.21.8 | `...+1.21.6-dev.jar` | `...+1.21.6.jar` |
| 1.21.9 | `...+1.21.9-dev.jar` | `...+1.21.9.jar` |
| 1.21.11 | `...+1.21.11-dev.jar` | `...+1.21.11.jar` |
| 26.1 | `...+26.1.jar` | `...+26.1.jar` |
| 26.2 | `...+26.2.jar` | `...+26.2.jar` |
| 26.3 | `...+26.2.jar` | `...+26.2.jar` |

- 1.21.x 的 `-dev.jar` 是 Mojang 映射的编译用 jar；`-dev` 不会进入最终 mod。
- 最终 CannonTool jar 的 `META-INF/jars/` 里会带对应版本的 `ryansserverrenderingkit-1.2.0+...jar`，服务器只需安装 CannonTool。
- 不要同时把 kit jar 放进 `mods/`，否则会重复同一个 mod ID。
