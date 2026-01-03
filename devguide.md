# MasterBrewing Developer Guide

This document provides a comprehensive technical overview of the MasterBrewing plugin architecture, code structure, and implementation details for developers who want to understand, modify, or extend the plugin.

## Architecture Overview

MasterBrewing is implemented as a single-file Java plugin following the SupaFloof Games architecture pattern. The entire plugin logic is contained within `MasterBrewing.java`, making it easy to understand the complete flow without navigating multiple files.

### Design Philosophy

- **Single-file architecture** - All code in one file for easy comprehension and maintenance
- **Extensive JavaDoc comments** - Every method, field, and class is documented
- **PersistentDataContainer (PDC)** - Uses Bukkit's NBT storage API for all custom data
- **Adventure Components** - Modern text formatting using the Adventure API
- **Async I/O** - File operations run asynchronously to prevent main thread blocking

## Class Structure

```
MasterBrewing (extends JavaPlugin implements Listener, TabCompleter)
├── Inner Classes
│   ├── UpgradePath          - Holds upgrade tier configuration per potion type
│   └── ActiveMasterEffect   - Tracks active effects on players
├── Static Lookup Tables
│   ├── POTION_NAME_TO_EFFECT_KEY  - Maps user-friendly names to effect keys
│   └── INSTANT_EFFECTS            - Set of instant effect identifiers
├── NamespacedKey Fields
│   ├── masterBrewingStandKey
│   ├── potionTimeLevelKey, potionPowerLevelKey
│   ├── potionDurationKey, potionEffectTypeKey
│   ├── masterPotionKey
│   └── brewingSlot0Key through brewingSlot4Key, brewingFuelLevelKey
├── Runtime Data Maps
│   ├── activeMasterEffects  - UUID -> List<ActiveMasterEffect>
│   ├── virtualBrewingStands - UUID -> Inventory
│   └── virtualBrewingFuel   - UUID -> Integer
├── Configuration Maps
│   ├── timeUpgrades         - Global duration upgrades
│   ├── powerUpgrades        - Global power upgrades
│   └── potionUpgradePaths   - Per-potion custom upgrade paths
└── Methods (grouped by functionality)
```

## NamespacedKey Registry

All custom NBT data uses NamespacedKeys in the format `masterbrewing:key_name`:

| Key | Data Type | Purpose |
|-----|-----------|---------|
| `master_brewing_stand` | BYTE | Marks items/blocks as Master Brewing Stands |
| `potion_time_level` | INTEGER | Duration upgrade level (0 = unupgraded) |
| `potion_power_level` | INTEGER | Power/amplifier level (0 = base level I) |
| `potion_duration` | INTEGER | Total effect duration in seconds |
| `potion_effect_type` | STRING | Effect identifier (e.g., "speed", "fly") |
| `master_potion` | BYTE | Marks items as Master Potions |
| `brewing_slot_0` - `brewing_slot_4` | STRING | JSON-serialized ItemStack for inventory slots |
| `brewing_fuel_level` | INTEGER | Remaining fuel charges (0-20) |

## Inner Classes

### UpgradePath

Encapsulates all upgrade configuration for a specific potion type:

```java
private static class UpgradePath {
    Map<Integer, int[]> timeUpgrades;    // level -> [redstoneCost, durationSeconds]
    Map<Integer, Integer> powerUpgrades; // level -> glowstoneCost
    int maxTimeLevel;                    // Highest time upgrade available
    int maxPowerLevel;                   // Highest power upgrade available
}
```

### ActiveMasterEffect

Tracks a single active effect on a player:

```java
private static class ActiveMasterEffect {
    String effectTypeKey;  // Effect identifier (e.g., "speed", "fly")
    int amplifier;         // 0-based (0 = level I, 1 = level II, etc.)
    long expiryTime;       // System.currentTimeMillis() timestamp
}
```

## Static Lookup Tables

### POTION_NAME_TO_EFFECT_KEY

Created by `createPotionNameMap()`, provides O(1) lookup for:
- Command argument validation
- Tab completion options
- User-friendly name to internal key translation

Maps names like "healing" → "instant_health", "leaping" → "jump_boost"

### INSTANT_EFFECTS

Set containing effect keys that have no duration:
- `instant_health`
- `instant_damage`
- `saturation`

Used to skip duration upgrades for these effects.

## Plugin Lifecycle

### onEnable()

