# Commands

**Tip: use `Ctrl+F` to quickly find the command you need**

Commands have two roots: `/cannon` runs on the server and requires OP (`commands/moderator` since 1.21.11, permission level 2 before that); `/cannonClient` runs locally on the client and needs no permission.

## Configurator (cannon)

&emsp;Calibrates each cannon array's thrust and stores one json per cannon in the world's `pearlcannon/` directory.

### Configuration

&emsp;&emsp;- `/cannon start <name> - Start configuring; only named ender pearls/snowballs whose name starts with "name:" are recorded`

&emsp;&emsp;- `/cannon stop - Stop the current configuration`

&emsp;&emsp;- `/cannon list - List all server configurations`

&emsp;&emsp;- `/cannon info - Show the current configuration`

&emsp;&emsp;- `/cannon delete <name> - Delete a configuration`

&emsp;&emsp;- `/cannon maxTnt <count> - Set the maximum TNT per side (0 means unlimited)`

&emsp;&emsp;- `/cannon resetTick - Clear the reference tick and all samples`

&emsp;&emsp;- `/cannon selectTick <tick> - Pick the reference tick by clicking in chat`

&emsp;&emsp;- `/cannon give [ender_pearl|snowball] - Give the 8 named calibration projectiles; ender pearl by default`

### Calibration

&emsp;&emsp;- After the first projectile ends, chat lists every tick with a non-zero horizontal momentum change; click one to use it as the reference tick

&emsp;&emsp;- Every later projectile is recorded and discarded at the end of the reference tick; a zero change is reported as an anomaly

&emsp;&emsp;- Sample names: `00B` `00R` `01B` `01R` `10B` `10R` `11B` `11R`; the optional empty configuration uses `ZZ`

### ROM

&emsp;&emsp;- `/cannon rom order <order> - Set the section order: one of RBD/RDB/BRD/BDR/DRB/DBR`

&emsp;&emsp;- `/cannon rom red <w1,w2,...> - Comma-separated red weights, left to right as on the signs`

&emsp;&emsp;- `/cannon rom red inverse <w1,w2,...> - Same, but the list is reversed before saving`

&emsp;&emsp;- `/cannon rom blue <w1,w2,...> - Comma-separated blue weights`

&emsp;&emsp;- `/cannon rom blue inverse <w1,w2,...> - Same, but the list is reversed before saving`

&emsp;&emsp;- `/cannon rom show - Show the ROM settings`

&emsp;&emsp;- Server configurations are stored as `<world>/pearlcannon/<name>.json`

### Server calculation and trails

&emsp;&emsp;- `/cannon calc normal <name> <x> <z> [maxTicks] - Tick mode using the server configuration directly`

&emsp;&emsp;- `/cannon calc plane <name> <x> <z> <y> [maxTicks] - Plane mode using the server configuration directly`

&emsp;&emsp;- `/cannon trail <name|off> - Draw matching ender pearl trails on the server; matches full name, config-name prefix, or sample-name suffix; off disables (26.1 only, requires Ryan's Server Rendering Kit)`

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

&emsp;&emsp;- Results are sorted by error; each row shows index, tick, landing point, error and total TNT count

&emsp;&emsp;- When a ROM is configured, the configuration code in the expanded view can be clicked to copy

## Two independent roots

&emsp;&emsp;- The client only registers `/cannonClient`; `/cannon ...` is sent to the server normally, where OP is still enforced
