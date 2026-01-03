# MasterBrewing Server Admin Guide

This guide covers installation, configuration, permissions, and administration of the MasterBrewing plugin.

## Installation

1. Download the MasterBrewing plugin JAR file
2. Place it in your server's `plugins/` directory
3. Start or restart the server
4. The plugin will create its default configuration files

### Directory Structure

After first run, the plugin creates:
```
plugins/MasterBrewing/
├── config.yml          # Main configuration file
└── playerdata/         # Per-player data storage
    └── {uuid}.yml      # Individual player effect/brewing data
```

## Configuration

The `config.yml` file controls all upgrade tiers and costs. Here's the structure:

### Global Upgrade Tiers

```yaml
# Duration upgrades (applies to all potions by default)
# Format: "level,redstone_cost,duration_in_seconds"
upgrade-time:
  - "1,4,600"      # Level 1: 4 redstone = 10 minutes
  - "2,8,1200"     # Level 2: 8 redstone = 20 minutes
  - "3,16,2400"    # Level 3: 16 redstone = 40 minutes
  - "4,32,3600"    # Level 4: 32 redstone = 1 hour
  - "5,64,7200"    # Level 5: 64 redstone = 2 hours

# Power upgrades (applies to all potions by default)
# Format: "level,glowstone_cost"
upgrade-power:
  - "1,4"          # Level 1: 4 glowstone = Amplifier I (Level II)
  - "2,8"          # Level 2: 8 glowstone = Amplifier II (Level III)
  - "3,16"         # Level 3: 16 glowstone = Amplifier III (Level IV)
  - "4,32"         # Level 4: 32 glowstone = Amplifier IV (Level V)
  - "5,64"         # Level 5: 64 glowstone = Amplifier V (Level VI)
```

### Per-Potion Custom Upgrade Paths

You can override the global settings for specific potion types:

```yaml
# Custom upgrade path for speed potions
speed:
  upgrade-time:
    - "1,2,300"    # Speed gets cheaper, shorter upgrades
    - "2,4,600"
    - "3,8,900"
  upgrade-power:
    - "1,2"        # Speed power upgrades are cheaper
    - "2,4"
    - "3,8"

# Custom upgrade path for fly potions (make them expensive)
fly:
  upgrade-time:
    - "1,8,300"    # Fly costs more for shorter duration
    - "2,16,600"
    - "3,32,900"
  upgrade-power:
    - "1,8"        # Fly power upgrades cost more
    - "2,16"
    - "3,32"
```

### Valid Potion Names for Custom Paths

Use these names as configuration keys for per-potion overrides:

| Config Key | Effect |
|------------|--------|
| `speed` | Speed |
| `slowness` | Slowness |
| `haste` | Haste |
| `mining_fatigue` | Mining Fatigue |
| `strength` | Strength |
| `healing` | Instant Health |
| `harming` | Instant Damage |
| `leaping` | Jump Boost |
| `nausea` | Nausea |
| `regeneration` | Regeneration |
| `resistance` | Resistance |
| `fire_resistance` | Fire Resistance |
| `water_breathing` | Water Breathing |
| `invisibility` | Invisibility |
| `blindness` | Blindness |
| `night_vision` | Night Vision |
| `hunger` | Hunger |
| `weakness` | Weakness |
| `poison` | Poison |
| `wither` | Wither |
| `health_boost` | Health Boost |
| `absorption` | Absorption |
| `saturation` | Saturation |
| `glowing` | Glowing |
| `levitation` | Levitation |
| `luck` | Luck |
| `unluck` | Bad Luck |
| `slow_falling` | Slow Falling |
| `conduit_power` | Conduit Power |
| `dolphins_grace` | Dolphin's Grace |
| `bad_omen` | Bad Omen |
| `hero_of_the_village` | Hero of the Village |
| `darkness` | Darkness |
| `fly` | Flight (custom) |
| `fortune` | Fortune (custom) |

### Instant Effects

These effects cannot have duration upgrades (redstone), only power upgrades (glowstone):
- `healing` (instant_health)
- `harming` (instant_damage)
- `saturation`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `masterbrewing.use` | Access `/masterbrewing` command to open virtual brewing stand | op |
| `masterbrewing.give` | Use `/masterbrewing give` commands | op |
| `masterbrewing.admin` | Use `/masterbrewing reload` command | op |

### Example Permission Setup (LuckPerms)

```
# Give all players access to virtual brewing stands
/lp group default permission set masterbrewing.use true

# Give moderators the ability to give items
/lp group moderator permission set masterbrewing.give true

# Give admins full access
/lp group admin permission set masterbrewing.admin true
```

## Commands

### Player Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/masterbrewing` | `masterbrewing.use` | Opens virtual Master Brewing Stand |
| `/masterbrewing help` | none | Shows help and upgrade tier tables |

### Admin Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/masterbrewing give stand <player>` | `masterbrewing.give` | Give a Master Brewing Stand item |
| `/masterbrewing give potion <player> <type>` | `masterbrewing.give` | Give a Master Potion with default levels |
| `/masterbrewing give potion <player> <type> <time> <power>` | `masterbrewing.give` | Give a Master Potion with specific levels |
| `/masterbrewing give potion <player> <type> max` | `masterbrewing.give` | Give max-level Master Potion |
| `/masterbrewing give potion <player> random` | `masterbrewing.give` | Give random Master Potion |
| `/masterbrewing reload` | `masterbrewing.admin` | Reload configuration |

### Command Examples