1. Display startup messages (green + magenta branding)
2. Initialize all NamespacedKey objects
3. Call `saveDefaultConfig()` to create config.yml if missing
4. Call `loadUpgradeTiers()` to parse configuration
5. Register event listeners (`this`)
6. Set command executor and tab completer
7. Start background effect refresh task via `startMasterPotionEffectTask()`
8. Load effects for any online players (handles /reload case)
9. Create playerdata directory

### onDisable()

1. Save all active effects to disk
2. Save all open virtual brewing stand inventories
3. Log shutdown message

## Configuration Loading

### loadUpgradeTiers()

Parses config.yml with the following logic:

1. Clear existing maps to handle reload cleanly
2. Parse global `upgrade-time` list (format: "level,cost,duration")
3. Parse global `upgrade-power` list (format: "level,cost")
4. Iterate all top-level config keys
5. Skip keys that are global sections
6. For keys matching POTION_NAME_TO_EFFECT_KEY, check for custom upgrade sections
7. Create UpgradePath objects for potions with custom configurations
8. Calculate maxTimeLevel and maxPowerLevel across all configurations

### Upgrade Path Resolution

Three methods handle fallback to global defaults:
- `getTimeUpgrades(effectKey)` - Returns potion-specific or global time upgrades
- `getPowerUpgrades(effectKey)` - Returns potion-specific or global power upgrades
- `getMaxTimeLevel(effectKey)` / `getMaxPowerLevel(effectKey)` - Returns appropriate max levels

## Event Handlers

### Block Events

**BlockPlaceEvent (HIGHEST priority)**
- Detects Master Brewing Stand item placement
- Schedules 1-tick delayed task to mark block PDC
- Restores serialized inventory from item NBT
- Restores fuel level

**BlockBreakEvent (LOWEST priority)**
- Cancels event to prevent vanilla drop
- Serializes inventory to JSON strings
- Creates new Master Brewing Stand item with saved data
- Handles SpecialBooks auto-pickup compatibility

### Brewing Events

**BrewEvent**
- Detects brewing in physical Master Brewing Stands
- Checks ingredient type (redstone/glowstone)
- Calls upgrade logic on all valid potions in slots 0-2

### Interaction Events

**PlayerInteractEvent**
- Intercepts right-click on Master Potions
- Cancels vanilla consumption
- Applies effect to player via `activeMasterEffects`
- Handles special Fly effect (setAllowFlight, setFlying, setFlySpeed)

**PlayerItemConsumeEvent (LOWEST priority)**
- Safety handler to block vanilla consumption of Master Potions

### Player Events

**PlayerJoinEvent**
- Loads saved effects from playerdata file
- Schedules 1-tick delayed task to restore effects
- Restores flight state if player has active Fly effect

**PlayerQuitEvent**
- Saves active effects before removing from map
- Effects timer continues counting down while offline

### Inventory Events

**InventoryClickEvent / InventoryDragEvent (MONITOR priority)**
- Detects interactions in virtual brewing stands
- Schedules 2-tick delayed processing (lets click complete first)
- Calls `processVirtualBrewing()`

**InventoryCloseEvent**
- Saves virtual brewing stand contents to player data file
- Cleans up in-memory tracking maps

## Core Methods

### createMasterBrewingStand()

Creates the Master Brewing Stand item with:
- Gold + Bold display name
- Informative lore text
- PDC marker tag

### createMasterPotion() / upgradeMasterPotion()

The upgrade process:
1. Validate potion has an effect type
2. Get current time/power levels from PDC
3. Calculate next level and material cost
4. Check against max levels
5. Update PDC with new levels/duration
6. Call `updateMasterPotionDisplay()` to refresh visuals

### updateMasterPotionDisplay()

Updates all visual aspects of a Master Potion:
- Sets potion color based on effect type
- Clears and re-adds custom effects
- Builds display name with Roman numerals
- Constructs lore with current stats and upgrade costs
- Shows "(MAX)" indicators when at cap

### processVirtualBrewing()

Handles brewing in virtual stands:
```java
while (true) {
    // Check ingredient (slot 3) for redstone/glowstone
    // Check fuel (slot 4) for blaze powder if needed
    // Find upgradeable potions (slots 0-2)
    // Calculate and verify material cost
    // Consume fuel (1 charge per upgrade)
    // Consume ingredient materials
    // Apply upgrade to all valid potions
    // Repeat until cannot upgrade
}
```

## Background Task

### startMasterPotionEffectTask()

Runs every 60 ticks (3 seconds):

