# Commands

**Tip: use `Ctrl+F` to quickly find the command you need**

Commands have two roots: `/cannon` runs on the server and requires OP (`commands/moderator` since 1.21.11, permission level 2 before that); `/cannonClient` runs locally on the client and needs no permission.

## Configurator (cannon)

&emsp;Calibrates each cannon array's thrust and stores one json per cannon in the world's `pearlcannon/` directory.

### Configuration

&emsp;&emsp;- `/cannon config start <name> - Start configuring; only named ender pearls/snowballs whose name starts with "name:" are recorded`

&emsp;&emsp;- `/cannon config stop - Stop the current configuration`

&emsp;&emsp;- `/cannon config list - List all server configurations`

&emsp;&emsp;- `/cannon config info - Show the current configuration; without `start`, shows a short tutorial (same as `/cannon config`)`

&emsp;&emsp;- `/cannon config delete <name> - Delete a configuration`

&emsp;&emsp;- `/cannon config maxTnt <count> - Set the maximum TNT per side (0 means unlimited)`

&emsp;&emsp;- `/cannon config resetTick - Clear the reference tick and all samples`

&emsp;&emsp;- `/cannon config selectTick <tick> - Pick the reference tick by clicking in chat`

&emsp;&emsp;- `/cannon config give [ender_pearl|snowball] - Give the 8 named calibration projectiles; ender pearl by default`

### Calibration

&emsp;&emsp;- After the first projectile ends, chat lists every tick with a non-zero horizontal momentum change; click one to use it as the reference tick

&emsp;&emsp;- The reference tick is exactly the tick you selected; calibration does not use any other tick

&emsp;&emsp;- Every later projectile is recorded and discarded at the end of the reference tick; a zero change is reported as an anomaly

&emsp;&emsp;- Sample names: `00B` `00R` `01B` `01R` `10B` `10R` `11B` `11R`; the optional empty configuration uses `ZZ`

### ROM

&emsp;&emsp;- `/cannon config rom order <order> - Set the section order: one of RBD/RDB/BRD/BDR/DRB/DBR`

&emsp;&emsp;- `/cannon config rom red <w1,w2,...> - Comma-separated red weights, left to right as on the signs`

&emsp;&emsp;- `/cannon config rom red inverse <w1,w2,...> - Same, but the list is reversed before saving`

&emsp;&emsp;- `/cannon config rom blue <w1,w2,...> - Comma-separated blue weights`

&emsp;&emsp;- `/cannon config rom blue inverse <w1,w2,...> - Same, but the list is reversed before saving`

&emsp;&emsp;- `/cannon config rom show - Show the ROM settings`

&emsp;&emsp;- Server configurations are stored as `<world>/pearlcannon/<name>.json`

### Server calculation and tracking

&emsp;&emsp;- `/cannon calc normal <name> <x> <z> [maxTicks] - Tick mode using the server configuration directly`

&emsp;&emsp;- `/cannon calc plane <name> <x> <z> <y> [maxTicks] - Plane mode using the server configuration directly`

&emsp;&emsp;- The calculation origin is always the executing player's current position and is not stored in the configuration; calculating from another spot uses that new spot. The console has no player position, so it cannot run calculations`

&emsp;&emsp;- `/cannon calc expand <index> - Expand the last calculation's result details (code, red/blue arrays, initial velocity, etc.)`

&emsp;&emsp;- `/cannon calc hud <index|off> - Keep a result's configuration code on the action bar; off hides it`

&emsp;&emsp;- Server results match the client: one clickable `[expand]` row per solution, and the details' `[copy]`/`[HUD]` actions work from chat`

&emsp;&emsp;- `/cannon tracker - Track only the current last ender pearl/snowball; turns off after the next throw`

&emsp;&emsp;- `/cannon tracker on|off|once - Continuous / off / current only`

&emsp;&emsp;- `/cannon tracker hud [on|off] - Toggle the HUD; when enabled the action bar shows Pos:[x,y,z] with 4 decimals`

&emsp;&emsp;- `/cannon tracker trail [on|off] - Toggle the connecting line; visible only to its owner, raw path is not pre-split, only visible parts within 256 blocks are rendered through a reused display pool; segments follow the line direction (the display entity yaw/pitch points along the segment) and the color is the hue of the projectile UUID hash; after the pearl disappears the trail is frozen but still updates as the owner moves; it also keeps refreshing during tick freeze`

&emsp;&emsp;- `/cannon tracker box [on|off] - Toggle the per-tick white concrete block markers; each block matches the projectile collision box, is visible only to its owner, has no outline, its display entity yaw/pitch points forward, and it reuses a 256-block display pool`

&emsp;&emsp;- Tracking uses the UUID of the player's last thrown ender pearl/snowball; it is retried while unloaded and is not interrupted (the server-side kit is bundled via JiJ, no separate install needed)`

&emsp;&emsp;- Per-player tracker settings (HUD/trail/box/mode) are stored in `<world>/pearlcannon/trackers/<uuid>.json`; defaults are trail on, box off, HUD on, mode off. `on` persists as always; `once` is a one-shot action saved as off`

## Calculator (cannonClient)

&emsp;Calculation can run on the server (reading the server configuration) or on the client with a local copy. The client reads `.minecraft/config/pearlcannon/<name>.json` and needs no OP.

### Synchronizing configurations

&emsp;&emsp;- `/cannonClient push <name> - Send a local configuration to the server (requires OP); names are completed from the client`

&emsp;&emsp;- `/cannonClient pull [name] - Pull one or all configurations from the server; names are completed from the server`

&emsp;&emsp;- `/cannonClient list - List local configurations with their reference tick and sample count`

&emsp;&emsp;- Alternatively, copy `<world>/pearlcannon/<name>.json` to `.minecraft/config/pearlcannon/<name>.json` manually; the next calculation picks it up

### Calculation

&emsp;&emsp;- `/cannonClient normal <name> <x> <z> [maxTicks] - Tick mode: solve a firing solution`

&emsp;&emsp;- `/cannonClient plane <name> <x> <z> <y> [maxTicks] - Plane mode: land on the plane at height y and report the teleport position at the hit tick`

&emsp;&emsp;- `/cannonClient expand <index> - Expand the details of one result (configuration code, red/blue arrays); the code is colored by section and offers [Copy] / [HUD]`

&emsp;&emsp;- `/cannonClient hud <index|off> - Keep a result's configuration code on the action bar; use off to hide it`

&emsp;&emsp;- The client calculation origin is the local player's current position (also not stored in the configuration)`

&emsp;&emsp;- Results are sorted by error; each row shows index, tick, landing point, error and total TNT count

&emsp;&emsp;- When a ROM is configured, the configuration code in the expanded view can be clicked to copy

## Two independent roots

&emsp;&emsp;- The client only registers `/cannonClient`; `/cannon ...` is sent to the server normally, where OP is still enforced