```
# Give a Master Brewing Stand to PlayerName
/masterbrewing give stand PlayerName

# Give a Speed potion with time level 3 and power level 2
/masterbrewing give potion PlayerName speed 3 2

# Give a max-level Fly potion
/masterbrewing give potion PlayerName fly max

# Give a random potion with random levels
/masterbrewing give potion PlayerName random

# Reload the configuration
/masterbrewing reload
```

## Data Storage

### Player Effect Data

Active Master Potion effects are stored per-player in:
```
plugins/MasterBrewing/playerdata/{uuid}.yml
```

Format:
```yaml
active-effects:
  - "speed,3,1699459200000"    # effectKey,amplifier,expiryTimestamp
  - "fly,1,1699460100000"
```

Effects persist through:
- Player logout/login
- Server restarts
- Plugin reloads

**Note:** Effect timers continue counting down while players are offline.

### Virtual Brewing Stand Data

Virtual brewing stand contents and fuel levels are also stored in the same player data file. This data persists across sessions.

## Plugin Compatibility

### SpecialBooks Plugin
MasterBrewing includes built-in compatibility with the SpecialBooks plugin's auto-pickup enchantment. When breaking a Master Brewing Stand:
- If the tool has the `auto_pickup` NBT tag, the stand goes directly to inventory
- Otherwise, the stand drops as a normal item

### Block Protection Plugins
Master Brewing Stands work with block protection plugins (LandClaim, GriefPrevention, etc.). The stand's special properties are stored in the block's PersistentDataContainer and persist through protection plugin interactions.

## Technical Details

### NBT Data Structure

**Master Brewing Stand Items:**
- `masterbrewing:master_brewing_stand` (BYTE=1) - Marker tag
- `masterbrewing:brewing_slot_0` through `brewing_slot_4` (STRING) - JSON serialized inventory
- `masterbrewing:brewing_fuel_level` (INT) - Fuel charges remaining

**Master Brewing Stand Blocks:**
- `masterbrewing:master_brewing_stand` (BYTE=1) - Marker tag
- Fuel level stored in vanilla block state

**Master Potions:**
- `masterbrewing:master_potion` (BYTE=1) - Marker tag
- `masterbrewing:potion_time_level` (INT) - Duration upgrade level
- `masterbrewing:potion_power_level` (INT) - Power upgrade level (amplifier)
- `masterbrewing:potion_duration` (INT) - Total duration in seconds
- `masterbrewing:potion_effect_type` (STRING) - Effect key

### Background Tasks

The plugin runs a background task every 3 seconds (60 ticks) that:
1. Checks all active Master Potion effects
2. Removes expired effects
3. Refreshes effects that are about to expire (bypasses vanilla's ~10 minute limit)
4. Displays flight time on action bar for players with active Fly effect
5. Shows expiration warnings at 30s and 10s remaining

### Fuel System

- One Blaze Powder = 20 fuel charges
- One fuel charge is consumed per upgrade operation
- Fuel is tracked separately for physical and virtual brewing stands

## Troubleshooting

### Effects Not Persisting
- Check that the `playerdata/` directory exists and is writable
- Verify the player's UUID file is being created
- Check console for save/load errors

### Brewing Not Working
- Ensure the brewing stand is a Master Brewing Stand (check for gold name)
- Verify fuel is present (Blaze Powder in fuel slot)
- Confirm the potion already has an effect (not water bottle or awkward potion)
- Check that the potion isn't already at max level for that upgrade type

### Configuration Not Loading
- Check for YAML syntax errors
- Verify the format matches the examples exactly
- Use `/masterbrewing reload` after changes
- Check console for parsing warnings

### Virtual Brewing Stand Issues
- Confirm player has `masterbrewing.use` permission
- Check if player data file is corrupted (delete to reset)

## Performance Considerations

### Memory Usage
- Active effects are stored in memory per-player
- Virtual brewing stand inventories are only in memory while open
- Player data files are small (typically < 1KB each)

### Disk I/O
- Player effects are saved asynchronously
- Data is saved on player quit, plugin disable, and inventory close
- Effects are loaded synchronously on player join (fast file reads)

### CPU Usage
- Background task runs every 3 seconds
- Only processes players with active Master Potion effects
- Effect refresh operations are lightweight

## Console Messages

### Startup
```
[MasterBrewing] MasterBrewing Started!
[MasterBrewing] By SupaFloof Games, LLC
[MasterBrewing] Loaded X time upgrades (max level: Y)
[MasterBrewing] Loaded X power upgrades (max level: Y)
[MasterBrewing] Loaded custom upgrade path for <potion> (time levels: X, power levels: Y)
```

### Shutdown
```
[MasterBrewing] MasterBrewing plugin disabled!
```

### Brewing Operations (Debug)
```
[VirtualBrew] Click detected in virtual brewing stand for PlayerName
[VirtualBrew] Completed X upgrade(s)
```

## Best Practices

1. **Backup Configuration** - Keep a copy of your customized config.yml

2. **Test Upgrades** - After configuration changes, test the upgrade costs match expectations

3. **Balance Carefully** - Consider game balance when setting upgrade costs
   - Fly potions should typically cost more
   - Combat effects like Strength might need higher costs
   - Utility effects like Night Vision can be cheaper

4. **Monitor Player Data** - Periodically check playerdata folder size
   - Files for inactive players can be cleaned up
   - Each file is small but many files can accumulate

5. **Use Virtual Stands** - Encourage players to use `/masterbrewing` for convenience
   - Reduces need for physical stand distribution
   - Contents persist like physical stands

6. **Set Appropriate Permissions** - Only give `masterbrewing.give` to trusted staff
   - Max-level potions can be very powerful
   - Random potions can produce unexpected combinations