```java
for each player in activeMasterEffects:
    for each effect:
        if expired:
            remove effect, disable flight if needed
        else if expiring soon (30s or 10s):
            refresh effect, show warning
        else if below 3 seconds remaining:
            refresh to prevent expiry
        
        if fly effect:
            build action bar message with time remaining
    
    show action bar if player has active fly
    remove player from map if no effects remain
```

## Data Persistence

### Effect Serialization

Format: `"effectTypeKey,amplifier,expiryTime"` (comma-separated string)

**savePlayerEffects()** - Runs async, saves to playerdata/{uuid}.yml
**loadPlayerEffects()** - Runs sync on join, parses from YAML

### Virtual Brewing Stand Serialization

Uses Gson to serialize ItemStacks:
```java
Gson gson = new Gson();
Map<String, Object> map = item.serialize();
String json = gson.toJson(map);
```

Stored in same player data file with fuel level.

## Utility Methods

### formatDuration(int seconds)

Returns human-readable string:
- `< 60s`: "30s"
- `< 1h`: "5m 30s"
- `>= 1h`: "1h 30m 0s"

### toRoman(int number)

Converts integers to Roman numerals for potion level display.

### formatEffectName(PotionEffectType)

Converts internal effect names to display names (e.g., "instant_health" → "Instant Health").

### getPotionColor(PotionEffectType)

Returns appropriate org.bukkit.Color for each effect type to match vanilla colors.

### isPotion(ItemStack)

Checks if item is POTION, SPLASH_POTION, or LINGERING_POTION.

### getBasePotionEffect(ItemStack)

Extracts the primary PotionEffectType from a vanilla potion.

## Command System

### onCommand()

Main dispatcher:
- No args → `openVirtualBrewingStand()`
- "help" → `sendHelp()`
- "give" → `handleGive()`
- "reload" → `handleReload()`

### Tab Completion

`onTabComplete()` provides context-aware suggestions:
- First arg: help, give (if permitted), reload (if permitted)
- After "give": stand, potion
- After "give potion <player>": random + all potion names from POTION_NAME_TO_EFFECT_KEY
- After potion type: max + level numbers 1 to maxTimeLevel
- Final arg: level numbers 1 to maxPowerLevel

## Error Handling

### Configuration Parsing
- Invalid format entries are logged and skipped
- Missing sections result in empty maps (safe defaults)
- Parsing exceptions don't crash the plugin

### Effect Application
- Null checks throughout effect processing
- Invalid effect types are logged and skipped
- Unknown effect keys return null from PotionEffectType.getByKey()

### File I/O
- Missing player files handled gracefully
- Save operations wrapped in try-catch
- Errors logged but don't propagate

## Performance Optimizations

1. **Static lookup maps** - O(1) instead of O(n) for potion name validation
2. **TreeMap for upgrades** - Maintains sorted order for iteration
3. **Async file saves** - Prevents main thread blocking
4. **Lazy effect cleanup** - Only process players with effects
5. **Batch brewing** - Single loop processes multiple upgrades

## Integration Points

### SpecialBooks Compatibility

Checks for auto_pickup NBT tag on breaking tool:
```java
NamespacedKey autoPickupKey = new NamespacedKey("specialbooks", "auto_pickup");
if (toolMeta.getPersistentDataContainer().has(autoPickupKey, PersistentDataType.BYTE)) {
    // Add to inventory instead of dropping
}
```

### Extending the Plugin

To add a new custom effect:

1. Add entry to `POTION_NAME_TO_EFFECT_KEY`
2. Add special handling in `updateMasterPotionDisplay()` for lore
3. Add special handling in effect application (like fly/fortune)
4. Add color mapping in `getPotionColor()` if needed

To add a new upgrade type:

1. Create new NamespacedKey for tracking
2. Add configuration parsing in `loadUpgradeTiers()`
3. Add cost/application logic in `upgradeMasterPotion()`
4. Update display logic to show new upgrade info

## Dependencies

- **Paper/Spigot API** - Core server functionality
- **Adventure API** - Text component formatting (included in Paper)
- **Gson** - JSON serialization for ItemStack persistence

## Build Requirements

- Java 17+
- Paper API 1.20.x+
- Maven for dependency management

## Package Structure

```
com.supafloof.masterbrewing
└── MasterBrewing.java
```

## Code Style

- Extensive inline comments explaining logic
- JavaDoc on all public/private methods
- Green startup message, magenta author credit
- Consistent `getLogger().info()` for debug/status messages
- `Component.text()` for all player-facing messages
