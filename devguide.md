# MasterBrewing - Developer Guide

This guide provides technical documentation for developers who want to understand, modify, or extend the MasterBrewing plugin.

## Table of Contents
- [Architecture Overview](#architecture-overview)
- [Code Structure](#code-structure)
- [Core Systems](#core-systems)
- [Data Persistence](#data-persistence)
- [Event Flow](#event-flow)
- [NBT Data Format](#nbt-data-format)
- [Configuration System](#configuration-system)
- [Extension Points](#extension-points)
- [Performance Optimizations](#performance-optimizations)
- [Building and Development](#building-and-development)

## Architecture Overview

### Design Philosophy

MasterBrewing follows SupaFloof Games' standard plugin architecture:
- **Single-file design**: All code in one Java file for simplicity
- **Extensive documentation**: Every method has detailed JavaDoc
- **Persistent NBT storage**: Item and block data stored in Minecraft's NBT system
- **Event-driven**: React to Minecraft events rather than polling
- **Effect refresh system**: Background task ensures effects remain active

### Key Components

1. **Master Brewing Stands**: Custom brewing stands that support unlimited upgrades
2. **Master Potions**: Potions with NBT data tracking upgrade levels
3. **Effect Manager**: Background task that refreshes active effects
4. **Configuration System**: YAML-based upgrade tier definitions
5. **Data Persistence**: Player effect data saved across sessions

## Code Structure

### Class Organization

```
MasterBrewing extends JavaPlugin implements Listener, TabCompleter
├── Persistent Data Keys (NamespacedKey fields)
├── Active Effect Tracking (Map<UUID, List<ActiveMasterEffect>>)
├── Configuration Data (Map-based upgrade tiers)
├── Static Lookup Maps (Optimization)
├── Inner Classes
│   ├── UpgradePath (Per-potion upgrade configuration)
│   └── ActiveMasterEffect (Active effect tracking)
├── Lifecycle Methods (onEnable, onDisable)
├── Configuration Loading (loadUpgradeTiers, getters)
├── Effect Management (startMasterPotionEffectTask)
├── Item Creation (createMasterBrewingStand)
├── Event Handlers
│   ├── onBlockPlace (Brewing stand placement)
│   ├── onBlockBreak (Brewing stand breaking)
│   ├── onBrew (Brewing completion)
│   ├── onPlayerInteract (Potion consumption)
│   ├── onPotionDrink (Prevent vanilla consumption)
│   ├── onInventoryClick (Prevent automation)
│   ├── onPlayerQuit (Save effects)
│   └── onPlayerJoin (Load effects)
├── Brewing Logic (processMasterBrew)
├── Data Persistence (savePlayerEffects, loadPlayerEffects)
├── Helper Methods (formatting, colors, conversions)
├── Command Handling (onCommand, subcommand handlers)
└── Tab Completion (onTabComplete)
```

### Package Structure

```
com.supafloof.masterbrewing
└── MasterBrewing.java (Single file - 2651 lines)
```

## Core Systems

### 1. Master Brewing Stand System

**Identification**:
- Brewing stands are marked with NBT tag: `masterbrewing:master_brewing_stand`
- Item has custom name ("Master Brewing Stand") and lore
- Block entity stores the NBT tag after placement

**State Preservation**:
- When broken, all inventory slots are serialized to JSON
- JSON is stored in the item's NBT as strings
- When placed, items are deserialized and restored
- Fuel level is also preserved

**Code Location**: 
- `createMasterBrewingStand()` - Item creation
- `onBlockPlace()` - Placement handling
- `onBlockBreak()` - Breaking and state saving

### 2. Master Potion System

**NBT Data Stored on Potions**:

| Key | Type | Purpose |
|-----|------|---------|
| `master_potion` | BYTE | Identifies as master potion |
| `potion_time_level` | INTEGER | Current duration upgrade level |
| `potion_power_level` | INTEGER | Current power upgrade level |
| `potion_duration` | INTEGER | Duration in seconds |
| `potion_effect_type` | STRING | Effect key (e.g., "speed", "fly") |

**Visual Representation**:
- Display name: Gold, italic, with Roman numeral power level
- Lore: Purple "Master Potion" text + effect stats + upgrade costs
- Color: Matches vanilla Minecraft potion colors per effect
- Custom effects: Hidden from player but present for vanilla compat

**Code Location**:
- `processMasterBrew()` - Potion upgrading
- `onPlayerInteract()` - Potion consumption

### 3. Effect Refresh System

**Purpose**: Ensures master potion effects remain active for their full duration.

**How It Works**:
1. Background task runs every 3 seconds (60 ticks)
2. Iterates through all players with active master potion effects
3. Calculates remaining time for each effect
4. Sends expiration warnings at 30s and 10s
5. Restores missing effects (if removed by other plugins)
6. Maintains flight state for fly potions
7. Removes expired effects

**Optimizations**:
- Only checks players in the `activeMasterEffects` map
- Skips offline players without lookup
- Only refreshes effects within 30s of expiry
- Flight speed only updated if different

**Code Location**: `startMasterPotionEffectTask()`

### 4. Configuration System

**Structure**:
```yaml
# Default paths (used by all potions unless overridden)
upgrade-time: [...]
upgrade-power: [...]

# Per-potion overrides
<potion_name>:
  upgrade-time: [...]
  upgrade-power: [...]
```

**Loading Process**:
1. `loadUpgradeTiers()` called on enable and reload
2. Parse default upgrade-time and upgrade-power lists
3. For each config key, check if it's a valid potion name
4. If valid, load custom upgrade paths
5. Create UpgradePath objects for custom potions
6. Store in `potionUpgradePaths` map

**Access Pattern**:
```java
// Get upgrade map for specific potion (falls back to default)
Map<Integer, int[]> timeUpgrades = getTimeUpgrades(effectKey);
Map<Integer, Integer> powerUpgrades = getPowerUpgrades(effectKey);

// Get max levels for specific potion
int maxTime = getMaxTimeLevel(effectKey);
int maxPower = getMaxPowerLevel(effectKey);
```

**Code Location**: 
- `loadUpgradeTiers()` - Configuration parsing
- `getTimeUpgrades()`, `getPowerUpgrades()` - Access methods

## Data Persistence

### Player Effect Storage

**File Format**: YAML in `plugins/MasterBrewing/playerdata/<uuid>.yml`

```yaml
effects:
  - "speed,2,1234567890123"  # effectKey,amplifier,expiryTime
  - "strength,1,1234567890456"
```

**Save Trigger**: 
- Player logout (`onPlayerQuit`)
- Server shutdown (`onDisable`)

**Load Trigger**: 
- Player login (`onPlayerJoin`)
- Server startup with online players (`onEnable`)

**Code Location**:
- `savePlayerEffects(UUID, List<ActiveMasterEffect>)`
- `loadPlayerEffects(UUID)`

### Brewing Stand State Storage

**Storage Location**: NBT data on the brewing stand item

**Data Stored**:
- Slot 0-2: Potion slots (JSON serialized ItemStack)
- Slot 3: Ingredient slot (JSON serialized ItemStack)
- Slot 4: Fuel slot (JSON serialized ItemStack)
- Fuel level: Integer

**Serialization**:
```java
// Serialize ItemStack to JSON
Gson gson = new Gson();
String json = gson.toJson(itemStack.serialize());

// Store in NBT
meta.getPersistentDataContainer().set(
    brewingSlot0Key, 
    PersistentDataType.STRING, 
    json
);
```

**Deserialization**:
```java
// Get JSON from NBT
String json = meta.getPersistentDataContainer().get(
    brewingSlot0Key, 
    PersistentDataType.STRING
);

// Deserialize ItemStack
Map<String, Object> map = gson.fromJson(json, Map.class);
ItemStack restored = ItemStack.deserialize(map);
```

## Event Flow

### Brewing Process Flow

```
1. Player places potions and redstone/glowstone in Master Brewing Stand
2. Vanilla brewing begins
3. BrewEvent fires (EventPriority.LOWEST)
4. onBrew() checks if brewing stand is a Master Brewing Stand
5. If not master stand → allow vanilla brewing
6. If master stand:
   a. Save current potion states
   b. Cancel vanilla brewing event
   c. Schedule restoration task for next tick
   d. Restore original potions
   e. Call processMasterBrew()
7. processMasterBrew():
   a. Find all valid potions to upgrade
   b. Get cost from FIRST valid potion
   c. Check if sufficient materials
   d. Upgrade ALL potions
   e. Update NBT, lore, display name
   f. Consume materials
```

### Potion Consumption Flow

```
1. Player right-clicks with Master Potion
2. PlayerInteractEvent fires (EventPriority.LOWEST)
3. onPlayerInteract() checks if item is Master Potion
4. If not master potion → allow vanilla behavior
5. If master potion:
   a. Cancel vanilla consumption event
   b. Read effect data from NBT
   c. Add/replace effect in activeMasterEffects
   d. Apply effect or enable flight
   e. Reduce potion count by 1
   f. Return empty bottle
   g. Send confirmation message
```

### Effect Refresh Flow

```
1. Background task runs every 60 ticks (3 seconds)
2. Get current time
3. For each player UUID in activeMasterEffects:
   a. Skip if player offline
   b. Get effect list
   c. For each effect:
      i. Calculate remaining time
      ii. If expired → remove and cleanup
      iii. If warning time → send message
      iv. If fly → maintain flight state
      v. If vanilla → restore if missing
   d. Remove player if no effects left
```

## NBT Data Format

### Master Brewing Stand Item

```
BrewingStand Item
└── ItemMeta
    ├── DisplayName: "Master Brewing Stand" (gold, bold)
    ├── Lore: [description lines]
    └── PersistentDataContainer
        ├── masterbrewing:master_brewing_stand: 1 (BYTE)
        ├── masterbrewing:brewing_slot_0: "{...json...}" (STRING)
        ├── masterbrewing:brewing_slot_1: "{...json...}" (STRING)
        ├── masterbrewing:brewing_slot_2: "{...json...}" (STRING)
        ├── masterbrewing:brewing_slot_3: "{...json...}" (STRING)
        ├── masterbrewing:brewing_slot_4: "{...json...}" (STRING)
        └── masterbrewing:brewing_fuel_level: 20 (INTEGER)
```

### Master Brewing Stand Block

```
BrewingStand Block Entity
└── PersistentDataContainer
    └── masterbrewing:master_brewing_stand: 1 (BYTE)
```

### Master Potion Item

```
Potion Item
└── PotionMeta
    ├── DisplayName: "<Effect> <Roman>" (gold, italic)
    ├── Lore: [stats, costs]
    ├── BasePotionType: WATER (clears vanilla text)
    ├── Color: <effect-specific RGB>
    ├── CustomEffects: [PotionEffect(...)]
    └── PersistentDataContainer
        ├── masterbrewing:master_potion: 1 (BYTE)
        ├── masterbrewing:potion_time_level: 3 (INTEGER)
        ├── masterbrewing:potion_power_level: 5 (INTEGER)
        ├── masterbrewing:potion_duration: 1080 (INTEGER)
        └── masterbrewing:potion_effect_type: "speed" (STRING)
```

## Configuration System

### UpgradePath Class

```java
private static class UpgradePath {
    Map<Integer, int[]> timeUpgrades;    // level -> [cost, duration]
    Map<Integer, Integer> powerUpgrades; // level -> cost
    int maxTimeLevel;
    int maxPowerLevel;
    
    UpgradePath(Map<Integer, int[]> time, Map<Integer, Integer> power) {
        this.timeUpgrades = time;
        this.powerUpgrades = power;
        this.maxTimeLevel = time.isEmpty() ? 0 : Collections.max(time.keySet());
        this.maxPowerLevel = power.isEmpty() ? 0 : Collections.max(power.keySet());
    }
}
```

### Configuration Flow

```
config.yml
    ↓
loadUpgradeTiers()
    ↓
Parse "upgrade-time" → timeUpgrades (TreeMap)
Parse "upgrade-power" → powerUpgrades (TreeMap)
Calculate maxTimeLevel, maxPowerLevel
    ↓
For each config key:
    ↓
    Check if valid potion name
        ↓
        Load <potion>.upgrade-time or use default
        Load <potion>.upgrade-power or use default
            ↓
            Create UpgradePath
            Store in potionUpgradePaths map
```

### Getter Methods

```java
// Returns appropriate upgrade map (custom or default)
private Map<Integer, int[]> getTimeUpgrades(String effectKey) {
    if (potionUpgradePaths.containsKey(effectKey)) {
        return potionUpgradePaths.get(effectKey).timeUpgrades;
    }
    return timeUpgrades; // default
}
```

## Extension Points

### Adding New Potion Types

To add a new custom potion effect (like fly or fortune):

1. **Add to name map**:
```java
private static Map<String, String> createPotionNameMap() {
    Map<String, String> map = new HashMap<>();
    // ... existing entries
    map.put("myeffect", "myeffect");
    return Collections.unmodifiableMap(map);
}
```

2. **Handle in effect refresh task**:
```java
if (effect.effectTypeKey.equals("myeffect")) {
    // Custom logic for your effect
    // Apply effect, maintain state, etc.
}
```

3. **Handle in potion consumption**:
```java
if (effectTypeKey.equals("myeffect")) {
    // Apply custom effect
    player.doSomething();
}
```

4. **Add lore generation**:
```java
if (effectTypeKey.equals("myeffect")) {
    // Custom lore showing effect stats
    lore.add(Component.text("My Stat: " + value, NamedTextColor.AQUA)
        .decoration(TextDecoration.ITALIC, false));
}
```

5. **Add color**:
```java
if (effectTypeKey.equals("myeffect")) {
    meta.setColor(org.bukkit.Color.fromRGB(0xRRGGBB));
}
```

### Modifying Upgrade Logic

To change how upgrades work:

**Location**: `processMasterBrew(BrewerInventory, ItemStack, boolean, boolean)`

**Current logic**:
- Cost determined by FIRST valid potion
- ALL potions upgraded together
- Materials consumed based on single cost

**To change**:
- Modify the cost calculation loop
- Change material consumption logic
- Update potion validation

### Adding New Commands

1. **Add case in onCommand**:
```java
case "mycommand":
    return handleMyCommand(sender, args);
```

2. **Implement handler**:
```java
private boolean handleMyCommand(CommandSender sender, String[] args) {
    // Permission check
    if (!sender.hasPermission("masterbrewing.mycommand")) {
        sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
        return true;
    }
    
    // Command logic
    // ...
    
    return true;
}
```

3. **Add tab completion**:
```java
if (args.length == 1) {
    completions.add("mycommand");
}
```

4. **Update plugin.yml**:
```yaml
commands:
  masterbrewing:
    description: MasterBrewing commands
    usage: /<command> [help|give|reload|mycommand]
```

## Performance Optimizations

### 1. Static Lookup Maps

**Instead of**:
```java
// O(n) lookup via switch statement
switch(potionName) {
    case "healing": return "instant_health";
    case "harming": return "instant_damage";
    // ... 30+ cases
}
```

**Use**:
```java
// O(1) lookup via HashMap
String effectKey = POTION_NAME_TO_EFFECT_KEY.get(potionName);
```

**Benefit**: Constant-time lookups vs linear search

### 2. Reduced Refresh Frequency

**Configuration**:
```java
Bukkit.getScheduler().runTaskTimer(this, () -> {
    // Effect refresh logic
}, 60L, 60L); // Every 3 seconds, not every second
```

**Benefit**: 66% reduction in task executions

### 3. Conditional Effect Restoration

**Only restore if**:
- Effect is completely missing
- Time remaining < 30 seconds

**Don't restore if**:
- Effect exists with any duration
- Time remaining > 30 seconds

**Benefit**: Prevents conflicts with other plugins

### 4. Early Bailout Checks

```java
// Bail out early if not a master brewing stand
if (!brewingStand.getPersistentDataContainer().has(
        masterBrewingStandKey, PersistentDataType.BYTE)) {
    return; // Fast exit
}

// Only process relevant inventory types
if (event.getInventory().getType() != InventoryType.BREWING) {
    return; // Fast exit
}
```

**Benefit**: Avoid unnecessary processing

### 5. TreeMap for Sorted Access

```java
// Use TreeMap for upgrade levels (maintains sorted order)
private Map<Integer, int[]> timeUpgrades = new TreeMap<>();

// Easy to find max level
int maxLevel = Collections.max(timeUpgrades.keySet());
```

**Benefit**: Efficient range queries and max finding

## Building and Development

### Requirements

- **JDK**: Java 21+
- **Build Tool**: Maven (pom.xml required)
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code

### Dependencies

```xml
<dependencies>
    <!-- Paper API -->
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.21-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- Gson for JSON serialization -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.10.1</version>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

### Project Structure

```
MasterBrewing/
├── src/main/java/com/supafloof/masterbrewing/
│   └── MasterBrewing.java
├── src/main/resources/
│   ├── plugin.yml
│   └── config.yml
├── pom.xml
└── README.md
```

### plugin.yml

```yaml
name: MasterBrewing
version: 1.0.0
main: com.supafloof.masterbrewing.MasterBrewing
api-version: 1.21
author: SupaFloof Games, LLC

commands:
  masterbrewing:
    description: MasterBrewing commands
    usage: /<command> [help|give|reload]
    aliases: [mb, masterbrew]

permissions:
  masterbrewing.give:
    description: Give Master Brewing items
    default: op
  masterbrewing.admin:
    description: Admin commands
    default: op
```

### Build Commands

```bash
# Clean build
mvn clean package

# Skip tests
mvn clean package -DskipTests

# Install to local repository
mvn clean install
```

### Debugging

**Enable debug logging**:
```java
getLogger().info("Debug: Processing brew for " + potion.getType());
```

**Use Bukkit scheduler debugging**:
```java
Bukkit.getScheduler().runTaskLater(this, () -> {
    getLogger().info("Task executed after delay");
}, 1L);
```

**Monitor event priority**:
```java
@EventHandler(priority = EventPriority.MONITOR)
public void debugEvent(BrewEvent event) {
    getLogger().info("Brew event fired: " + event.getBlock().getLocation());
}
```

### Testing

**Manual testing checklist**:
- [ ] Place Master Brewing Stand
- [ ] Brew vanilla potion
- [ ] Upgrade with redstone
- [ ] Upgrade with glowstone
- [ ] Consume master potion
- [ ] Verify effect persistence after logout
- [ ] Break and replace brewing stand
- [ ] Test with multiple potions
- [ ] Test at max upgrade levels
- [ ] Test with insufficient materials
- [ ] Test config reload
- [ ] Test all commands
- [ ] Test tab completion

## Code Standards

### SupaFloof Games Standards

1. **Single-file design**: Keep all code in one file
2. **Extensive JavaDoc**: Every method documented
3. **Console messages**: Green for startup, magenta for author
4. **Package structure**: `com.supafloof.<pluginname>`
5. **Formatting**: Consistent spacing and indentation

### Best Practices

- Use `NamespacedKey` for all NBT data
- Prefer `Component` over legacy strings for messages
- Use `TreeMap` for sorted key-value pairs
- Cache expensive lookups in static maps
- Always validate input in commands
- Handle edge cases (null checks, bounds checking)
- Use early returns to reduce nesting

## API Usage

### Adventure Text Components

```java
// Basic message
Component msg = Component.text("Hello!", NamedTextColor.GREEN);

// Chained components
Component complex = Component.text("You received ", NamedTextColor.GREEN)
    .append(Component.text("Master Potion", NamedTextColor.GOLD))
    .append(Component.text("!", NamedTextColor.GREEN));

// With decorations
Component decorated = Component.text("Title", NamedTextColor.GOLD, TextDecoration.BOLD)
    .decoration(TextDecoration.ITALIC, false); // Disable italic
```

### Persistent Data Container

```java
// Store data
meta.getPersistentDataContainer().set(
    key,                      // NamespacedKey
    PersistentDataType.INTEGER, // Data type
    value                     // Value
);

// Retrieve data
int value = meta.getPersistentDataContainer().get(
    key,
    PersistentDataType.INTEGER
);

// Retrieve with default
int value = meta.getPersistentDataContainer().getOrDefault(
    key,
    PersistentDataType.INTEGER,
    0 // default value
);

// Check existence
boolean has = meta.getPersistentDataContainer().has(
    key,
    PersistentDataType.INTEGER
);
```

### Paper Scheduler

```java
// Run task later (after delay)
Bukkit.getScheduler().runTaskLater(plugin, () -> {
    // Code to run
}, 20L); // 20 ticks = 1 second

// Run repeating task
Bukkit.getScheduler().runTaskTimer(plugin, () -> {
    // Code to run
}, 0L, 20L); // Initial delay, repeat interval

// Run async (don't touch Bukkit API!)
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    // Heavy computation only
});
```

## Troubleshooting Development Issues

### ClassNotFoundException
- Check Paper API version matches server version
- Ensure dependencies are in pom.xml
- Verify package names in plugin.yml

### NBT Data Not Persisting
- Ensure `meta.setItemMeta()` or `blockState.update()` is called
- Check key is registered in `onEnable()`
- Verify data type matches (INTEGER, STRING, BYTE, etc.)

### Events Not Firing
- Check event priority (LOWEST runs first, HIGHEST runs last)
- Ensure plugin is registered as listener
- Verify event is not cancelled by another plugin

### Memory Leaks
- Always remove from maps when no longer needed
- Clear lists when appropriate
- Don't store Player objects, use UUID

## Conclusion

MasterBrewing is a well-structured, performant plugin following SupaFloof Games' development standards. The single-file architecture makes it easy to understand and modify, while extensive documentation ensures maintainability.

For further assistance, refer to the comprehensive inline comments in the source code.

Happy Developing! 💻✨
