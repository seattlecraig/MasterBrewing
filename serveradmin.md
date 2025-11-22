# MasterBrewing - Server Admin Guide

This guide covers installation, configuration, commands, and server management for the MasterBrewing plugin.

## Table of Contents
- [Installation](#installation)
- [Configuration](#configuration)
- [Commands](#commands)
- [Permissions](#permissions)
- [Upgrade Tier Configuration](#upgrade-tier-configuration)
- [Per-Potion Configuration](#per-potion-configuration)
- [Performance Considerations](#performance-considerations)
- [Troubleshooting](#troubleshooting)

## Installation

### Requirements
- **Paper/Spigot**: 1.21+ (or compatible fork)
- **Java**: 21+
- **Dependencies**: None

### Installation Steps

1. **Download** the MasterBrewing.jar file
2. **Place** it in your server's `/plugins/` folder
3. **Restart** your server (or use a plugin manager)
4. **Verify** installation:
   - Check console for green startup message: `[MasterBrewing] MasterBrewing Started!`
   - Check for `plugins/MasterBrewing/config.yml` file

### First-Time Setup

On first run, MasterBrewing will:
- Create `config.yml` with default upgrade tiers
- Create `playerdata/` folder for effect persistence
- Register commands and permissions
- Start the effect refresh task

## Configuration

### Default Config Structure

```yaml
# Default upgrade paths for all potions
upgrade-time:
  - "1,1,360"       # Level 1: 1 redstone = 6 minutes
  - "2,4,720"       # Level 2: 4 redstone = 12 minutes
  - "3,16,1080"     # Level 3: 16 redstone = 18 minutes
  - "4,32,1440"     # Level 4: 32 redstone = 24 minutes
  - "5,64,1800"     # Level 5: 64 redstone = 30 minutes

upgrade-power:
  - "1,1"           # Level 1: 1 glowstone = Power II
  - "2,4"           # Level 2: 4 glowstone = Power III
  - "3,16"          # Level 3: 16 glowstone = Power IV
  - "4,32"          # Level 4: 32 glowstone = Power V
  - "5,64"          # Level 5: 64 glowstone = Power VI

# Per-potion overrides (optional)
fortune:
  upgrade-time:
    - "1,1,360"     # Custom upgrade path for Fortune potions
    # ... more levels
  upgrade-power:
    - "1,1"         # Custom upgrade path
    # ... more levels
```

### Configuration Values Explained

#### upgrade-time Format
```yaml
- "level,redstone-cost,duration-seconds"
```

- **level**: The upgrade tier (1, 2, 3, etc.)
- **redstone-cost**: Number of redstone dust required (max 64)
- **duration-seconds**: Potion duration in seconds

**Example**: `"1,1,360"` = Level 1 costs 1 redstone for 360 seconds (6 minutes)

#### upgrade-power Format
```yaml
- "level,glowstone-cost"
```

- **level**: The upgrade tier (1, 2, 3, etc.)
- **glowstone-cost**: Number of glowstone dust required (max 64)

**Example**: `"1,1"` = Level 1 costs 1 glowstone (creates Power II potion)

**Important**: The highest level number in the config becomes the maximum upgrade level.

## Commands

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/masterbrewing help` | Show help menu with upgrade tables | None (default) |

### Admin Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/masterbrewing give stand <player>` | Give a Master Brewing Stand | `masterbrewing.give` |
| `/masterbrewing give potion <player> <type> [time] [power]` | Give a master potion | `masterbrewing.give` |
| `/masterbrewing give potion <player> random` | Give a random potion | `masterbrewing.give` |
| `/masterbrewing reload` | Reload configuration | `masterbrewing.admin` |

### Command Examples

```bash
# Give a Master Brewing Stand to Steve
/masterbrewing give stand Steve

# Give Steve a Speed II potion with 6-minute duration (level 1, 1)
/masterbrewing give potion Steve speed 1 1

# Give Steve a max-level Fortune potion
/masterbrewing give potion Steve fortune max

# Give Steve a Speed V potion with 30-minute duration (level 5, 4)
/masterbrewing give potion Steve speed 5 4

# Give Steve a random potion with random levels
/masterbrewing give potion Steve random

# Reload the configuration
/masterbrewing reload
```

### Available Potion Types

**Standard Potions**:
- speed, slowness, haste, mining_fatigue
- strength, weakness
- healing, harming (instant effects)
- leaping (jump boost)
- regeneration, poison, wither
- resistance, absorption, health_boost
- fire_resistance, water_breathing
- night_vision, invisibility, blindness
- hunger, saturation, nausea
- glowing, levitation, slow_falling
- conduit_power, dolphins_grace
- bad_omen, hero_of_the_village, darkness
- luck, unluck

**Special Potions**:
- **fly**: Creative-style flight in survival
- **fortune**: Luck effect for better loot

## Permissions

### Permission Nodes

| Permission | Description | Default |
|------------|-------------|---------|
| `masterbrewing.give` | Use `/masterbrewing give` commands | OP |
| `masterbrewing.admin` | Use `/masterbrewing reload` | OP |

### Setting Up Permissions

**LuckPerms Example**:
```bash
# Give all admins access to give commands
/lp group admin permission set masterbrewing.give true

# Give specific user full admin access
/lp user Steve permission set masterbrewing.admin true
```

**PermissionsEx Example**:
```bash
/pex group admin add masterbrewing.give
/pex user Steve add masterbrewing.admin
```

## Upgrade Tier Configuration

### Designing Upgrade Paths

When configuring upgrade tiers, consider:

1. **Material Cost Progression**: Exponential increases feel natural
   - 1 → 4 → 16 → 32 → 64 (geometric progression)
   - 1 → 2 → 4 → 8 → 16 (binary progression)

2. **Duration Progression**: Linear or exponential
   - Linear: 6min → 12min → 18min → 24min → 30min
   - Exponential: 3min → 6min → 12min → 24min → 48min

3. **Power Level Balance**: More levels = more granular progression
   - 5 levels: Quick progression, easy to max out
   - 10+ levels: Gradual progression, long-term goals
   - 64 levels: Extreme progression, ultimate endgame

### Example Configurations

#### Fast Progression (Casual)
```yaml
upgrade-time:
  - "1,1,600"       # 10 minutes
  - "2,4,1200"      # 20 minutes
  - "3,16,1800"     # 30 minutes

upgrade-power:
  - "1,1"           # Power II
  - "2,4"           # Power III
  - "3,16"          # Power IV
```

#### Balanced Progression (Default)
```yaml
upgrade-time:
  - "1,1,360"       # 6 minutes
  - "2,4,720"       # 12 minutes
  - "3,16,1080"     # 18 minutes
  - "4,32,1440"     # 24 minutes
  - "5,64,1800"     # 30 minutes

upgrade-power:
  - "1,1"           # Power II
  - "2,4"           # Power III
  - "3,16"          # Power IV
  - "4,32"          # Power V
  - "5,64"          # Power VI
```

#### Hardcore Progression (Difficult)
```yaml
upgrade-time:
  - "1,4,300"       # 5 minutes
  - "2,16,600"      # 10 minutes
  - "3,32,900"      # 15 minutes
  - "4,64,1200"     # 20 minutes

upgrade-power:
  - "1,4"           # Power II
  - "2,16"          # Power III
  - "3,32"          # Power IV
  - "4,64"          # Power V
```

## Per-Potion Configuration

### Override Defaults

You can customize upgrade paths for specific potion types:

```yaml
# Default paths for most potions
upgrade-time:
  - "1,1,360"
  - "2,4,720"
  
upgrade-power:
  - "1,1"
  - "2,4"

# Custom path for Fortune potions only
fortune:
  upgrade-time:
    - "1,1,360"
    - "2,2,720"     # Cheaper than default!
    - "3,4,1080"
    # ... up to 64 levels possible
  
  upgrade-power:
    - "1,1"
    - "2,2"         # More granular progression
    - "3,3"
    # ... up to 64 levels possible
```

### Supported Potion Types for Overrides

Any potion type from the "Available Potion Types" list can have custom upgrade paths.

### When to Use Per-Potion Overrides

**Good use cases**:
- **Rare potions** (fortune, fly) → More levels, cheaper costs
- **Common potions** (speed, strength) → Fewer levels, standard costs  
- **Utility potions** (night_vision, water_breathing) → Longer durations, low power
- **Combat potions** (strength, speed) → Higher power, standard duration

**Example: Separate PvP and Utility**:
```yaml
# Combat potions: Expensive, strong
strength:
  upgrade-power:
    - "1,4"
    - "2,16"
    - "3,64"

# Utility potions: Cheap, long duration
night_vision:
  upgrade-time:
    - "1,1,1800"    # 30 minutes for just 1 redstone
    - "2,2,3600"    # 1 hour for just 2 redstone
```

## Performance Considerations

### Effect Refresh Task

MasterBrewing runs a task every 3 seconds (60 ticks) to refresh potion effects. This:
- Checks only players with active master potion effects
- Only refreshes effects within 30 seconds of expiry
- Skips offline players automatically

**Performance impact**: Negligible on most servers (< 0.1ms per cycle)

### Player Data Persistence

- Effects are saved to `playerdata/<uuid>.yml` on logout
- Effects are loaded on login
- Files are small (< 1KB per player)

**Storage impact**: ~1KB per active player

### Brewing Stand State

- Brewing stand contents are saved in NBT on break
- Contents are restored on place
- No additional files created

### Optimization for Large Servers

If you have 500+ players:

1. **Reduce effect duration caps** to minimize active effects:
   ```yaml
   upgrade-time:
     - "1,1,300"     # 5 minutes (instead of 6)
     - "2,4,600"     # 10 minutes (instead of 12)
   ```

2. **Limit potion types** by only configuring overrides for allowed potions

3. **Monitor with timings**: `/timings on` then check `MasterBrewing` section

## Troubleshooting

### Common Issues

#### "Plugin not loading"
- **Check Java version**: Must be Java 21+
- **Check server version**: Must be Paper/Spigot 1.21+
- **Check console**: Look for errors during plugin loading

#### "Commands not working"
- **Check permissions**: Ensure user has correct permission nodes
- **Check spelling**: Command is `/masterbrewing` (all lowercase)
- **Check arguments**: Use `/masterbrewing help` to see syntax

#### "Potions not upgrading"
- **Check material count**: Need exact amount from config
- **Check max level**: Can't upgrade past configured maximum
- **Check brewing stand**: Must be a Master Brewing Stand
- **Check ingredient**: Must be exactly redstone or glowstone dust

#### "Effects not persisting after restart"
- **Check playerdata folder**: Should exist in `plugins/MasterBrewing/`
- **Check file permissions**: Server must be able to write files
- **Check console**: Look for save/load errors

#### "Flight not working"
- **Check gamemode**: Only works in survival/adventure
- **Check permissions**: No special permissions needed for using flight
- **Check expiry**: Flight effect may have ended

### Debug Mode

Enable detailed logging by editing `config.yml` and reloading:

```yaml
# Not in default config - add if needed
debug: true
```

Then check console for detailed operation logs.

### Getting Help

If issues persist:

1. **Check console logs** for errors
2. **Try `/masterbrewing reload`** to refresh config
3. **Test with OP permissions** to rule out permission issues
4. **Check plugin version** compatibility with your server

### Performance Monitoring

Monitor plugin performance:

```bash
# Enable timings
/timings on

# Let server run for 10+ minutes

# View report
/timings paste
```

Look for the `MasterBrewing` section to see task execution times.

## Best Practices

### Configuration Management

1. **Backup config.yml** before making changes
2. **Test changes on a test server** first
3. **Use comments** to document custom configurations
4. **Reload after changes**: `/masterbrewing reload`

### Giving Items

1. **Master Brewing Stands**: Give sparingly - they're powerful
2. **Master Potions**: Use as rewards, not regular items
3. **Consider economy integration**: Sell stands in shops

### Balancing

1. **Start conservative**: Can always make upgrades cheaper later
2. **Monitor economy**: Track redstone/glowstone prices
3. **Get player feedback**: Adjust based on actual usage
4. **Different tiers for different potions**: Not all potions need same progression

### Server Integration

1. **Event rewards**: Give high-level potions as prizes
2. **Quest rewards**: Give Master Brewing Stands for major quests
3. **Shop integration**: Sell stands or potions in server shop
4. **Rank perks**: Give stands to donators/VIPs

## Configuration Reference

### Full Default Config

```yaml
upgrade-time:
  - "1,1,360"
  - "2,4,720"
  - "3,16,1080"
  - "4,32,1440"
  - "5,64,1800"

upgrade-power:
  - "1,1"
  - "2,4"
  - "3,16"
  - "4,32"
  - "5,64"
```

This creates:
- **5 time levels**: 6min, 12min, 18min, 24min, 30min
- **5 power levels**: II, III, IV, V, VI
- **Total cost to max**: 117 redstone + 117 glowstone per potion

### Reload Command

After changing config.yml:

```bash
/masterbrewing reload
```

This will:
- Reload all upgrade tiers
- Recalculate maximum levels
- Apply changes immediately
- Log loaded configuration to console

## Support

For additional help:
- Check the Developer Guide (devguide.md) for technical details
- Check the End User Guide (enduser.md) for player instructions
- Report bugs or suggest features to SupaFloof Games, LLC

Happy Administrating! 🛠️
