# Commands

**Tip: use `Ctrl+F` to quickly find the command you need**

Commands have two roots: `/pearlCannonConfig` runs on the server and requires OP (`commands/moderator` since 1.21.11, permission level 2 before that); `/pearlCannonCalc` runs locally on the client and needs no permission.

## Configurator (pearlCannonConfig)

&emsp;Calibrates each cannon array's thrust and stores one json per cannon in the world's `pearlcannon/` directory.

### Configuration

&emsp;&emsp;- `/pearlCannonConfig start <name> - Start configuring; only named ender pearls/snowballs whose name starts with "name:" are recorded`

&emsp;&emsp;- `/pearlCannonConfig stop - Stop the current configuration`

&emsp;&emsp;- `/pearlCannonConfig list - List all server configurations`

&emsp;&emsp;- `/pearlCannonConfig info - Show the current configuration`

&emsp;&emsp;- `/pearlCannonConfig delete <name> - Delete a configuration`

&emsp;&emsp;- `/pearlCannonConfig maxTnt <count> - Set the maximum TNT per side (0 means unlimited)`

&emsp;&emsp;- `/pearlCannonConfig resetTick - Clear the reference tick and all samples`

&emsp;&emsp;- `/pearlCannonConfig selectTick <tick> - Pick the reference tick by clicking in chat`

&emsp;&emsp;- `/pearlCannonConfig give [ender_pearl|snowball] - Give the 8 named calibration projectiles; ender pearl by default`

### Calibration

&emsp;&emsp;- After the first projectile ends, chat lists every tick with a non-zero horizontal momentum change; click one to use it as the reference tick

&emsp;&emsp;- Every later projectile is recorded and discarded at the end of the reference tick; a zero change is reported as an anomaly

&emsp;&emsp;- Sample names: `00B` `00R` `01B` `01R` `10B` `10R` `11B` `11R`; the optional empty configuration uses `ZZ`

### ROM

&emsp;&emsp;- `/pearlCannonConfig rom order <order> - Set the section order: one of RBD/RDB/BRD/BDR/DRB/DBR`

&emsp;&emsp;- `/pearlCannonConfig rom red <w1,w2,...> - Comma-separated red weights, left to right as on the signs`

&emsp;&emsp;- `/pearlCannonConfig rom red inverse <w1,w2,...> - Same, but the list is reversed before saving`

&emsp;&emsp;- `/pearlCannonConfig rom blue <w1,w2,...> - Comma-separated blue weights`

&emsp;&emsp;- `/pearlCannonConfig rom blue inverse <w1,w2,...> - Same, but the list is reversed before saving`

&emsp;&emsp;- `/pearlCannonConfig rom show - Show the ROM settings`

&emsp;&emsp;- Server configurations are stored as `<world>/pearlcannon/<name>.json`

## Calculator (pearlCannonCalc)

&emsp;All calculation happens on the client, reading `.minecraft/config/pearlcannon/<name>.json`. It needs no OP and works without the server once the configuration is stored locally.

### Synchronizing configurations

&emsp;&emsp;- `/pearlCannonCalc pull [name] - Pull one or all configurations from the server into the client config directory`

&emsp;&emsp;- `/pearlCannonCalc list - List local configurations with their reference tick and sample count`

&emsp;&emsp;- Alternatively, copy `<world>/pearlcannon/<name>.json` to `.minecraft/config/pearlcannon/<name>.json` manually; the next calculation picks it up

### Calculation

&emsp;&emsp;- `/pearlCannonCalc normal <name> <x> <z> [maxTicks] - Tick mode: solve a firing solution`

&emsp;&emsp;- `/pearlCannonCalc plane <name> <x> <z> <y> [maxTicks] - Plane mode: land on the plane at height y and report the teleport position at the hit tick`

&emsp;&emsp;- `/pearlCannonCalc expand <index> - Expand the details of one result (configuration code, red/blue arrays)`

&emsp;&emsp;- `/pearlCannonCalc hud <index|off> - Keep a result's configuration code on the action bar; use off to hide it`

&emsp;&emsp;- Results are sorted by error; each row shows index, tick, landing point, error and total TNT count

&emsp;&emsp;- When a ROM is configured, the configuration code in the expanded view can be clicked to copy

## Two independent roots

&emsp;&emsp;- The client only registers `/pearlCannonCalc`; `/pearlCannonConfig ...` is sent to the server normally, where OP is still enforced
