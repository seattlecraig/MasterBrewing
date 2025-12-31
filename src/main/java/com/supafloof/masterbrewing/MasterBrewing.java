package com.supafloof.masterbrewing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import com.google.gson.Gson;

/**
 * MasterBrewing Plugin
 * 
 * Creates special Master Brewing Stands that support unlimited potion upgrades.
 * Re-brew potions with redstone to increase duration without changing power.
 * Re-brew potions with glowstone to increase power without changing duration.
 * 
 * Features:
 * - Special brewing stands with custom NBT tags
 * - Configurable upgrade tiers for duration (redstone) and power (glowstone)
 * - Supports unlimited levels based on config (max 64 redstone/glowstone per upgrade)
 * - Master potions display with gold/orange italic names
 * - Master potions show effect descriptions in lore
 * - Master potions activate instantly on right-click (no drinking animation)
 * - Continuous effect refresh system prevents premature expiration
 * - Persistent storage of active effects across logouts/server restarts
 * - Tracks current time level and power level via NBT on potions
 * - Admin commands to give special brewing stands
 * - Prevents vanilla brewing mechanics from interfering with master brews
 * 
 * Config Format:
 * upgrade-time: level,redstone-cost,duration-seconds
 * upgrade-power: level,glowstone-cost
 * 
 * @author SupaFloof Games, LLC
 * @version 1.0.0
 */
public class MasterBrewing extends JavaPlugin implements Listener, TabCompleter {
    
    // Persistent data keys
    private NamespacedKey masterBrewingStandKey;
    private NamespacedKey potionTimeLevelKey;
    private NamespacedKey potionPowerLevelKey;
    private NamespacedKey potionDurationKey;
    private NamespacedKey potionEffectTypeKey;
    private NamespacedKey masterPotionKey;
    
    // Brewing stand state keys
    private NamespacedKey brewingSlot0Key;
    private NamespacedKey brewingSlot1Key;
    private NamespacedKey brewingSlot2Key;
    private NamespacedKey brewingSlot3Key;
    private NamespacedKey brewingSlot4Key;
    private NamespacedKey brewingFuelLevelKey;
    
    // Track active master potion effects per player
    // Map: Player UUID -> List of active effects with expiry times
    private Map<UUID, List<ActiveMasterEffect>> activeMasterEffects = new HashMap<>();
    
    // Track virtual master brewing stand inventories opened via command
    // Map: Player UUID -> Inventory (tracks which player has which virtual stand open)
    private Map<UUID, org.bukkit.inventory.Inventory> virtualBrewingStands = new HashMap<>();
    
    // Fuel level tracking for virtual brewing stands (1 blaze powder = 20 fuel)
    // Only holds data for CURRENTLY OPEN stands - loaded from disk on open, saved on close
    // Map: Player UUID -> fuel level remaining
    private Map<UUID, Integer> virtualBrewingFuel = new HashMap<>();
    
    // Configuration: upgrade tiers
    // Map: level -> [redstone cost, duration in seconds]
    private Map<Integer, int[]> timeUpgrades = new TreeMap<>();
    
    // Map: level -> glowstone cost
    private Map<Integer, Integer> powerUpgrades = new TreeMap<>();
    
    // Per-potion upgrade paths (effect key -> UpgradePath)
    // If a potion isn't in this map, use the default timeUpgrades/powerUpgrades
    private Map<String, UpgradePath> potionUpgradePaths = new HashMap<>();
    
    // Max levels based on config
    private int maxTimeLevel = 0;
    private int maxPowerLevel = 0;
    
    // OPTIMIZATION #3: Static maps for O(1) potion name lookup instead of O(n) switch
    private static final Map<String, String> POTION_NAME_TO_EFFECT_KEY = createPotionNameMap();
    
    // Instant effects that have no duration (cannot be upgraded with redstone)
    private static final Set<String> INSTANT_EFFECTS = createInstantEffectsSet();
    
    /**
     * Creates the set of instant effect keys (effects with no duration)
     * These effects cannot be upgraded with redstone (time upgrades)
     */
    private static Set<String> createInstantEffectsSet() {
        Set<String> set = new HashSet<>();
        set.add("instant_health");
        set.add("instant_damage");
        set.add("saturation");
        return Collections.unmodifiableSet(set);
    }
    
    /**
     * Checks if an effect type is an instant effect (no duration)
     * Instant effects cannot be upgraded with redstone for longer duration
     * 
     * @param effectKey The effect identifier (e.g., "instant_health", "speed")
     * @return true if the effect is instant and has no duration
     */
    private static boolean isInstantEffect(String effectKey) {
        return effectKey != null && INSTANT_EFFECTS.contains(effectKey);
    }
    
    /**
     * Creates the potion name to effect key mapping
     */
    private static Map<String, String> createPotionNameMap() {
        Map<String, String> map = new HashMap<>();
        // Custom potions
        map.put("fortune", "fortune");
        map.put("fly", "fly");
        // Standard potions - map special cases
        map.put("speed", "speed");
        map.put("slowness", "slowness");
        map.put("haste", "haste");
        map.put("mining_fatigue", "mining_fatigue");
        map.put("strength", "strength");
        map.put("healing", "instant_health");
        map.put("harming", "instant_damage");
        map.put("leaping", "jump_boost");
        map.put("nausea", "nausea");
        map.put("regeneration", "regeneration");
        map.put("resistance", "resistance");
        map.put("fire_resistance", "fire_resistance");
        map.put("water_breathing", "water_breathing");
        map.put("invisibility", "invisibility");
        map.put("blindness", "blindness");
        map.put("night_vision", "night_vision");
        map.put("hunger", "hunger");
        map.put("weakness", "weakness");
        map.put("poison", "poison");
        map.put("wither", "wither");
        map.put("health_boost", "health_boost");
        map.put("absorption", "absorption");
        map.put("saturation", "saturation");
        map.put("glowing", "glowing");
        map.put("levitation", "levitation");
        map.put("luck", "luck");
        map.put("unluck", "unluck");
        map.put("slow_falling", "slow_falling");
        map.put("conduit_power", "conduit_power");
        map.put("dolphins_grace", "dolphins_grace");
        map.put("bad_omen", "bad_omen");
        map.put("hero_of_the_village", "hero_of_the_village");
        map.put("darkness", "darkness");
        return Collections.unmodifiableMap(map);
    }
    
    /**
     * Inner class to hold upgrade paths for a specific potion type
     */
    private static class UpgradePath {
        Map<Integer, int[]> timeUpgrades; // level -> [redstone cost, duration seconds]
        Map<Integer, Integer> powerUpgrades; // level -> glowstone cost
        int maxTimeLevel;
        int maxPowerLevel;
        
        UpgradePath(Map<Integer, int[]> timeUpgrades, Map<Integer, Integer> powerUpgrades) {
            this.timeUpgrades = timeUpgrades;
            this.powerUpgrades = powerUpgrades;
            this.maxTimeLevel = timeUpgrades.isEmpty() ? 0 : Collections.max(timeUpgrades.keySet());
            this.maxPowerLevel = powerUpgrades.isEmpty() ? 0 : Collections.max(powerUpgrades.keySet());
        }
    }
    
    /**
     * Inner class to track active master potion effects
     */
    private static class ActiveMasterEffect {
        String effectTypeKey; // Effect type key (e.g. "speed", "strength", "fly")
        int amplifier;
        long expiryTime; // System time in milliseconds when effect expires
        
        ActiveMasterEffect(String effectTypeKey, int amplifier, long expiryTime) {
            this.effectTypeKey = effectTypeKey;
            this.amplifier = amplifier;
            this.expiryTime = expiryTime;
        }
    }
    
    /**
     * PLUGIN LIFECYCLE: Called when the plugin is enabled (server startup or /reload)
     * 
     * This method initializes all plugin systems in a specific order:
     * 1. Display startup messages (branding)
     * 2. Initialize NBT keys (required before any NBT operations)
     * 3. Load configuration from config.yml
     * 4. Register event listeners and command handlers
     * 5. Start background tasks
     * 6. Load persistent data for online players
     * 
     * The initialization order is critical - changing it may cause errors!
     */
    @Override
    public void onEnable() {
        // ===== STEP 1: Display startup messages =====
        // These messages appear in the console when the server starts
        // Green = success message, Purple = attribution
        getServer().getConsoleSender().sendMessage(
            Component.text("[MasterBrewing] MasterBrewing Started!", NamedTextColor.GREEN)
        );
        getServer().getConsoleSender().sendMessage(
            Component.text("[MasterBrewing] By SupaFloof Games, LLC", NamedTextColor.LIGHT_PURPLE)
        );
        
        // ===== STEP 2: Initialize persistent data keys =====
        // These NamespacedKey objects are used to store custom NBT data on items and blocks
        // Format: "masterbrewing:key_name" ensures uniqueness across all plugins
        // Must be initialized before any code tries to read/write NBT data
        
        // Master Brewing Stand marker - identifies special brewing stands
        masterBrewingStandKey = new NamespacedKey(this, "master_brewing_stand");
        
        // Master Potion NBT keys - track upgrade levels and effect data
        potionTimeLevelKey = new NamespacedKey(this, "potion_time_level");      // Duration upgrade level (0-based)
        potionPowerLevelKey = new NamespacedKey(this, "potion_power_level");    // Amplifier upgrade level (0-based)
        potionDurationKey = new NamespacedKey(this, "potion_duration");          // Total duration in seconds
        potionEffectTypeKey = new NamespacedKey(this, "potion_effect_type");    // Effect identifier (e.g., "speed", "fly")
        masterPotionKey = new NamespacedKey(this, "master_potion");              // Boolean marker for master potions
        
        // Brewing Stand inventory persistence keys - save/restore stand contents when broken/placed
        // These keys store serialized ItemStacks as Base64 strings
        brewingSlot0Key = new NamespacedKey(this, "brewing_slot_0");  // Left potion slot
        brewingSlot1Key = new NamespacedKey(this, "brewing_slot_1");  // Middle potion slot
        brewingSlot2Key = new NamespacedKey(this, "brewing_slot_2");  // Right potion slot
        brewingSlot3Key = new NamespacedKey(this, "brewing_slot_3");  // Ingredient slot (top center)
        brewingSlot4Key = new NamespacedKey(this, "brewing_slot_4");  // Fuel slot (blaze powder)
        brewingFuelLevelKey = new NamespacedKey(this, "brewing_fuel_level");  // Remaining fuel charges
        
        // ===== STEP 3: Load configuration =====
        // saveDefaultConfig() creates config.yml from the plugin jar if it doesn't exist
        // This ensures first-time users have a working config without manual setup
        saveDefaultConfig();
        
        // loadUpgradeTiers() parses the config.yml and builds the upgrade path maps
        // This must happen before any brewing events can be processed
        loadUpgradeTiers();
        
        // ===== STEP 4: Register event listeners and commands =====
        // Event listeners allow us to react to player actions (brewing, clicking, etc.)
        getServer().getPluginManager().registerEvents(this, this);
        
        // Command handlers process /masterbrewing commands
        // Tab completer provides auto-completion suggestions
        getCommand("masterbrewing").setExecutor(this);
        getCommand("masterbrewing").setTabCompleter(this);
        
        // ===== STEP 5: Start background tasks =====
        // This task runs continuously to refresh active potion effects on players
        // Required because our effects bypass vanilla potion duration limits
        startMasterPotionEffectTask();
        
        // ===== STEP 6: Load persistent data for online players =====
        // This handles the case where plugin is reloaded while players are online
        // We need to restore their active effects from disk
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerEffects(player.getUniqueId());
        }
        
        // Create playerdata directory for virtual brewing stand data
        File playerDataDir = new File(getDataFolder(), "playerdata");
        if (!playerDataDir.exists()) {
            playerDataDir.mkdirs();
        }
        
        // Final success message
        getLogger().info("MasterBrewing plugin enabled!");
    }
    
    /**
     * PLUGIN LIFECYCLE: Called when the plugin is disabled (server shutdown or /reload)
     * 
     * This method ensures data persistence by saving all active effects to disk.
     * Critical for preventing data loss when server shuts down unexpectedly.
     * 
     * Process:
     * 1. Iterate through all players with active effects
     * 2. Save each player's effect data to individual files
     * 3. Log shutdown message
     * 
     * Note: We do NOT clear the activeMasterEffects map here because if this
     * is a plugin reload (not shutdown), we want to keep effects in memory.
     */
    @Override
    public void onDisable() {
        // Save all active player effects to disk for persistence
        // This prevents data loss if the server crashes or is force-killed
        for (UUID uuid : activeMasterEffects.keySet()) {
            List<ActiveMasterEffect> effects = activeMasterEffects.get(uuid);
            savePlayerEffects(uuid, effects);
        }
        
        // Save any currently open virtual brewing stands
        for (Map.Entry<UUID, org.bukkit.inventory.Inventory> entry : virtualBrewingStands.entrySet()) {
            UUID playerUUID = entry.getKey();
            org.bukkit.inventory.Inventory inv = entry.getValue();
            
            ItemStack[] contents = new ItemStack[inv.getSize()];
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item != null) {
                    contents[i] = item.clone();
                }
            }
            
            int fuelLevel = virtualBrewingFuel.getOrDefault(playerUUID, 0);
            savePlayerBrewingData(playerUUID, contents, fuelLevel);
        }
        
        getLogger().info("MasterBrewing plugin disabled!");
    }
    
    /**
     * CONFIGURATION LOADING: Parses config.yml and builds upgrade tier maps
     * 
     * This method handles two types of upgrade configurations:
     * 
     * 1. GLOBAL UPGRADES (apply to all potions by default)
     *    upgrade-time: List of "level,redstone-cost,duration-seconds"
     *    upgrade-power: List of "level,glowstone-cost"
     * 
     * 2. PER-POTION OVERRIDES (specific costs/durations for certain effects)
     *    <effect-name>:
     *      upgrade-time: List of "level,cost,duration"
     *      upgrade-power: List of "level,cost"
     * 
     * Example config:
     *   upgrade-time:
     *     - "1,4,600"    # Level 1: 4 redstone = 10 minutes
     *     - "2,8,1200"   # Level 2: 8 redstone = 20 minutes
     *   
     *   upgrade-power:
     *     - "1,4"        # Level 1: 4 glowstone
     *     - "2,8"        # Level 2: 8 glowstone
     *   
     *   speed:           # Speed potions have custom costs
     *     upgrade-time:
     *       - "1,2,300"  # Cheaper/shorter than default
     * 
     * The method also calculates maxTimeLevel and maxPowerLevel by finding
     * the highest level across all configurations (global + per-potion).
     * 
     * Error Handling:
     * - Invalid format entries are logged and skipped
     * - Missing sections result in empty maps (safe defaults)
     * - Parsing errors don't crash the plugin
     */
    private void loadUpgradeTiers() {
        // Clear existing configurations to prevent stale data on /reload
        timeUpgrades.clear();
        powerUpgrades.clear();
        maxTimeLevel = 0;
        maxPowerLevel = 0;
        
        FileConfiguration config = getConfig();
        List<String> timeConfigs = config.getStringList("upgrade-time");
        List<String> powerConfigs = config.getStringList("upgrade-power");
        
        // ===== LOAD GLOBAL TIME UPGRADES =====
        // Format: "level,redstone-cost,duration-seconds"
        // These upgrades apply to all potions unless overridden
        for (String entry : timeConfigs) {
            try {
                // Split CSV format: "1,4,600" -> ["1", "4", "600"]
                String[] parts = entry.split(",");
                if (parts.length != 3) {
                    getLogger().warning("Invalid upgrade-time entry: " + entry);
                    continue;
                }
                
                // Parse each component
                int level = Integer.parseInt(parts[0].trim());          // Upgrade level (1, 2, 3...)
                int redstoneCost = Integer.parseInt(parts[1].trim());   // How many redstone dust needed
                int duration = Integer.parseInt(parts[2].trim());       // Duration in seconds
                
                // Store in map: level -> [cost, duration]
                timeUpgrades.put(level, new int[]{redstoneCost, duration});
                
                // Track maximum level across all upgrades
                maxTimeLevel = Math.max(maxTimeLevel, level);
            } catch (Exception e) {
                // Log and skip invalid entries rather than crashing
                getLogger().warning("Failed to parse upgrade-time entry: " + entry);
            }
        }
        
        // ===== LOAD GLOBAL POWER UPGRADES =====
        // Format: "level,glowstone-cost"
        // These upgrades apply to all potions unless overridden
        for (String entry : powerConfigs) {
            try {
                // Split CSV format: "1,4" -> ["1", "4"]
                String[] parts = entry.split(",");
                if (parts.length != 2) {
                    getLogger().warning("Invalid upgrade-power entry: " + entry);
                    continue;
                }
                
                // Parse each component
                int level = Integer.parseInt(parts[0].trim());         // Upgrade level (1, 2, 3...)
                int glowstoneCost = Integer.parseInt(parts[1].trim()); // How many glowstone dust needed
                
                // Store in map: level -> cost
                // Power upgrades don't have a duration component (only affects amplifier)
                powerUpgrades.put(level, glowstoneCost);
                
                // Track maximum level across all upgrades
                maxPowerLevel = Math.max(maxPowerLevel, level);
            } catch (Exception e) {
                // Log and skip invalid entries rather than crashing
                getLogger().warning("Failed to parse upgrade-power entry: " + entry);
            }
        }
        
        // ===== LOAD PER-POTION OVERRIDE CONFIGURATIONS =====
        // This section allows admins to customize costs for specific potion types
        // For example: speed potions might be cheaper than strength potions
        potionUpgradePaths.clear();
        Set<String> configKeys = config.getKeys(false); // Get top-level keys
        
        for (String key : configKeys) {
            // Skip the default upgrade sections (already processed above)
            if (key.equals("upgrade-time") || key.equals("upgrade-power")) {
                continue;
            }
            
            // Check if this key corresponds to a valid potion effect
            // POTION_NAME_TO_EFFECT_KEY contains all valid potion names
            if (!POTION_NAME_TO_EFFECT_KEY.containsKey(key)) {
                continue;  // Not a potion name, skip
            }
            
            // Get the internal effect key (e.g., "speed" -> "speed", "healing" -> "instant_health")
            String effectKey = POTION_NAME_TO_EFFECT_KEY.get(key);
            
            // ===== Load this potion's custom time upgrades =====
            Map<Integer, int[]> potionTimeUpgrades = new TreeMap<>();
            if (config.contains(key + ".upgrade-time")) {
                // Potion has custom time upgrades defined
                List<String> potionTimeConfigs = config.getStringList(key + ".upgrade-time");
                for (String entry : potionTimeConfigs) {
                    try {
                        String[] parts = entry.split(",");
                        if (parts.length != 3) {
                            getLogger().warning("Invalid " + key + ".upgrade-time entry: " + entry);
                            continue;
                        }
                        
                        int level = Integer.parseInt(parts[0].trim());
                        int redstoneCost = Integer.parseInt(parts[1].trim());
                        int duration = Integer.parseInt(parts[2].trim());
                        
                        potionTimeUpgrades.put(level, new int[]{redstoneCost, duration});
                    } catch (Exception e) {
                        getLogger().warning("Failed to parse " + key + ".upgrade-time entry: " + entry);
                    }
                }
            } else {
                // No custom time upgrades - use global defaults
                potionTimeUpgrades.putAll(timeUpgrades);
            }
            
            // ===== Load this potion's custom power upgrades =====
            Map<Integer, Integer> potionPowerUpgrades = new TreeMap<>();
            if (config.contains(key + ".upgrade-power")) {
                // Potion has custom power upgrades defined
                List<String> potionPowerConfigs = config.getStringList(key + ".upgrade-power");
                for (String entry : potionPowerConfigs) {
                    try {
                        String[] parts = entry.split(",");
                        if (parts.length != 2) {
                            getLogger().warning("Invalid " + key + ".upgrade-power entry: " + entry);
                            continue;
                        }
                        
                        int level = Integer.parseInt(parts[0].trim());
                        int glowstoneCost = Integer.parseInt(parts[1].trim());
                        
                        potionPowerUpgrades.put(level, glowstoneCost);
                    } catch (Exception e) {
                        getLogger().warning("Failed to parse " + key + ".upgrade-power entry: " + entry);
                    }
                }
            } else {
                // No custom power upgrades - use global defaults
                potionPowerUpgrades.putAll(powerUpgrades);
            }
            
            // Create and store the UpgradePath for this potion type
            // Only store if at least one upgrade type is defined
            if (!potionTimeUpgrades.isEmpty() || !potionPowerUpgrades.isEmpty()) {
                UpgradePath path = new UpgradePath(potionTimeUpgrades, potionPowerUpgrades);
                potionUpgradePaths.put(effectKey, path);
                getLogger().info("Loaded custom upgrade path for " + key + " (time levels: " + path.maxTimeLevel + ", power levels: " + path.maxPowerLevel + ")");
            }
        }
        
        // Log summary of loaded configuration
        getLogger().info("Loaded " + timeUpgrades.size() + " time upgrades (max level: " + maxTimeLevel + ")");
        getLogger().info("Loaded " + powerUpgrades.size() + " power upgrades (max level: " + maxPowerLevel + ")");
    }
    
    /**
     * UPGRADE PATH RESOLUTION: Gets the time upgrade configuration for a potion
     * 
     * This method implements a fallback system:
     * 1. First checks if the potion has a custom upgrade path defined
     * 2. If yes: returns the potion-specific configuration
     * 3. If no: returns the global default configuration
     * 
     * This allows admins to customize costs for specific potions while
     * maintaining reasonable defaults for all others.
     * 
     * Example:
     *   - Speed potions might have custom cheap upgrades
     *   - Unconfigured potions (like poison) use global defaults
     *   - Both work seamlessly without special handling
     * 
     * @param effectKey The effect identifier (e.g., "speed", "strength", "fly")
     * @return Map of level -> [redstone cost, duration in seconds]
     */
    private Map<Integer, int[]> getTimeUpgrades(String effectKey) {
        // Check if this potion type has a custom upgrade path
        if (potionUpgradePaths.containsKey(effectKey)) {
            return potionUpgradePaths.get(effectKey).timeUpgrades;
        }
        // Fall back to global defaults
        return timeUpgrades;
    }
    
    /**
     * UPGRADE PATH RESOLUTION: Gets the power upgrade configuration for a potion
     * 
     * Same fallback logic as getTimeUpgrades(), but for power (glowstone) upgrades.
     * 
     * Power upgrades increase the amplifier of the potion effect:
     * - Level 0: Amplifier 1 (e.g., Speed II)
     * - Level 1: Amplifier 2 (e.g., Speed III)
     * - Level 2: Amplifier 3 (e.g., Speed IV)
     * And so on, without vanilla's level limits.
     * 
     * @param effectKey The effect identifier (e.g., "speed", "strength", "fly")
     * @return Map of level -> glowstone cost
     */
    private Map<Integer, Integer> getPowerUpgrades(String effectKey) {
        // Check if this potion type has a custom upgrade path
        if (potionUpgradePaths.containsKey(effectKey)) {
            return potionUpgradePaths.get(effectKey).powerUpgrades;
        }
        // Fall back to global defaults
        return powerUpgrades;
    }
    
    /**
     * UPGRADE PATH RESOLUTION: Gets the maximum time level for a potion
     * 
     * Returns the highest achievable time (duration) upgrade level for the
     * specified potion effect. This respects potion-specific configurations.
     * 
     * Used for:
     * - Validating upgrade attempts (can't upgrade past max)
     * - Displaying "MAX" indicators in potion lore
     * - Tab completion suggestions in commands
     * 
     * @param effectKey The effect identifier (e.g., "speed", "strength")
     * @return Maximum time upgrade level for this potion
     */
    private int getMaxTimeLevel(String effectKey) {
        // Check if potion has custom upgrade path with its own max level
        if (potionUpgradePaths.containsKey(effectKey)) {
            return potionUpgradePaths.get(effectKey).maxTimeLevel;
        }
        // Fall back to global maximum
        return maxTimeLevel;
    }
    
    /**
     * UPGRADE PATH RESOLUTION: Gets the maximum power level for a potion
     * 
     * Returns the highest achievable power (amplifier) upgrade level for the
     * specified potion effect. This respects potion-specific configurations.
     * 
     * Used for:
     * - Validating upgrade attempts (can't upgrade past max)
     * - Displaying "MAX" indicators in potion lore
     * - Tab completion suggestions in commands
     * 
     * @param effectKey The effect identifier (e.g., "speed", "strength")
     * @return Maximum power upgrade level for this potion
     */
    private int getMaxPowerLevel(String effectKey) {
        // Check if potion has custom upgrade path with its own max level
        if (potionUpgradePaths.containsKey(effectKey)) {
            return potionUpgradePaths.get(effectKey).maxPowerLevel;
        }
        // Fall back to global maximum
        return maxPowerLevel;
    }
    
    /**
     * EFFECT MANAGEMENT: Starts the continuous effect refresh task
     * 
     * This is the heart of the master potion system. It maintains active effects
     * on players by periodically checking and refreshing them.
     * 
     * ==============================================================================
     * WHY IS THIS NEEDED?
     * ==============================================================================
     * Minecraft's vanilla potion system has hard limits:
     * - Maximum duration: 9 minutes 59 seconds (effic ticks: 19999)
     * - Maximum amplifier: Level 255
     * - No support for custom effects like flight or fortune
     * 
     * Master potions bypass these limits by:
     * 1. Storing effect data in our own tracking system (activeMasterEffects)
     * 2. Periodically reapplying effects before they expire
     * 3. Using custom handlers for non-vanilla effects (fly, fortune)
     * 
     * ==============================================================================
     * HOW IT WORKS
     * ==============================================================================
     * Every 3 seconds (60 ticks), this task:
     * 1. Iterates through all players with active master potion effects
     * 2. For each effect on each player:
     *    a. Checks if effect has expired (currentTime >= expiryTime)
     *    b. If expired: Removes effect and cleans up
     *    c. If active but close to expiring (<30s): Refreshes the effect
     *    d. If active with plenty of time: Skips refresh (optimization)
     * 3. Shows expiration warnings at 30s and 10s remaining
     * 4. Removes players from tracking map when all effects expire
     * 
     * ==============================================================================
     * OPTIMIZATIONS
     * ==============================================================================
     * - Runs every 3 seconds instead of every tick (reduces CPU by 60x)
     * - Only checks players in activeMasterEffects map (not all online players)
     * - Only refreshes effects within 30 seconds of expiring
     * - Uses cached Player lookups (Bukkit.getPlayer is relatively expensive)
     * - Skips offline players without repeatedly checking PlayerData
     * - Flight speed only updated when it differs from desired value
     * 
     * ==============================================================================
     * SPECIAL EFFECT HANDLING
     * ==============================================================================
     * 
     * FLY EFFECT:
     * - Managed via setAllowFlight() / setFlying() API calls
     * - Not a vanilla potion effect
     * - Speed scales with amplifier: base * (1.0 + amplifier * 0.2)
     * - Only affects survival/adventure mode (creative already has flight)
     * - Shows action bar with remaining time
     * - On expiry: Disables flight and resets speed to default
     * 
     * FORTUNE EFFECT:
     * - Implemented as vanilla LUCK potion effect
     * - Luck affects loot table drops (better loot from mobs/chests)
     * - Applied with our custom amplifier (beyond vanilla limits)
     * 
     * VANILLA EFFECTS:
     * - Applied with remaining duration (not full duration)
     * - Only reapplied if completely missing (avoids conflicts)
     * - Doesn't fight with other plugins' effect applications
     * 
     * ==============================================================================
     * THREADING
     * ==============================================================================
     * This task runs on the main Bukkit thread (runTaskTimer, not async).
     * Why? All Bukkit API calls (player.addPotionEffect, player.setAllowFlight)
     * must be on the main thread or they'll throw IllegalStateException.
     * 
     * Performance impact is minimal because:
     * - Only checks players with active effects (usually < 10 players)
     * - Only runs every 3 seconds
     * - Most operations are O(1) map lookups
     * 
     * ==============================================================================
     */
    private void startMasterPotionEffectTask() {
        // Schedule repeating task: delay 60 ticks (3s), period 60 ticks (3s)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // Get current time for expiry calculations
            // Uses System.currentTimeMillis() for precision (not affected by lag)
            long currentTime = System.currentTimeMillis();
            
            // OPTIMIZATION: Create snapshot of players to check
            // This prevents ConcurrentModificationException if map changes during iteration
            Set<UUID> playersToCheck = new HashSet<>(activeMasterEffects.keySet());
            
            // Process each player with active effects
            for (UUID uuid : playersToCheck) {
                // OPTIMIZATION: Get player once and reuse
                // Bukkit.getPlayer() lookups are relatively expensive
                Player player = Bukkit.getPlayer(uuid);
                
                // OPTIMIZATION: Skip offline players immediately
                // No point processing effects for players who aren't online
                if (player == null || !player.isOnline()) {
                    continue;  // Keep effects in map for when player returns
                }
                
                // Get this player's active effects list
                List<ActiveMasterEffect> effects = activeMasterEffects.get(uuid);
                if (effects == null || effects.isEmpty()) {
                    continue;  // Shouldn't happen, but safety check
                }
                
                // Track fly status for action bar display
                // Using arrays for lambda capture (final variables)
                final boolean[] hasActiveFly = {false};
                final String[] flyActionBar = {null};
                
                // Process all effects for this player
                // removeIf() iterates and removes expired effects in one pass
                effects.removeIf(effect -> {
                    // Calculate remaining time for this effect
                    long remainingMillis = effect.expiryTime - currentTime;
                    int remainingSeconds = (int) (remainingMillis / 1000);
                    
                    // ===== CHECK FOR EXPIRATION =====
                    if (currentTime >= effect.expiryTime) {
                        // Effect has expired - clean up and remove
                        
                        if (effect.effectTypeKey.equals("fly")) {
                            // Revoke flight permission
                            // Only affect survival/adventure mode (creative keeps flight)
                            if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || 
                                player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                                player.setAllowFlight(false);  // Disable flight ability
                                player.setFlying(false);       // Stop flying immediately
                                player.setFlySpeed(0.1f);      // Reset to vanilla default
                            }
                            // Notify player that flight ended
                            player.sendMessage(Component.text("Flight ended!", NamedTextColor.RED));
                        }
                        
                        return true; // Remove this effect from the list
                    }
                    
                    // ===== EXPIRATION WARNINGS =====
                    // Show warnings at 30 seconds and 10 seconds remaining
                    if (remainingSeconds == 30 || remainingSeconds == 10) {
                        // Format effect name for display
                        String effectName;
                        if (effect.effectTypeKey.equals("fly")) {
                            effectName = "Flight";
                        } else if (effect.effectTypeKey.equals("fortune")) {
                            effectName = "Fortune";
                        } else {
                            // Get vanilla effect display name
                            effectName = formatEffectName(PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effect.effectTypeKey)));
                        }
                        
                        // Send warning message
                        player.sendMessage(Component.text(effectName + " ending in " + remainingSeconds + " seconds!", 
                            NamedTextColor.YELLOW));
                    }
                    
                    // ===== HANDLE FLY EFFECT =====
                    if (effect.effectTypeKey.equals("fly")) {
                        hasActiveFly[0] = true;  // Mark that player has active flight
                        
                        // Maintain flight state (only if not creative/spectator)
                        if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || 
                            player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                            
                            // Ensure flight permission is enabled
                            if (!player.getAllowFlight()) {
                                player.setAllowFlight(true);
                            }
                            
                            // OPTIMIZATION: Only update flight speed if it changed
                            // Calculate desired speed based on amplifier
                            // Formula: base (0.1) * (1.0 + amplifier * 0.2)
                            // Example: Amplifier 5 = 0.1 * (1.0 + 5 * 0.2) = 0.1 * 2.0 = 0.2 (2x speed)
                            float desiredSpeed = 0.1f * (1.0f + (effect.amplifier * 0.2f));
                            desiredSpeed = Math.min(desiredSpeed, 1.0f);  // Cap at 1.0 (max flight speed)
                            float currentSpeed = player.getFlySpeed();
                            
                            // Only update if speed differs significantly (avoid floating point issues)
                            if (Math.abs(currentSpeed - desiredSpeed) > 0.001f) {
                                player.setFlySpeed(desiredSpeed);
                            }
                        }
                        
                        // Prepare action bar message showing flight level and remaining time
                        int displayLevel = effect.amplifier + 1;  // Convert 0-based to 1-based
                        String romanLevel = toRoman(displayLevel);  // Convert to Roman numerals (I, II, III...)
                        String durationStr = formatDuration(remainingSeconds);  // Format as "5m 30s"
                        flyActionBar[0] = "✈ Flight " + romanLevel + " • " + durationStr + " remaining";
                        
                        return false; // Keep effect in list
                    }
                    
                    // ===== OPTIMIZATION: Only refresh effects close to expiring =====
                    // If effect has > 30 seconds remaining, don't refresh yet
                    // This prevents constant reapplication and conflicts with other plugins
                    if (remainingSeconds > 30) {
                        return false; // Don't refresh yet, keep in list
                    }
                    
                    // ===== HANDLE VANILLA POTION EFFECTS =====
                    // Map internal effect keys to Minecraft's effect keys
                    String mappedEffectKey = effect.effectTypeKey;
                    if (effect.effectTypeKey.equals("fortune")) {
                        mappedEffectKey = "luck"; // Fortune uses LUCK potion effect internally
                    }
                    
                    // Get the PotionEffectType for this effect
                    PotionEffectType effectType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(mappedEffectKey));
                    if (effectType == null) {
                        // Invalid effect type - remove it
                        getLogger().warning("Invalid effect type: " + mappedEffectKey);
                        return true; // Remove from list
                    }
                    
                    // Check if player currently has this effect
                    PotionEffect currentEffect = player.getPotionEffect(effectType);
                    
                    // OPTIMIZATION: Only restore if effect is completely missing
                    // Don't fight with other plugins that may apply the same effect
                    // with different durations or amplifiers
                    if (currentEffect == null) {
                        // Effect is missing - reapply it with remaining duration
                        int durationTicks = remainingSeconds * 20;  // Convert seconds to ticks (20 ticks/second)
                        
                        // Create and apply potion effect
                        // Parameters: type, duration, amplifier, ambient, particles, icon
                        player.addPotionEffect(
                            new PotionEffect(
                                effectType,       // Effect type (SPEED, STRENGTH, etc.)
                                durationTicks,    // Duration in ticks
                                effect.amplifier, // Amplifier (0 = level I, 1 = level II, etc.)
                                false,            // Ambient (false = not from beacon)
                                true,             // Particles (true = show swirl particles)
                                true              // Icon (true = show in inventory)
                            ),
                            true // true = overwrite existing effect
                        );
                    }
                    // If effect exists with different parameters, let the other plugin manage it
                    // We only care that SOME version of the effect is active
                    
                    return false; // Keep effect in list
                });
                
                // ===== DISPLAY FLY ACTION BAR =====
                // Show flight status on action bar (text above hotbar)
                if (hasActiveFly[0] && flyActionBar[0] != null) {
                    player.sendActionBar(Component.text(flyActionBar[0], NamedTextColor.GOLD));
                }
                
                // ===== CLEANUP: Remove player if no effects remain =====
                if (effects.isEmpty()) {
                    activeMasterEffects.remove(uuid);
                    // This frees memory and prevents unnecessary iterations in future cycles
                }
            }
        }, 60L, 60L); // Run every 3 seconds (60 ticks)
    }
    
    /**
     * ITEM CREATION: Creates a Master Brewing Stand item
     * 
     * This method generates a brewing stand ItemStack with special properties:
     * 1. Custom display name (gold + bold)
     * 2. Informative lore describing functionality
     * 3. NBT marker identifying it as a master brewing stand
     * 
     * The NBT marker (masterBrewingStandKey) is critical - it's how we identify
     * master brewing stands throughout the plugin:
     * - onBlockPlace checks for it to mark placed blocks
     * - onBrewEvent checks brewing stands for this marker
     * - Commands use this to give special stands to players
     * 
     * Display:
     * - Name: "Master Brewing Stand" (gold, bold, no italic)
     * - Lore: Explains unlimited upgrades and usage
     * 
     * @return ItemStack of a master brewing stand ready to give to players
     */
    private ItemStack createMasterBrewingStand() {
        // Create base brewing stand item
        ItemStack brewingStand = new ItemStack(Material.BREWING_STAND);
        ItemMeta meta = brewingStand.getItemMeta();
        
        // Set custom display name
        // Gold color + Bold styling, italic explicitly disabled (Minecraft adds italic by default)
        meta.displayName(Component.text("Master Brewing Stand", NamedTextColor.GOLD, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        
        // Create informative lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A special brewing stand that allows", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("unlimited potion upgrades.", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("", NamedTextColor.GRAY));  // Blank line for spacing
        lore.add(Component.text("Use redstone to increase duration", NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Use glowstone to increase power", NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        
        meta.lore(lore);
        
        // CRITICAL: Mark item as a master brewing stand
        // This NBT tag is how we identify master stands throughout the plugin
        // Using BYTE type with value 1 (true)
        meta.getPersistentDataContainer().set(masterBrewingStandKey, PersistentDataType.BYTE, (byte) 1);
        
        // Apply metadata to item
        brewingStand.setItemMeta(meta);
        return brewingStand;
    }
    
    /**
     * UTILITY: Checks if an ItemStack is a Master Brewing Stand
     * 
     * This method performs a series of validation checks to determine if
     * a given item is a master brewing stand:
     * 1. Null check (item exists)
     * 2. Type check (is a BREWING_STAND material)
     * 3. Metadata check (has item metadata)
     * 4. NBT check (has our master brewing stand marker)
     * 
     * Used by:
     * - Event handlers to identify master stands
     * - Commands to validate items
     * - Any code that needs to distinguish master stands from normal stands
     * 
     * @param item The ItemStack to check
     * @return true if item is a master brewing stand, false otherwise
     */
    private boolean isMasterBrewingStand(ItemStack item) {
        // Null safety check
        if (item == null || item.getType() != Material.BREWING_STAND) {
            return false;  // Not a brewing stand at all
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        return meta.getPersistentDataContainer().has(masterBrewingStandKey, PersistentDataType.BYTE);
    }
    
    /**
     * Checks if a location has a Master Brewing Stand
     * OPTIMIZATION #1: Now uses string-based lookup
     */
    
    // ==================== EVENT HANDLERS ====================
    
    /**
     * Handles placing Master Brewing Stands
     * OPTIMIZATION #1: Now stores location as string
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        
        if (isMasterBrewingStand(item)) {
            Block block = event.getBlock();
            
            // Check if the item has saved brewing stand state
            ItemMeta itemMeta = item.getItemMeta();
            
            // Mark the brewing stand block itself with NBT and restore fuel level after 1 tick
            Bukkit.getScheduler().runTaskLater(this, () -> {
                // Verify the block is still a brewing stand
                if (block.getType() == Material.BREWING_STAND) {
                    BrewingStand brewingStand = (BrewingStand) block.getState();
                    
                    // Mark this block as a master brewing stand
                    brewingStand.getPersistentDataContainer().set(masterBrewingStandKey, PersistentDataType.BYTE, (byte) 1);
                    
                    // Restore fuel level to the state
                    if (itemMeta != null && itemMeta.getPersistentDataContainer().has(brewingFuelLevelKey, PersistentDataType.INTEGER)) {
                        int fuelLevel = itemMeta.getPersistentDataContainer().get(brewingFuelLevelKey, PersistentDataType.INTEGER);
                        brewingStand.setFuelLevel(fuelLevel);
                        getLogger().info("Restoring fuel level: " + fuelLevel);
                    }
                    
                    // Update the block state (writes NBT and fuel level)
                    brewingStand.update();
                    
                    // NOW restore inventory items after the state is updated
                    if (itemMeta != null) {
                        // Get a fresh reference to the inventory after updating
                        BrewingStand updatedStand = (BrewingStand) block.getState();
                        BrewerInventory inventory = updatedStand.getInventory();
                        
                        // Restore inventory slots
                        Gson gson = new Gson();
                        
                        getLogger().info("=== RESTORING BREWING STAND STATE ===");
                        getLogger().info("Has slot 0 data: " + itemMeta.getPersistentDataContainer().has(brewingSlot0Key, PersistentDataType.STRING));
                        getLogger().info("Has slot 1 data: " + itemMeta.getPersistentDataContainer().has(brewingSlot1Key, PersistentDataType.STRING));
                        getLogger().info("Has slot 2 data: " + itemMeta.getPersistentDataContainer().has(brewingSlot2Key, PersistentDataType.STRING));
                        getLogger().info("Has slot 3 data: " + itemMeta.getPersistentDataContainer().has(brewingSlot3Key, PersistentDataType.STRING));
                        getLogger().info("Has slot 4 data: " + itemMeta.getPersistentDataContainer().has(brewingSlot4Key, PersistentDataType.STRING));
                        
                        if (itemMeta.getPersistentDataContainer().has(brewingSlot0Key, PersistentDataType.STRING)) {
                            String json = itemMeta.getPersistentDataContainer().get(brewingSlot0Key, PersistentDataType.STRING);
                            getLogger().info("Slot 0 JSON length on restore: " + json.length());
                            try {
                                Map<String, Object> map = gson.fromJson(json, Map.class);
                                ItemStack restored = ItemStack.deserialize(map);
                                inventory.setItem(0, restored);
                                getLogger().info("Slot 0 restored: " + restored.getType());
                            } catch (Exception e) {
                                getLogger().warning("Failed to restore brewing stand slot 0: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        
                        if (itemMeta.getPersistentDataContainer().has(brewingSlot1Key, PersistentDataType.STRING)) {
                            String json = itemMeta.getPersistentDataContainer().get(brewingSlot1Key, PersistentDataType.STRING);
                            getLogger().info("Slot 1 JSON length on restore: " + json.length());
                            try {
                                Map<String, Object> map = gson.fromJson(json, Map.class);
                                ItemStack restored = ItemStack.deserialize(map);
                                inventory.setItem(1, restored);
                                getLogger().info("Slot 1 restored: " + restored.getType());
                            } catch (Exception e) {
                                getLogger().warning("Failed to restore brewing stand slot 1: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        
                        if (itemMeta.getPersistentDataContainer().has(brewingSlot2Key, PersistentDataType.STRING)) {
                            String json = itemMeta.getPersistentDataContainer().get(brewingSlot2Key, PersistentDataType.STRING);
                            getLogger().info("Slot 2 JSON length on restore: " + json.length());
                            try {
                                Map<String, Object> map = gson.fromJson(json, Map.class);
                                ItemStack restored = ItemStack.deserialize(map);
                                inventory.setItem(2, restored);
                                getLogger().info("Slot 2 restored: " + restored.getType());
                            } catch (Exception e) {
                                getLogger().warning("Failed to restore brewing stand slot 2: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        
                        if (itemMeta.getPersistentDataContainer().has(brewingSlot3Key, PersistentDataType.STRING)) {
                            String json = itemMeta.getPersistentDataContainer().get(brewingSlot3Key, PersistentDataType.STRING);
                            getLogger().info("Slot 3 JSON length on restore: " + json.length());
                            try {
                                Map<String, Object> map = gson.fromJson(json, Map.class);
                                ItemStack restored = ItemStack.deserialize(map);
                                inventory.setItem(3, restored);
                                getLogger().info("Slot 3 restored: " + restored.getType());
                            } catch (Exception e) {
                                getLogger().warning("Failed to restore brewing stand slot 3: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        
                        if (itemMeta.getPersistentDataContainer().has(brewingSlot4Key, PersistentDataType.STRING)) {
                            String json = itemMeta.getPersistentDataContainer().get(brewingSlot4Key, PersistentDataType.STRING);
                            getLogger().info("Slot 4 JSON length on restore: " + json.length());
                            try {
                                Map<String, Object> map = gson.fromJson(json, Map.class);
                                ItemStack restored = ItemStack.deserialize(map);
                                inventory.setItem(4, restored);
                                getLogger().info("Slot 4 restored: " + restored.getType());
                            } catch (Exception e) {
                                getLogger().warning("Failed to restore brewing stand slot 4: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }, 1L);
            
            event.getPlayer().sendMessage(Component.text("Master Brewing Stand placed!", NamedTextColor.GREEN));
        }
    }
    
    /**
     * Handles breaking Master Brewing Stands
     * Prevents duplicate drops when using SpecialBooks auto-pickup tools
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        
        if (block.getType() == Material.BREWING_STAND) {
            BrewingStand brewingStand = (BrewingStand) block.getState();
            
            // Check if this brewing stand is marked as a master brewing stand
            if (brewingStand.getPersistentDataContainer().has(masterBrewingStandKey, PersistentDataType.BYTE)) {
                // Get brewing stand state before breaking
                BrewerInventory inventory = brewingStand.getInventory();
                int fuelLevel = brewingStand.getFuelLevel();
                
                // Cancel event to prevent SpecialBooks from adding vanilla drop
                event.setCancelled(true);
                
                Player player = event.getPlayer();
                ItemStack tool = player.getInventory().getItemInMainHand();
                ItemStack masterStand = createMasterBrewingStand();
                
                // Save brewing stand state to the item's NBT
                ItemMeta standMeta = masterStand.getItemMeta();
                
                // Save each inventory slot (0-4: three potion slots, ingredient slot, fuel slot)
                Gson gson = new Gson();
                ItemStack slot0 = inventory.getItem(0);
                ItemStack slot1 = inventory.getItem(1);
                ItemStack slot2 = inventory.getItem(2);
                ItemStack slot3 = inventory.getItem(3);
                ItemStack slot4 = inventory.getItem(4);
                
                getLogger().info("=== SAVING BREWING STAND STATE ===");
                getLogger().info("Slot 0: " + (slot0 != null ? slot0.getType() : "null"));
                getLogger().info("Slot 1: " + (slot1 != null ? slot1.getType() : "null"));
                getLogger().info("Slot 2: " + (slot2 != null ? slot2.getType() : "null"));
                getLogger().info("Slot 3: " + (slot3 != null ? slot3.getType() : "null"));
                getLogger().info("Slot 4: " + (slot4 != null ? slot4.getType() : "null"));
                
                if (slot0 != null && slot0.getType() != Material.AIR) {
                    String json = gson.toJson(slot0.serialize());
                    getLogger().info("Slot 0 JSON length: " + json.length());
                    standMeta.getPersistentDataContainer().set(brewingSlot0Key, PersistentDataType.STRING, json);
                }
                if (slot1 != null && slot1.getType() != Material.AIR) {
                    String json = gson.toJson(slot1.serialize());
                    getLogger().info("Slot 1 JSON length: " + json.length());
                    standMeta.getPersistentDataContainer().set(brewingSlot1Key, PersistentDataType.STRING, json);
                }
                if (slot2 != null && slot2.getType() != Material.AIR) {
                    String json = gson.toJson(slot2.serialize());
                    getLogger().info("Slot 2 JSON length: " + json.length());
                    standMeta.getPersistentDataContainer().set(brewingSlot2Key, PersistentDataType.STRING, json);
                }
                if (slot3 != null && slot3.getType() != Material.AIR) {
                    String json = gson.toJson(slot3.serialize());
                    getLogger().info("Slot 3 JSON length: " + json.length());
                    standMeta.getPersistentDataContainer().set(brewingSlot3Key, PersistentDataType.STRING, json);
                }
                if (slot4 != null && slot4.getType() != Material.AIR) {
                    String json = gson.toJson(slot4.serialize());
                    getLogger().info("Slot 4 JSON length: " + json.length());
                    standMeta.getPersistentDataContainer().set(brewingSlot4Key, PersistentDataType.STRING, json);
                }
                
                // Save fuel level
                if (fuelLevel > 0) {
                    standMeta.getPersistentDataContainer().set(brewingFuelLevelKey, PersistentDataType.INTEGER, fuelLevel);
                }
                
                masterStand.setItemMeta(standMeta);
                
                // Check if tool has auto-pickup from SpecialBooks
                boolean hasAutoPickup = false;
                if (tool != null && tool.hasItemMeta()) {
                    ItemMeta meta = tool.getItemMeta();
                    // SpecialBooks uses "specialbooks" namespace
                    NamespacedKey autoPickupKey = new NamespacedKey("specialbooks", "auto_pickup");
                    hasAutoPickup = meta.getPersistentDataContainer().has(autoPickupKey, PersistentDataType.BYTE);
                }
                
                // Manually break the block
                block.setType(Material.AIR);
                
                // Handle the drop based on auto-pickup
                if (hasAutoPickup) {
                    // Add directly to inventory (mimics SpecialBooks behavior)
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(masterStand);
                    // Drop overflow at player location if inventory is full
                    if (!leftover.isEmpty()) {
                        for (ItemStack overflow : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                        }
                    }
                } else {
                    // Drop naturally at block location
                    block.getWorld().dropItemNaturally(block.getLocation(), masterStand);
                }
                
                player.sendMessage(Component.text("Master Brewing Stand broken!", NamedTextColor.YELLOW));
            }
        }
    }
    
    /**
     * Handles brewing completion in Master Brewing Stands
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBrew(BrewEvent event) {
        Block block = event.getBlock();
        
        // Check if this is a master brewing stand by reading block NBT
        BrewingStand brewingStand = (BrewingStand) block.getState();
        if (!brewingStand.getPersistentDataContainer().has(masterBrewingStandKey, PersistentDataType.BYTE)) {
            return;
        }
        
        BrewerInventory inv = event.getContents();
        ItemStack ingredient = inv.getIngredient();
        
        // Check if ingredient is redstone or glowstone
        if (ingredient == null) {
            return;
        }
        
        Material ingredientType = ingredient.getType();
        boolean isRedstone = ingredientType == Material.REDSTONE;
        boolean isGlowstone = ingredientType == Material.GLOWSTONE_DUST;
        
        if (!isRedstone && !isGlowstone) {
            return;
        }
        
        // Store current potion states BEFORE vanilla brewing happens
        ItemStack[] potionsBefore = new ItemStack[3];
        for (int i = 0; i < 3; i++) {
            ItemStack potion = inv.getItem(i);
            if (potion != null && isPotion(potion)) {
                potionsBefore[i] = potion.clone();
            }
        }
        
        // Cancel vanilla brewing for master brewing stands with redstone/glowstone
        event.setCancelled(true);
        
        // Restore potions and process master brewing
        Bukkit.getScheduler().runTask(this, () -> {
            // Restore original potions
            for (int i = 0; i < 3; i++) {
                if (potionsBefore[i] != null) {
                    inv.setItem(i, potionsBefore[i]);
                }
            }
            
            // Now process master brewing
            processMasterBrew(inv, ingredient, isRedstone, isGlowstone);
        });
    }
    
    /**
     * Processes master brewing upgrades
     */
    private void processMasterBrew(BrewerInventory inv, ItemStack ingredient, boolean isRedstone, boolean isGlowstone) {
        // Get cost from first valid potion
        int materialCost = -1;
        List<ItemStack> potionsToUpgrade = new ArrayList<>();
        
        for (int slot = 0; slot < 3; slot++) {
            ItemStack potion = inv.getItem(slot);
            int cost = getUpgradeCost(potion, isRedstone, isGlowstone);
            if (cost > 0) {
                potionsToUpgrade.add(potion);
                if (materialCost < 0) {
                    materialCost = cost;
                }
            }
        }
        
        if (potionsToUpgrade.isEmpty() || materialCost < 0) {
            return;
        }
        
        if (ingredient.getAmount() < materialCost) {
            return;
        }
        
        // Upgrade all valid potions
        for (ItemStack potion : potionsToUpgrade) {
            upgradeMasterPotion(potion, isRedstone, isGlowstone);
        }
        
        // Consume materials
        int newAmount = ingredient.getAmount() - materialCost;
        if (newAmount <= 0) {
            inv.setIngredient(null);
        } else {
            ingredient.setAmount(newAmount);
            inv.setIngredient(ingredient);
        }
    }
    
    /**
     * Extracts the duration from a vanilla potion
     */
    private int extractVanillaPotionDuration(PotionMeta meta, PotionEffectType effectType) {
        // Try to get duration from custom effects first
        for (PotionEffect effect : meta.getCustomEffects()) {
            if (effect.getType().equals(effectType)) {
                return effect.getDuration() / 20; // Convert ticks to seconds
            }
        }
        
        // Fallback: use vanilla potion type durations
        PotionType potionType = meta.getBasePotionType();
        if (potionType != null) {
            String typeName = potionType.name();
            
            // Long variants are 8 minutes (480 seconds)
            if (typeName.startsWith("LONG_")) {
                return 480;
            }
            
            // Strong variants and regular variants are 3 minutes (180 seconds)
            // Instant effects return 1 second
            if (typeName.contains("INSTANT")) {
                return 1;
            }
            
            // Default to 3 minutes for most potions
            return 180;
        }
        
        // Ultimate fallback
        return 180;
    }
    
    /**
     * Handles right-clicking master potions for instant use
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        ItemStack item = event.getItem();
        if (item == null || !isPotion(item)) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        // Check if it's a master potion
        if (!meta.getPersistentDataContainer().has(masterPotionKey, PersistentDataType.BYTE)) {
            return;
        }
        
        // Cancel vanilla potion consumption completely
        event.setCancelled(true);
        
        Player player = event.getPlayer();
        
        // Get potion data from NBT
        String effectTypeKey = meta.getPersistentDataContainer().get(potionEffectTypeKey, PersistentDataType.STRING);
        int powerLevel = meta.getPersistentDataContainer().getOrDefault(potionPowerLevelKey, PersistentDataType.INTEGER, 0);
        int duration = meta.getPersistentDataContainer().getOrDefault(potionDurationKey, PersistentDataType.INTEGER, 0);
        
        if (effectTypeKey == null || duration == 0) {
            player.sendMessage(Component.text("Invalid master potion!", NamedTextColor.RED));
            return;
        }
        
        // Add effect to tracking system for continuous refresh
        UUID uuid = player.getUniqueId();
        long expiryTime = System.currentTimeMillis() + (duration * 1000L);
        
        // Get or create effect list for player
        List<ActiveMasterEffect> effects = activeMasterEffects.computeIfAbsent(uuid, k -> new ArrayList<>());
        
        // Remove any existing effect of the same type (refresh/replace)
        effects.removeIf(e -> e.effectTypeKey.equals(effectTypeKey));
        
        // Add new effect
        effects.add(new ActiveMasterEffect(effectTypeKey, powerLevel, expiryTime));
        
        // Handle fly potion specially
        if (effectTypeKey.equals("fly")) {
            // Enable flight for the player
            player.setAllowFlight(true);
            player.setFlying(true);
            
            // Calculate and set flight speed: base 0.1f + 20% per power level
            float flightSpeed = 0.1f * (1.0f + (powerLevel * 0.2f));
            flightSpeed = Math.min(flightSpeed, 1.0f); // Cap at 1.0f
            player.setFlySpeed(flightSpeed);
        } else {
            // Handle normal potion effects (including fortune -> luck)
            String mappedEffectKey = effectTypeKey;
            if (effectTypeKey.equals("fortune")) {
                mappedEffectKey = "luck"; // Fortune uses LUCK potion effect
            }
            
            PotionEffectType effectType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(mappedEffectKey));
            if (effectType == null) {
                player.sendMessage(Component.text("Invalid potion effect!", NamedTextColor.RED));
                return;
            }
            
            // Immediately apply the effect (will be continuously refreshed by task)
            player.removePotionEffect(effectType);
            player.addPotionEffect(new PotionEffect(effectType, duration * 20, powerLevel, false, true, true), true);
        }
        
        // Consume potion
        item.setAmount(item.getAmount() - 1);
        
        // Play sound
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f);
        
        // Send activation message with details
        String effectName;
        if (effectTypeKey.equals("fly")) {
            effectName = "Fly";
        } else if (effectTypeKey.equals("fortune")) {
            effectName = "Fortune";
        } else {
            effectName = formatEffectName(PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effectTypeKey)));
        }
        int displayLevel = powerLevel + 1; // Power level 1 = II, 2 = III, etc.
        String romanLevel = toRoman(displayLevel);
        String durationStr = formatDuration(duration);
        
        player.sendMessage(Component.text("Master Potion ", NamedTextColor.GREEN)
            .append(Component.text(effectName + " " + romanLevel, NamedTextColor.GOLD))
            .append(Component.text(" (" + durationStr + ")", NamedTextColor.YELLOW))
            .append(Component.text(" activated!", NamedTextColor.GREEN)));
    }
    
    /**
     * Prevents vanilla from consuming master potions
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        
        if (item == null || !isPotion(item)) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        // If it's a master potion, prevent vanilla consumption
        if (meta.getPersistentDataContainer().has(masterPotionKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Clean up tracked effects when player disconnects
     * OPTIMIZATION #4: Now saves asynchronously
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        
        // Get the effects BEFORE removing from map
        List<ActiveMasterEffect> effects = activeMasterEffects.get(uuid);
        
        // Save effects with the current list (will be copied in save method)
        savePlayerEffects(uuid, effects);
        
        // Remove from active tracking
        activeMasterEffects.remove(uuid);
    }
    
    /**
     * Restore tracked effects when player reconnects
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Player player = event.getPlayer();
        
        // Load saved effects
        loadPlayerEffects(uuid);
        
        // Wait 1 tick for player to fully load, then restore effects
        Bukkit.getScheduler().runTask(this, () -> {
            // Immediately restore flight state if player has active fly effect
            List<ActiveMasterEffect> effects = activeMasterEffects.get(uuid);
            if (effects != null) {
                long currentTime = System.currentTimeMillis();
                
                for (ActiveMasterEffect effect : effects) {
                    // Skip expired effects
                    if (currentTime >= effect.expiryTime) {
                        continue;
                    }
                    
                    int remainingSeconds = (int) ((effect.expiryTime - currentTime) / 1000);
                    
                    // Restore fly effect immediately
                    if (effect.effectTypeKey.equals("fly")) {
                        // Only enable for survival/adventure mode
                        if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || 
                            player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                            player.setAllowFlight(true);
                            player.setFlying(true);
                            
                            // Set correct flight speed
                            float flightSpeed = 0.1f * (1.0f + (effect.amplifier * 0.2f));
                            flightSpeed = Math.min(flightSpeed, 1.0f);
                            player.setFlySpeed(flightSpeed);
                        }
                        
                        player.sendMessage(Component.text("Flight restored! ", NamedTextColor.GREEN)
                            .append(Component.text(formatDuration(remainingSeconds) + " remaining", NamedTextColor.YELLOW)));
                    } else {
                        // Restore normal potion effects immediately
                        String mappedEffectKey = effect.effectTypeKey;
                        if (effect.effectTypeKey.equals("fortune")) {
                            mappedEffectKey = "luck";
                        }
                        
                        PotionEffectType effectType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(mappedEffectKey));
                        if (effectType != null) {
                            int durationTicks = remainingSeconds * 20;
                            player.addPotionEffect(new PotionEffect(effectType, durationTicks, effect.amplifier, false, true, true), true);
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Saves a player's active master potion effects to their playerdata file
     * OPTIMIZATION #4: Now runs asynchronously
     */
    private void savePlayerEffects(UUID uuid, List<ActiveMasterEffect> effects) {
        // OPTIMIZATION #4: Run file I/O asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (effects == null || effects.isEmpty()) {
                // Delete file if no effects
                File playerFile = new File(getDataFolder(), "playerdata/" + uuid.toString() + ".yml");
                if (playerFile.exists()) {
                    playerFile.delete();
                }
                return;
            }
            
            File playerDataFolder = new File(getDataFolder(), "playerdata");
            if (!playerDataFolder.exists()) {
                playerDataFolder.mkdirs();
            }
            
            File playerFile = new File(playerDataFolder, uuid.toString() + ".yml");
            org.bukkit.configuration.file.FileConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(playerFile);
            
            List<String> effectStrings = new ArrayList<>();
            for (ActiveMasterEffect effect : effects) {
                // Format: effectTypeKey,amplifier,expiryTime
                String effectString = effect.effectTypeKey + "," + 
                                     effect.amplifier + "," + 
                                     effect.expiryTime;
                effectStrings.add(effectString);
            }
            
            config.set("active-effects", effectStrings);
            
            try {
                config.save(playerFile);
            } catch (Exception e) {
                getLogger().warning("Failed to save player effects for " + uuid + ": " + e.getMessage());
            }
        });
    }
    
    /**
     * Loads a player's active master potion effects from their playerdata file
     */
    private void loadPlayerEffects(UUID uuid) {
        File playerFile = new File(getDataFolder(), "playerdata/" + uuid.toString() + ".yml");
        
        if (!playerFile.exists()) {
            return;
        }
        
        org.bukkit.configuration.file.FileConfiguration config = 
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(playerFile);
        
        List<String> effectStrings = config.getStringList("active-effects");
        if (effectStrings.isEmpty()) {
            return;
        }
        
        List<ActiveMasterEffect> effects = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (String effectString : effectStrings) {
            try {
                String[] parts = effectString.split(",");
                if (parts.length != 3) continue;
                
                String effectKey = parts[0];
                int amplifier = Integer.parseInt(parts[1]);
                long expiryTime = Long.parseLong(parts[2]);
                
                // Skip if effect already expired
                if (currentTime >= expiryTime) {
                    continue;
                }
                
                // Validate effect type exists (skip validation for fly and fortune)
                if (!effectKey.equals("fly") && !effectKey.equals("fortune")) {
                    PotionEffectType effectType = PotionEffectType.getByKey(
                        org.bukkit.NamespacedKey.minecraft(effectKey)
                    );
                    
                    if (effectType == null) {
                        getLogger().warning("Unknown effect type: " + effectKey);
                        continue;
                    }
                }
                
                effects.add(new ActiveMasterEffect(effectKey, amplifier, expiryTime));
                
            } catch (Exception e) {
                getLogger().warning("Failed to parse effect string: " + effectString);
            }
        }
        
        if (!effects.isEmpty()) {
            activeMasterEffects.put(uuid, effects);
            getLogger().info("Restored " + effects.size() + " master potion effects for player " + uuid);
        }
    }
    
    /**
     * Saves virtual brewing stand contents for all players to disk
     * Called asynchronously during normal operation
     */
    /**
     * Saves a player's virtual brewing stand data to their individual file
     * 
     * @param playerUUID The player's UUID
     * @param contents The inventory contents (5 slots)
     * @param fuelLevel The current fuel level (0-20)
     */
    private void savePlayerBrewingData(UUID playerUUID, ItemStack[] contents, int fuelLevel) {
        File playerFile = new File(getDataFolder(), "playerdata/" + playerUUID.toString() + ".yml");
        
        // Check if there's anything to save
        boolean hasContent = false;
        for (ItemStack item : contents) {
            if (item != null) {
                hasContent = true;
                break;
            }
        }
        
        // If no content and no fuel, delete the file if it exists
        if (!hasContent && fuelLevel <= 0) {
            if (playerFile.exists()) {
                playerFile.delete();
            }
            return;
        }
        
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        
        // Serialize each slot
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                String base64 = itemStackToBase64(contents[i]);
                config.set("slot" + i, base64);
            }
        }
        
        // Save fuel level
        if (fuelLevel > 0) {
            config.set("fuel", fuelLevel);
        }
        
        try {
            config.save(playerFile);
        } catch (Exception e) {
            getLogger().warning("Failed to save brewing data for " + playerUUID + ": " + e.getMessage());
        }
    }
    
    /**
     * Loads a player's virtual brewing stand data from their individual file
     * 
     * @param playerUUID The player's UUID
     * @return A two-element array: [0] = ItemStack[] contents, [1] = Integer fuel level
     *         Returns null contents and 0 fuel if no data exists
     */
    private Object[] loadPlayerBrewingData(UUID playerUUID) {
        File playerFile = new File(getDataFolder(), "playerdata/" + playerUUID.toString() + ".yml");
        
        ItemStack[] contents = new ItemStack[5];
        int fuelLevel = 0;
        
        if (!playerFile.exists()) {
            return new Object[] { contents, fuelLevel };
        }
        
        try {
            org.bukkit.configuration.file.YamlConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(playerFile);
            
            // Load each slot
            for (int i = 0; i < 5; i++) {
                String base64 = config.getString("slot" + i);
                if (base64 != null) {
                    contents[i] = itemStackFromBase64(base64);
                }
            }
            
            // Load fuel level
            fuelLevel = config.getInt("fuel", 0);
            
        } catch (Exception e) {
            getLogger().warning("Failed to load brewing data for " + playerUUID + ": " + e.getMessage());
        }
        
        return new Object[] { contents, fuelLevel };
    }
    
    /**
     * Serializes an ItemStack to a Base64 string
     */
    private String itemStackToBase64(ItemStack item) {
        try {
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            org.bukkit.util.io.BukkitObjectOutputStream dataOutput = new org.bukkit.util.io.BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            getLogger().warning("Failed to serialize ItemStack: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Deserializes an ItemStack from a Base64 string
     */
    private ItemStack itemStackFromBase64(String base64) {
        try {
            java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(Base64.getDecoder().decode(base64));
            org.bukkit.util.io.BukkitObjectInputStream dataInput = new org.bukkit.util.io.BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            getLogger().warning("Failed to deserialize ItemStack: " + e.getMessage());
            return null;
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Upgrades a master potion with the given ingredient
     * This is the SINGLE code path for all potion upgrades - real and virtual brewing stands
     * 
     * @param potion The potion to upgrade
     * @param isRedstone True if upgrading with redstone (duration)
     * @param isGlowstone True if upgrading with glowstone (power)
     * @return The material cost consumed, or -1 if upgrade failed
     */
    private int upgradeMasterPotion(ItemStack potion, boolean isRedstone, boolean isGlowstone) {
        if (potion == null || !isPotion(potion)) {
            return -1;
        }
        
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta == null) {
            return -1;
        }
        
        // Get current levels
        int currentTimeLevel = meta.getPersistentDataContainer().getOrDefault(potionTimeLevelKey, PersistentDataType.INTEGER, 0);
        int currentPowerLevel = meta.getPersistentDataContainer().getOrDefault(potionPowerLevelKey, PersistentDataType.INTEGER, 0);
        
        // Get effect type
        String effectTypeKey = meta.getPersistentDataContainer().get(potionEffectTypeKey, PersistentDataType.STRING);
        if (effectTypeKey == null) {
            PotionEffectType effectType = getBasePotionEffect(potion);
            if (effectType == null) {
                return -1;
            }
            effectTypeKey = effectType.getKey().getKey();
        }
        
        // Determine upgrade parameters
        int materialCost = 0;
        int newTimeLevel = currentTimeLevel;
        int newPowerLevel = currentPowerLevel;
        int upgradeDuration = 0;
        
        if (isRedstone) {
            if (isInstantEffect(effectTypeKey)) {
                return -1; // Can't upgrade instant effects with redstone
            }
            int nextLevel = currentTimeLevel + 1;
            int potionMaxTimeLevel = getMaxTimeLevel(effectTypeKey);
            if (nextLevel > potionMaxTimeLevel) {
                return -1; // Already at max
            }
            Map<Integer, int[]> potionTimeUpgrades = getTimeUpgrades(effectTypeKey);
            int[] upgrade = potionTimeUpgrades.get(nextLevel);
            if (upgrade == null) {
                return -1;
            }
            materialCost = upgrade[0];
            upgradeDuration = upgrade[1];
            newTimeLevel = nextLevel;
        } else if (isGlowstone) {
            int nextLevel = currentPowerLevel + 1;
            int potionMaxPowerLevel = getMaxPowerLevel(effectTypeKey);
            if (nextLevel > potionMaxPowerLevel) {
                return -1; // Already at max
            }
            Map<Integer, Integer> potionPowerUpgrades = getPowerUpgrades(effectTypeKey);
            Integer glowstoneCost = potionPowerUpgrades.get(nextLevel);
            if (glowstoneCost == null) {
                return -1;
            }
            materialCost = glowstoneCost;
            newPowerLevel = nextLevel;
        } else {
            return -1;
        }
        
        // Calculate duration
        int duration;
        if (isRedstone) {
            duration = upgradeDuration;
        } else {
            // Keep existing duration for power upgrades
            if (meta.getPersistentDataContainer().has(potionDurationKey, PersistentDataType.INTEGER)) {
                duration = meta.getPersistentDataContainer().get(potionDurationKey, PersistentDataType.INTEGER);
            } else {
                if (effectTypeKey.equals("fly") || effectTypeKey.equals("fortune")) {
                    duration = 180;
                } else {
                    PotionEffectType effectType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effectTypeKey));
                    if (effectType != null) {
                        duration = extractVanillaPotionDuration(meta, effectType);
                    } else {
                        duration = 180;
                    }
                }
            }
        }
        
        // Get effect type for display
        PotionEffectType effectType = null;
        if (!effectTypeKey.equals("fly") && !effectTypeKey.equals("fortune")) {
            effectType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effectTypeKey));
        }
        
        // Update NBT
        meta.getPersistentDataContainer().set(potionTimeLevelKey, PersistentDataType.INTEGER, newTimeLevel);
        meta.getPersistentDataContainer().set(potionPowerLevelKey, PersistentDataType.INTEGER, newPowerLevel);
        meta.getPersistentDataContainer().set(potionDurationKey, PersistentDataType.INTEGER, duration);
        meta.getPersistentDataContainer().set(potionEffectTypeKey, PersistentDataType.STRING, effectTypeKey);
        meta.getPersistentDataContainer().set(masterPotionKey, PersistentDataType.BYTE, (byte) 1);
        
        // Clear base potion type
        meta.setBasePotionType(PotionType.WATER);
        
        // Update display (color, effects, name, lore)
        updateMasterPotionDisplay(meta, effectTypeKey, effectType, newTimeLevel, newPowerLevel, duration);
        
        potion.setItemMeta(meta);
        return materialCost;
    }
    
    /**
     * Gets the material cost for the next upgrade without applying it
     * 
     * @param potion The potion to check
     * @param isRedstone True if checking redstone upgrade
     * @param isGlowstone True if checking glowstone upgrade
     * @return The material cost, or -1 if upgrade not possible
     */
    private int getUpgradeCost(ItemStack potion, boolean isRedstone, boolean isGlowstone) {
        if (potion == null || !isPotion(potion)) {
            return -1;
        }
        
        ItemMeta meta = potion.getItemMeta();
        if (meta == null) {
            return -1;
        }
        
        int currentTimeLevel = meta.getPersistentDataContainer().getOrDefault(potionTimeLevelKey, PersistentDataType.INTEGER, 0);
        int currentPowerLevel = meta.getPersistentDataContainer().getOrDefault(potionPowerLevelKey, PersistentDataType.INTEGER, 0);
        
        String effectTypeKey = meta.getPersistentDataContainer().get(potionEffectTypeKey, PersistentDataType.STRING);
        if (effectTypeKey == null) {
            PotionEffectType effectType = getBasePotionEffect(potion);
            if (effectType == null) {
                return -1;
            }
            effectTypeKey = effectType.getKey().getKey();
        }
        
        if (isRedstone) {
            if (isInstantEffect(effectTypeKey)) {
                return -1;
            }
            int nextLevel = currentTimeLevel + 1;
            if (nextLevel > getMaxTimeLevel(effectTypeKey)) {
                return -1;
            }
            Map<Integer, int[]> potionTimeUpgrades = getTimeUpgrades(effectTypeKey);
            int[] upgrade = potionTimeUpgrades.get(nextLevel);
            return (upgrade != null) ? upgrade[0] : -1;
        } else if (isGlowstone) {
            int nextLevel = currentPowerLevel + 1;
            if (nextLevel > getMaxPowerLevel(effectTypeKey)) {
                return -1;
            }
            Map<Integer, Integer> potionPowerUpgrades = getPowerUpgrades(effectTypeKey);
            Integer cost = potionPowerUpgrades.get(nextLevel);
            return (cost != null) ? cost : -1;
        }
        return -1;
    }
    
    /**
     * Updates a master potion's display name, lore, color, and effects
     * This is the single source of truth for how master potions are displayed
     * 
     * @param meta The potion meta to update
     * @param effectTypeKey The effect identifier (e.g., "speed", "fly", "fortune")
     * @param effectType The PotionEffectType (null for fly)
     * @param newTimeLevel The current time upgrade level
     * @param newPowerLevel The current power upgrade level
     * @param duration The duration in seconds
     */
    private void updateMasterPotionDisplay(PotionMeta meta, String effectTypeKey, PotionEffectType effectType, 
                                           int newTimeLevel, int newPowerLevel, int duration) {
        // Set color
        if (effectTypeKey.equals("fly")) {
            meta.setColor(org.bukkit.Color.ORANGE);
        } else if (effectTypeKey.equals("fortune")) {
            meta.setColor(org.bukkit.Color.LIME);
        } else if (effectType != null) {
            meta.setColor(getPotionColor(effectType));
        }
        
        // Set custom effects
        meta.clearCustomEffects();
        if (effectTypeKey.equals("fortune")) {
            PotionEffectType luckEffect = PotionEffectType.LUCK;
            meta.addCustomEffect(new PotionEffect(luckEffect, duration * 20, newPowerLevel, false, false, false), true);
        } else if (!effectTypeKey.equals("fly") && effectType != null) {
            meta.addCustomEffect(new PotionEffect(effectType, duration * 20, newPowerLevel, false, false, false), true);
        }
        
        // Set display name
        String effectName;
        if (effectTypeKey.equals("fly")) {
            effectName = "Fly";
        } else if (effectTypeKey.equals("fortune")) {
            effectName = "Fortune";
        } else {
            effectName = formatEffectName(effectType);
        }
        int displayLevel = newPowerLevel + 1;
        String romanLevel = toRoman(displayLevel);
        
        Component name = Component.text(effectName + " " + romanLevel, NamedTextColor.GOLD, TextDecoration.ITALIC)
            .decoration(TextDecoration.ITALIC, true);
        meta.displayName(name);
        
        // Build lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Master Potion", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
        
        int potionMaxPowerLevel = getMaxPowerLevel(effectTypeKey);
        int potionMaxTimeLevel = getMaxTimeLevel(effectTypeKey);
        Map<Integer, Integer> potionPowerUpgrades = getPowerUpgrades(effectTypeKey);
        Map<Integer, int[]> potionTimeUpgrades = getTimeUpgrades(effectTypeKey);
        
        int maxDuration = 0;
        for (int[] timeUpgrade : potionTimeUpgrades.values()) {
            if (timeUpgrade[1] > maxDuration) {
                maxDuration = timeUpgrade[1];
            }
        }
        
        if (effectTypeKey.equals("fly")) {
            // Fly-specific lore
            float flightSpeed = 0.1f * (1.0f + (newPowerLevel * 0.2f));
            flightSpeed = Math.min(flightSpeed, 1.0f);
            int speedPercent = (int)((flightSpeed / 0.1f) * 100);
            
            boolean atMaxSpeed = newPowerLevel >= potionMaxPowerLevel;
            boolean atMaxDuration = newTimeLevel >= potionMaxTimeLevel;
            
            if (atMaxSpeed) {
                lore.add(Component.text("Flight Speed: " + speedPercent + "% (MAX)", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                float maxFlightSpeed = 0.1f * (1.0f + (potionMaxPowerLevel * 0.2f));
                maxFlightSpeed = Math.min(maxFlightSpeed, 1.0f);
                int maxSpeedPercent = (int)((maxFlightSpeed / 0.1f) * 100);
                lore.add(Component.text("Flight Speed: " + speedPercent + "% (Max: " + maxSpeedPercent + "%)", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            if (atMaxDuration) {
                lore.add(Component.text("Duration: " + formatDuration(duration) + " (MAX)", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Duration: " + formatDuration(duration) + " (Max: " + formatDuration(maxDuration) + ")", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            if (!atMaxSpeed) {
                int nextPowerLevel = newPowerLevel + 1;
                Integer glowstoneCost = potionPowerUpgrades.get(nextPowerLevel);
                if (glowstoneCost != null) {
                    lore.add(Component.text("Flight Speed Upgrade: " + glowstoneCost + " glowstone dust", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
            if (!atMaxDuration) {
                int nextTimeLevel = newTimeLevel + 1;
                int[] timeUpgrade = potionTimeUpgrades.get(nextTimeLevel);
                if (timeUpgrade != null) {
                    lore.add(Component.text("Duration Upgrade: " + timeUpgrade[0] + " redstone dust", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
        } else if (effectTypeKey.equals("fortune")) {
            // Fortune-specific lore
            boolean atMaxLuck = newPowerLevel >= potionMaxPowerLevel;
            boolean atMaxDuration = newTimeLevel >= potionMaxTimeLevel;
            
            if (atMaxLuck) {
                lore.add(Component.text("Luck: +" + (newPowerLevel + 1) + " (MAX)", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Luck: +" + (newPowerLevel + 1) + " (Max: +" + (potionMaxPowerLevel + 1) + ")", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            if (atMaxDuration) {
                lore.add(Component.text("Duration: " + formatDuration(duration) + " (MAX)", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Duration: " + formatDuration(duration) + " (Max: " + formatDuration(maxDuration) + ")", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            if (!atMaxLuck) {
                int nextPowerLevel = newPowerLevel + 1;
                Integer glowstoneCost = potionPowerUpgrades.get(nextPowerLevel);
                if (glowstoneCost != null) {
                    lore.add(Component.text("Luck Upgrade: " + glowstoneCost + " glowstone dust", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
            if (!atMaxDuration) {
                int nextTimeLevel = newTimeLevel + 1;
                int[] timeUpgrade = potionTimeUpgrades.get(nextTimeLevel);
                if (timeUpgrade != null) {
                    lore.add(Component.text("Duration Upgrade: " + timeUpgrade[0] + " redstone dust", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
        } else {
            // Standard potion lore
            boolean atMaxPower = newPowerLevel >= potionMaxPowerLevel;
            boolean atMaxDuration = newTimeLevel >= potionMaxTimeLevel;
            boolean isInstant = isInstantEffect(effectTypeKey);
            
            String powerLabel = effectName;
            if (atMaxPower) {
                lore.add(Component.text(powerLabel + ": +" + (newPowerLevel + 1) + " (MAX)", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(powerLabel + ": +" + (newPowerLevel + 1) + " (Max: +" + (potionMaxPowerLevel + 1) + ")", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            if (!isInstant) {
                if (atMaxDuration) {
                    lore.add(Component.text("Duration: " + formatDuration(duration) + " (MAX)", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("Duration: " + formatDuration(duration) + " (Max: " + formatDuration(maxDuration) + ")", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                }
            } else {
                lore.add(Component.text("Duration: Instant", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            if (!atMaxPower) {
                int nextPowerLevel = newPowerLevel + 1;
                Integer glowstoneCost = potionPowerUpgrades.get(nextPowerLevel);
                if (glowstoneCost != null) {
                    lore.add(Component.text(powerLabel + " Upgrade: " + glowstoneCost + " glowstone dust", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
            if (!isInstant && !atMaxDuration) {
                int nextTimeLevel = newTimeLevel + 1;
                int[] timeUpgrade = potionTimeUpgrades.get(nextTimeLevel);
                if (timeUpgrade != null) {
                    lore.add(Component.text("Duration Upgrade: " + timeUpgrade[0] + " redstone dust", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
        }
        
        meta.lore(lore);
    }
    
    /**
     * Checks if an item is a potion
     */
    private boolean isPotion(ItemStack item) {
        Material type = item.getType();
        return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION;
    }
    
    /**
     * Gets the base potion effect from a potion item
     */
    private PotionEffectType getBasePotionEffect(ItemStack potion) {
        if (!(potion.getItemMeta() instanceof PotionMeta)) {
            return null;
        }
        
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        
        // Check if already a master potion with stored effect
        if (meta.getPersistentDataContainer().has(potionEffectTypeKey, PersistentDataType.STRING)) {
            String effectKey = meta.getPersistentDataContainer().get(potionEffectTypeKey, PersistentDataType.STRING);
            if (effectKey.equals("fly")) {
                return null; // Fly has no PotionEffectType
            }
            return PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effectKey));
        }
        
        // Try to get from base potion type first
        PotionType potionType = meta.getBasePotionType();
        if (potionType != null && potionType != PotionType.WATER) {
            // Get the potion type's key and extract effect name
            String potionKey = potionType.getKey().getKey();
            
            // Remove long_ and strong_ prefixes if present
            String effectName = potionKey.replace("long_", "").replace("strong_", "");
            
            // Map special cases
            if (effectName.equals("leaping")) {
                effectName = "jump_boost";
            } else if (effectName.equals("swiftness")) {
                effectName = "speed";
            } else if (effectName.equals("healing")) {
                effectName = "instant_health";
            } else if (effectName.equals("harming")) {
                effectName = "instant_damage";
            }
            
            PotionEffectType effectType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effectName));
            if (effectType != null) {
                return effectType;
            }
        }
        
        // Fallback: check custom effects (for potions like luck, bad omen, etc.)
        if (!meta.getCustomEffects().isEmpty()) {
            // Return the first custom effect's type
            return meta.getCustomEffects().get(0).getType();
        }
        
        return null;
    }
    
    /**
     * Formats effect name for display
     */
    private String formatEffectName(PotionEffectType effectType) {
        String key = effectType.getKey().getKey();
        
        // Convert snake_case to Title Case
        String[] words = key.split("_");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }
        
        return result.toString();
    }
    
    /**
     * Converts integer to Roman numeral
     */
    private String toRoman(int number) {
        if (number <= 0) return "I";
        
        String[] thousands = {"", "M", "MM", "MMM"};
        String[] hundreds = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        
        return thousands[number / 1000] +
               hundreds[(number % 1000) / 100] +
               tens[(number % 100) / 10] +
               ones[number % 10];
    }
    
    /**
     * Gets the correct potion color for a given effect type
     * Colors from vanilla Minecraft 1.21+ (OptiFine color.properties)
     */
    private org.bukkit.Color getPotionColor(PotionEffectType effectType) {
        String key = effectType.getKey().getKey();
        
        switch (key) {
            case "speed":
                return org.bukkit.Color.fromRGB(0x33ebff);
            case "slowness":
                return org.bukkit.Color.fromRGB(0x8bafe0);
            case "haste":
                return org.bukkit.Color.fromRGB(0xd9c043);
            case "mining_fatigue":
                return org.bukkit.Color.fromRGB(0x63855c);
            case "strength":
                return org.bukkit.Color.fromRGB(0xcc243c);
            case "instant_health":
                return org.bukkit.Color.fromRGB(0xf5424b);
            case "instant_damage":
                return org.bukkit.Color.fromRGB(0x5b4f31);
            case "jump_boost":
                return org.bukkit.Color.fromRGB(0x44ff33);
            case "nausea":
                return org.bukkit.Color.fromRGB(0x76ae6e);
            case "regeneration":
                return org.bukkit.Color.fromRGB(0xf89fba);
            case "resistance":
                return org.bukkit.Color.fromRGB(0x937c64);
            case "fire_resistance":
                return org.bukkit.Color.fromRGB(0xffa733);
            case "water_breathing":
                return org.bukkit.Color.fromRGB(0x6d9fa9);
            case "invisibility":
                return org.bukkit.Color.fromRGB(0x9999b0);
            case "blindness":
                return org.bukkit.Color.fromRGB(0x3b393a);
            case "night_vision":
                return org.bukkit.Color.fromRGB(0x3066e6);
            case "hunger":
                return org.bukkit.Color.fromRGB(0x718956);
            case "weakness":
                return org.bukkit.Color.fromRGB(0x555555);
            case "poison":
                return org.bukkit.Color.fromRGB(0x76b361);
            case "wither":
                return org.bukkit.Color.fromRGB(0x4e483c);
            case "health_boost":
                return org.bukkit.Color.fromRGB(0xfc5952);
            case "absorption":
                return org.bukkit.Color.fromRGB(0x3e61b7);
            case "saturation":
                return org.bukkit.Color.fromRGB(0xff5933);
            case "glowing":
                return org.bukkit.Color.fromRGB(0xcc9966);
            case "levitation":
                return org.bukkit.Color.fromRGB(0xccccff);
            case "luck":
                return org.bukkit.Color.fromRGB(0x59db6d);
            case "unluck":
                return org.bukkit.Color.fromRGB(0xf5e79f);
            case "slow_falling":
                return org.bukkit.Color.fromRGB(0xe6f0f7);
            case "conduit_power":
                return org.bukkit.Color.fromRGB(0x5bb3d1);
            case "dolphins_grace":
                return org.bukkit.Color.fromRGB(0x89ade9);
            case "bad_omen":
                return org.bukkit.Color.fromRGB(0x345f27);
            case "hero_of_the_village":
                return org.bukkit.Color.fromRGB(0x2e9e6a);
            case "darkness":
                return org.bukkit.Color.fromRGB(0x292929);
            default:
                return org.bukkit.Color.GRAY;
        }
    }
    
    /**
     * Formats duration in seconds to human-readable string
     */
    private String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
    
    // ==================== COMMANDS ====================
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // No args - open virtual master brewing stand if player has permission
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("This command can only be used by players!", NamedTextColor.RED));
                return true;
            }
            
            Player player = (Player) sender;
            if (!player.hasPermission("masterbrewing.use")) {
                player.sendMessage(Component.text("You don't have permission to use Master Brewing Stands!", NamedTextColor.RED));
                return true;
            }
            
            openVirtualBrewingStand(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "help":
                sendHelp(sender);
                return true;
            case "give":
                return handleGive(sender, args);
            case "reload":
                return handleReload(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    /**
     * Sends styled help message
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Master Brewing Commands", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        
        if (sender.hasPermission("masterbrewing.use")) {
            sender.sendMessage(Component.text("/masterbrewing", NamedTextColor.YELLOW)
                .append(Component.text(" - Open a virtual Master Brewing Stand", NamedTextColor.GRAY)));
        }
        
        sender.sendMessage(Component.text("/masterbrewing help", NamedTextColor.YELLOW)
            .append(Component.text(" - Show this help menu", NamedTextColor.GRAY)));
        
        if (sender.hasPermission("masterbrewing.give")) {
            sender.sendMessage(Component.text("/masterbrewing give stand <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Give a Master Brewing Stand", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/masterbrewing give potion <player> <potion> [time_lvl] [power_lvl]", NamedTextColor.YELLOW)
                .append(Component.text(" - Give a Master Potion (defaults: 1,1 or 0,0 for fly)", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/masterbrewing give potion <player> <potion> max", NamedTextColor.YELLOW)
                .append(Component.text(" - Give max level potion", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/masterbrewing give potion <player> random", NamedTextColor.YELLOW)
                .append(Component.text(" - Give random potion", NamedTextColor.GRAY)));
        }
        
        if (sender.hasPermission("masterbrewing.admin")) {
            sender.sendMessage(Component.text("/masterbrewing reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload configuration", NamedTextColor.GRAY)));
        }
        
        // Display upgrade tables
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Time Upgrades (Redstone)", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(" Level │ Redstone │ Duration", NamedTextColor.WHITE, TextDecoration.BOLD));
        sender.sendMessage(Component.text("───────┼──────────┼──────────", NamedTextColor.DARK_GRAY));
        
        for (int level : timeUpgrades.keySet()) {
            int[] upgrade = timeUpgrades.get(level);
            int redstoneCost = upgrade[0];
            int duration = upgrade[1];
            String durationStr = formatDuration(duration);
            
            // Format with proper spacing: Level (right-aligned 5), Redstone (right-aligned 8), Duration (left-aligned 8)
            String levelStr = String.format("%5d", level);
            String redstoneStr = String.format("%8d", redstoneCost);
            String durationDisplay = String.format("%-8s", durationStr);
            
            sender.sendMessage(Component.text(levelStr, NamedTextColor.YELLOW)
                .append(Component.text(" │ ", NamedTextColor.DARK_GRAY))
                .append(Component.text(redstoneStr, NamedTextColor.RED))
                .append(Component.text(" │ ", NamedTextColor.DARK_GRAY))
                .append(Component.text(durationDisplay, NamedTextColor.AQUA)));
        }
        
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Power Upgrades (Glowstone)", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(" Level │ Glowstone │ Potion Level", NamedTextColor.WHITE, TextDecoration.BOLD));
        sender.sendMessage(Component.text("───────┼───────────┼──────────────", NamedTextColor.DARK_GRAY));
        
        for (int level : powerUpgrades.keySet()) {
            int glowstoneCost = powerUpgrades.get(level);
            int displayLevel = level + 1; // Power level 1 = II, 2 = III, etc.
            String romanLevel = toRoman(displayLevel);
            
            // Format with proper spacing: Level (right-aligned 5), Glowstone (right-aligned 9), Potion Level (left-aligned 12)
            String levelStr = String.format("%5d", level);
            String glowstoneStr = String.format("%9d", glowstoneCost);
            String potionLevelDisplay = String.format("%-12s", romanLevel);
            
            sender.sendMessage(Component.text(levelStr, NamedTextColor.YELLOW)
                .append(Component.text(" │ ", NamedTextColor.DARK_GRAY))
                .append(Component.text(glowstoneStr, NamedTextColor.GOLD))
                .append(Component.text(" │ ", NamedTextColor.DARK_GRAY))
                .append(Component.text(potionLevelDisplay, NamedTextColor.LIGHT_PURPLE)));
        }
        
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }
    
    /**
     * Handles /masterbrewing give command
     */
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("masterbrewing.give")) {
            sender.sendMessage(Component.text("You don't have permission to give Master Brewing items!", NamedTextColor.RED));
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /masterbrewing give stand <player>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> <potion> [time_lvl] [power_lvl]", NamedTextColor.RED));
            sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> random", NamedTextColor.RED));
            return true;
        }
        
        String subCommand = args[1].toLowerCase();
        
        if (subCommand.equals("stand")) {
            return handleGiveStand(sender, args);
        } else if (subCommand.equals("potion")) {
            return handleGivePotion(sender, args);
        } else {
            sender.sendMessage(Component.text("Usage: /masterbrewing give stand <player>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> <potion> [time_lvl] [power_lvl]", NamedTextColor.RED));
            sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> random", NamedTextColor.RED));
            return true;
        }
    }
    
    /**
     * Handles /masterbrewing give stand command
     */
    private boolean handleGiveStand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /masterbrewing give stand <player>", NamedTextColor.RED));
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
            return true;
        }
        
        target.getInventory().addItem(createMasterBrewingStand());
        
        sender.sendMessage(Component.text("Gave Master Brewing Stand to ", NamedTextColor.GREEN)
            .append(Component.text(target.getName(), NamedTextColor.GOLD)));
        target.sendMessage(Component.text("You received a Master Brewing Stand!", NamedTextColor.GREEN));
        
        return true;
    }
    
    /**
     * Handles /masterbrewing give potion command
     * OPTIMIZATION #3: Now uses map lookup instead of switch statement
     */
    private boolean handleGivePotion(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> <potion> [time_lvl] [power_lvl]", NamedTextColor.RED)
                .append(Component.text(" - defaults: 1,1 (or 0,0 for fly)", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> random", NamedTextColor.RED));
            sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> <potion> max", NamedTextColor.RED));
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
            return true;
        }
        
        String potionName = args[3].toLowerCase();
        
        // Handle "random" variant
        if (potionName.equals("random")) {
            return handleGiveRandomPotion(sender, target);
        }
        
        // Handle "max" variant
        boolean isMaxVariant = (args.length >= 5 && args[4].equalsIgnoreCase("max"));
        boolean useDefaults = (args.length <= 4) || isMaxVariant;
        
        // OPTIMIZATION #3: Use map lookup instead of switch statement
        String effectKey = POTION_NAME_TO_EFFECT_KEY.get(potionName);
        
        if (effectKey == null) {
            sender.sendMessage(Component.text("Invalid potion type: " + potionName, NamedTextColor.RED));
            sender.sendMessage(Component.text("Available types: fortune, fly, speed, slowness, haste, mining_fatigue, strength, healing, harming, leaping, nausea, regeneration, resistance, fire_resistance, water_breathing, invisibility, blindness, night_vision, hunger, weakness, poison, wither, health_boost, absorption, saturation, glowing, levitation, luck, unluck, slow_falling, conduit_power, dolphins_grace, bad_omen, hero_of_the_village, darkness", NamedTextColor.GRAY));
            return true;
        }
        
        // Get effect type using NamespacedKey (skip for custom fly and fortune potions)
        PotionEffectType effectType = null;
        if (!effectKey.equals("fly") && !effectKey.equals("fortune")) {
            effectType = PotionEffectType.getByKey(NamespacedKey.minecraft(effectKey));
            if (effectType == null) {
                sender.sendMessage(Component.text("Failed to load potion effect type: " + effectKey, NamedTextColor.RED));
                return true;
            }
        }
        
        int timeLevel;
        int powerLevel;
        
        // Need to get max levels for this specific potion for the max variant
        int potionMaxTimeLevel = getMaxTimeLevel(effectKey);
        int potionMaxPowerLevel = getMaxPowerLevel(effectKey);
        
        if (useDefaults) {
            // Special case: fly and fortune default to 0,0 (base power, 3 min duration)
            if (potionName.equals("fly") || potionName.equals("fortune")) {
                timeLevel = 0;
                powerLevel = 0;
            } else {
                timeLevel = 1;
                powerLevel = 1;
            }
        } else if (isMaxVariant) {
            timeLevel = potionMaxTimeLevel;
            powerLevel = potionMaxPowerLevel;
        } else {
            try {
                // If only one level provided, use it for both time and power
                if (args.length == 5) {
                    int level = Integer.parseInt(args[4]);
                    timeLevel = level;
                    powerLevel = level;
                } else if (args.length >= 6) {
                    timeLevel = Integer.parseInt(args[4]);
                    powerLevel = Integer.parseInt(args[5]);
                } else {
                    sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> <potion> [time_lvl] [power_lvl]", NamedTextColor.RED));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Levels must be valid numbers!", NamedTextColor.RED));
                return true;
            } catch (ArrayIndexOutOfBoundsException e) {
                sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> <potion> [time_lvl] [power_lvl]", NamedTextColor.RED));
                return true;
            }
        }
        
        // Validate levels (allow 0 for fly and fortune potions)
        boolean isCustomWithZeroLevels = (potionName.equals("fly") || potionName.equals("fortune")) && (timeLevel == 0 || powerLevel == 0);
        if (!isCustomWithZeroLevels && (timeLevel < 1 || powerLevel < 1)) {
            sender.sendMessage(Component.text("Levels must be 1 or greater!", NamedTextColor.RED));
            return true;
        }
        
        // Use the already-declared potionMaxTimeLevel and potionMaxPowerLevel from above
        Map<Integer, int[]> potionTimeUpgrades = getTimeUpgrades(effectKey);
        Map<Integer, Integer> potionPowerUpgrades = getPowerUpgrades(effectKey);
        
        if (timeLevel > potionMaxTimeLevel) {
            sender.sendMessage(Component.text("Time level cannot exceed " + potionMaxTimeLevel + " for " + potionName + "!", NamedTextColor.RED));
            return true;
        }
        
        if (powerLevel > potionMaxPowerLevel) {
            sender.sendMessage(Component.text("Power level cannot exceed " + potionMaxPowerLevel + " for " + potionName + "!", NamedTextColor.RED));
            return true;
        }
        
        // Get duration from config for the specified time level
        int duration;
        if ((potionName.equals("fly") || potionName.equals("fortune")) && timeLevel == 0) {
            // Custom potions at level 0 have 3 minute (180 second) duration
            duration = 180;
        } else {
            if (!potionTimeUpgrades.containsKey(timeLevel)) {
                sender.sendMessage(Component.text("Invalid time level: " + timeLevel + "! Valid levels are 1-" + potionMaxTimeLevel, NamedTextColor.RED));
                return true;
            }
            duration = potionTimeUpgrades.get(timeLevel)[1];
        }
        
        // Validate power level exists in config (skip for custom potions at level 0)
        if (!((potionName.equals("fly") || potionName.equals("fortune")) && powerLevel == 0)) {
            if (!potionPowerUpgrades.containsKey(powerLevel)) {
                sender.sendMessage(Component.text("Invalid power level: " + powerLevel + "! Valid levels are 1-" + potionMaxPowerLevel, NamedTextColor.RED));
                return true;
            }
        }
        
        // Create the master potion
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        
        // Store all NBT data
        meta.getPersistentDataContainer().set(potionTimeLevelKey, PersistentDataType.INTEGER, timeLevel);
        meta.getPersistentDataContainer().set(potionPowerLevelKey, PersistentDataType.INTEGER, powerLevel);
        meta.getPersistentDataContainer().set(potionDurationKey, PersistentDataType.INTEGER, duration);
        meta.getPersistentDataContainer().set(potionEffectTypeKey, PersistentDataType.STRING, effectKey);
        meta.getPersistentDataContainer().set(masterPotionKey, PersistentDataType.BYTE, (byte) 1);
        
        // Set base potion type to water (removes vanilla text)
        meta.setBasePotionType(PotionType.WATER);
        
        // Set the correct color for this effect type
        if (effectKey.equals("fly")) {
            meta.setColor(org.bukkit.Color.ORANGE);
        } else if (effectKey.equals("fortune")) {
            meta.setColor(org.bukkit.Color.LIME); // Green for luck/fortune
        } else {
            meta.setColor(getPotionColor(effectType));
        }
        
        // Add custom effect (handle fortune separately, skip fly)
        meta.clearCustomEffects();
        if (effectKey.equals("fortune")) {
            // Fortune uses LUCK potion effect
            PotionEffectType luckEffect = PotionEffectType.LUCK;
            meta.addCustomEffect(new PotionEffect(luckEffect, duration * 20, powerLevel, false, false, false), true);
        } else if (!effectKey.equals("fly") && effectType != null) {
            meta.addCustomEffect(new PotionEffect(effectType, duration * 20, powerLevel, false, false, false), true);
        }
        
        // Set display name
        String effectName;
        if (effectKey.equals("fly")) {
            effectName = "Fly";
        } else if (effectKey.equals("fortune")) {
            effectName = "Fortune";
        } else {
            effectName = formatEffectName(effectType);
        }
        int displayLevel = powerLevel + 1; // Power level 0 = I, 1 = II, etc.
        String romanLevel = toRoman(displayLevel);
        
        Component name = Component.text(effectName + " " + romanLevel, NamedTextColor.GOLD, TextDecoration.ITALIC)
            .decoration(TextDecoration.ITALIC, true);
        meta.displayName(name);
        
        // Set lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Master Potion", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
        
        // Calculate max duration (highest duration value in time upgrades)
        int maxDuration = 0;
        for (int[] timeUpgrade : potionTimeUpgrades.values()) {
            if (timeUpgrade[1] > maxDuration) {
                maxDuration = timeUpgrade[1];
            }
        }
        
        // Add fly-specific lore
        if (effectKey.equals("fly")) {
            // Calculate flight speed
            float flightSpeed = 0.1f * (1.0f + (powerLevel * 0.2f));
            flightSpeed = Math.min(flightSpeed, 1.0f);
            int speedPercent = (int)((flightSpeed / 0.1f) * 100);
            
            // Check if at max levels
            boolean atMaxSpeed = powerLevel >= potionMaxPowerLevel;
            boolean atMaxDuration = timeLevel >= potionMaxTimeLevel;
            
            // Flight speed line
            if (atMaxSpeed) {
                lore.add(Component.text("Flight Speed: " + speedPercent + "% (MAX)", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                // Calculate max speed
                float maxFlightSpeed = 0.1f * (1.0f + (potionMaxPowerLevel * 0.2f));
                maxFlightSpeed = Math.min(maxFlightSpeed, 1.0f);
                int maxSpeedPercent = (int)((maxFlightSpeed / 0.1f) * 100);
                lore.add(Component.text("Flight Speed: " + speedPercent + "% (Max: " + maxSpeedPercent + "%)", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            // Duration line
            if (atMaxDuration) {
                lore.add(Component.text("Duration: " + formatDuration(duration) + " (MAX)", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Duration: " + formatDuration(duration) + " (Max: " + formatDuration(maxDuration) + ")", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            // Add upgrade cost lines if not at max
            if (!atMaxSpeed) {
                int nextPowerLevel = powerLevel + 1;
                Integer glowstoneCost = potionPowerUpgrades.get(nextPowerLevel);
                if (glowstoneCost != null) {
                    lore.add(Component.text("Flight Speed Upgrade: " + glowstoneCost + " glowstone dust", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
            if (!atMaxDuration) {
                int nextTimeLevel = timeLevel + 1;
                int[] timeUpgrade = potionTimeUpgrades.get(nextTimeLevel);
                if (timeUpgrade != null) {
                    lore.add(Component.text("Duration Upgrade: " + timeUpgrade[0] + " redstone dust", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
        } else if (effectKey.equals("fortune")) {
            // Check if at max levels
            boolean atMaxLuck = powerLevel >= potionMaxPowerLevel;
            boolean atMaxDuration = timeLevel >= potionMaxTimeLevel;
            
            // Luck line
            if (atMaxLuck) {
                lore.add(Component.text("Luck: +" + (powerLevel + 1) + " (MAX)", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Luck: +" + (powerLevel + 1) + " (Max: +" + (potionMaxPowerLevel + 1) + ")", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            // Duration line
            if (atMaxDuration) {
                lore.add(Component.text("Duration: " + formatDuration(duration) + " (MAX)", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Duration: " + formatDuration(duration) + " (Max: " + formatDuration(maxDuration) + ")", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            // Add upgrade cost lines if not at max
            if (!atMaxLuck) {
                int nextPowerLevel = powerLevel + 1;
                Integer glowstoneCost = potionPowerUpgrades.get(nextPowerLevel);
                if (glowstoneCost != null) {
                    lore.add(Component.text("Luck Upgrade: " + glowstoneCost + " glowstone dust", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
            if (!atMaxDuration) {
                int nextTimeLevel = timeLevel + 1;
                int[] timeUpgrade = potionTimeUpgrades.get(nextTimeLevel);
                if (timeUpgrade != null) {
                    lore.add(Component.text("Duration Upgrade: " + timeUpgrade[0] + " redstone dust", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
        } else {
            // Standard potion lore (for vanilla effects like speed, strength, etc.)
            boolean atMaxPower = powerLevel >= potionMaxPowerLevel;
            boolean atMaxDuration = timeLevel >= potionMaxTimeLevel;
            boolean isInstant = isInstantEffect(effectKey);
            
            // Power line
            String powerLabel = effectName;
            if (atMaxPower) {
                lore.add(Component.text(powerLabel + ": +" + (powerLevel + 1) + " (MAX)", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(powerLabel + ": +" + (powerLevel + 1) + " (Max: +" + (potionMaxPowerLevel + 1) + ")", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            // Duration line (skip for instant effects)
            if (!isInstant) {
                if (atMaxDuration) {
                    lore.add(Component.text("Duration: " + formatDuration(duration) + " (MAX)", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("Duration: " + formatDuration(duration) + " (Max: " + formatDuration(maxDuration) + ")", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                }
            } else {
                // Show "Instant" label for instant effects
                lore.add(Component.text("Duration: Instant", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            // Add upgrade cost lines if not at max
            if (!atMaxPower) {
                int nextPowerLevel = powerLevel + 1;
                Integer glowstoneCost = potionPowerUpgrades.get(nextPowerLevel);
                if (glowstoneCost != null) {
                    lore.add(Component.text(powerLabel + " Upgrade: " + glowstoneCost + " glowstone dust", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
            // Duration upgrade line (skip for instant effects - they can't be upgraded with redstone)
            if (!isInstant && !atMaxDuration) {
                int nextTimeLevel = timeLevel + 1;
                int[] timeUpgrade = potionTimeUpgrades.get(nextTimeLevel);
                if (timeUpgrade != null) {
                    lore.add(Component.text("Duration Upgrade: " + timeUpgrade[0] + " redstone dust", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
        }
        
        meta.lore(lore);
        
        potion.setItemMeta(meta);
        
        target.getInventory().addItem(potion);
        
        sender.sendMessage(Component.text("Gave ", NamedTextColor.GREEN)
            .append(Component.text(target.getName(), NamedTextColor.GOLD))
            .append(Component.text(" a ", NamedTextColor.GREEN))
            .append(Component.text(potionName, NamedTextColor.GOLD))
            .append(Component.text(" potion (Time: ", NamedTextColor.GREEN))
            .append(Component.text(timeLevel + "", NamedTextColor.GOLD))
            .append(Component.text(", Power: ", NamedTextColor.GREEN))
            .append(Component.text(powerLevel + "", NamedTextColor.GOLD))
            .append(Component.text(")", NamedTextColor.GREEN)));
        
        target.sendMessage(Component.text("You received a Master ", NamedTextColor.GREEN)
            .append(Component.text(potionName.substring(0, 1).toUpperCase() + potionName.substring(1), NamedTextColor.GOLD))
            .append(Component.text(" Potion!", NamedTextColor.GREEN)));
        
        return true;
    }
    
    /**
     * Handles /masterbrewing give potion <player> random command
     * OPTIMIZATION #3: Now uses map lookup instead of switch statement
     */
    private boolean handleGiveRandomPotion(CommandSender sender, Player target) {
        // Get random potion name from all available potions
        List<String> potionNames = new ArrayList<>(POTION_NAME_TO_EFFECT_KEY.keySet());
        String potionName = potionNames.get(new Random().nextInt(potionNames.size()));
        String effectKey = POTION_NAME_TO_EFFECT_KEY.get(potionName);
        
        // Get per-potion upgrade paths
        int potionMaxTimeLevel = getMaxTimeLevel(effectKey);
        int potionMaxPowerLevel = getMaxPowerLevel(effectKey);
        Map<Integer, int[]> potionTimeUpgrades = getTimeUpgrades(effectKey);
        Map<Integer, Integer> potionPowerUpgrades = getPowerUpgrades(effectKey);
        
        // Get random time and power levels based on this potion's max levels
        int timeLevel = 1 + new Random().nextInt(potionMaxTimeLevel);
        int powerLevel = 1 + new Random().nextInt(potionMaxPowerLevel);
        
        // Get effect type using NamespacedKey (skip for custom fly and fortune potions)
        PotionEffectType effectType = null;
        if (!effectKey.equals("fly") && !effectKey.equals("fortune")) {
            effectType = PotionEffectType.getByKey(NamespacedKey.minecraft(effectKey));
            if (effectType == null) {
                sender.sendMessage(Component.text("Failed to load random potion effect type: " + effectKey, NamedTextColor.RED));
                return true;
            }
        }
        
        // Get duration from this potion's config
        int duration = potionTimeUpgrades.get(timeLevel)[1];
        
        // Calculate max duration for lore
        int maxDuration = 0;
        for (int[] timeUpgrade : potionTimeUpgrades.values()) {
            if (timeUpgrade[1] > maxDuration) {
                maxDuration = timeUpgrade[1];
            }
        }
        
        // Create the master potion
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        
        // Store all NBT data
        meta.getPersistentDataContainer().set(potionTimeLevelKey, PersistentDataType.INTEGER, timeLevel);
        meta.getPersistentDataContainer().set(potionPowerLevelKey, PersistentDataType.INTEGER, powerLevel);
        meta.getPersistentDataContainer().set(potionDurationKey, PersistentDataType.INTEGER, duration);
        meta.getPersistentDataContainer().set(potionEffectTypeKey, PersistentDataType.STRING, effectKey);
        meta.getPersistentDataContainer().set(masterPotionKey, PersistentDataType.BYTE, (byte) 1);
        
        // Set base potion type to water (removes vanilla text)
        meta.setBasePotionType(PotionType.WATER);
        
        // Set the correct color for this effect type
        if (effectKey.equals("fly")) {
            meta.setColor(org.bukkit.Color.ORANGE);
        } else if (effectKey.equals("fortune")) {
            meta.setColor(org.bukkit.Color.LIME);
        } else {
            meta.setColor(getPotionColor(effectType));
        }
        
        // Add custom effect (handle fortune, skip fly)
        meta.clearCustomEffects();
        if (effectKey.equals("fortune")) {
            PotionEffectType luckEffect = PotionEffectType.LUCK;
            meta.addCustomEffect(new PotionEffect(luckEffect, duration * 20, powerLevel, false, false, false), true);
        } else if (!effectKey.equals("fly")) {
            meta.addCustomEffect(new PotionEffect(effectType, duration * 20, powerLevel, false, false, false), true);
        }
        
        // Set display name
        String effectName;
        if (effectKey.equals("fly")) {
            effectName = "Fly";
        } else if (effectKey.equals("fortune")) {
            effectName = "Fortune";
        } else {
            effectName = formatEffectName(effectType);
        }
        int displayLevel = powerLevel + 1; // Power level 1 = II, 2 = III, etc.
        String romanLevel = toRoman(displayLevel);
        
        Component name = Component.text(effectName + " " + romanLevel, NamedTextColor.GOLD, TextDecoration.ITALIC)
            .decoration(TextDecoration.ITALIC, true);
        meta.displayName(name);
        
        // Set lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Master Potion", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
        
        // Add fly-specific lore
        if (effectKey.equals("fly")) {
            // Calculate flight speed
            float flightSpeed = 0.1f * (1.0f + (powerLevel * 0.2f));
            flightSpeed = Math.min(flightSpeed, 1.0f);
            int speedPercent = (int)((flightSpeed / 0.1f) * 100);
            
            // Check if at max levels
            boolean atMaxSpeed = powerLevel >= potionMaxPowerLevel;
            boolean atMaxDuration = timeLevel >= potionMaxTimeLevel;
            
            // Flight speed line
            if (atMaxSpeed) {
                lore.add(Component.text("Flight Speed: " + speedPercent + "% (MAX)", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                // Calculate max speed
                float maxFlightSpeed = 0.1f * (1.0f + (potionMaxPowerLevel * 0.2f));
                maxFlightSpeed = Math.min(maxFlightSpeed, 1.0f);
                int maxSpeedPercent = (int)((maxFlightSpeed / 0.1f) * 100);
                lore.add(Component.text("Flight Speed: " + speedPercent + "% (Max: " + maxSpeedPercent + "%)", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            // Duration line
            if (atMaxDuration) {
                lore.add(Component.text("Duration: " + formatDuration(duration) + " (MAX)", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Duration: " + formatDuration(duration) + " (Max: " + formatDuration(maxDuration) + ")", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            // Add upgrade cost lines if not at max
            if (!atMaxSpeed) {
                int nextPowerLevel = powerLevel + 1;
                Integer glowstoneCost = potionPowerUpgrades.get(nextPowerLevel);
                if (glowstoneCost != null) {
                    lore.add(Component.text("Flight Speed Upgrade: " + glowstoneCost + " glowstone dust", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
            if (!atMaxDuration) {
                int nextTimeLevel = timeLevel + 1;
                int[] timeUpgrade = potionTimeUpgrades.get(nextTimeLevel);
                if (timeUpgrade != null) {
                    lore.add(Component.text("Duration Upgrade: " + timeUpgrade[0] + " redstone dust", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
        } else if (effectKey.equals("fortune")) {
            // Check if at max levels
            boolean atMaxLuck = powerLevel >= potionMaxPowerLevel;
            boolean atMaxDuration = timeLevel >= potionMaxTimeLevel;
            
            // Luck line
            if (atMaxLuck) {
                lore.add(Component.text("Luck: +" + (powerLevel + 1) + " (MAX)", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Luck: +" + (powerLevel + 1) + " (Max: +" + (potionMaxPowerLevel + 1) + ")", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            // Duration line
            if (atMaxDuration) {
                lore.add(Component.text("Duration: " + formatDuration(duration) + " (MAX)", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Duration: " + formatDuration(duration) + " (Max: " + formatDuration(maxDuration) + ")", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            // Add upgrade cost lines if not at max
            if (!atMaxLuck) {
                int nextPowerLevel = powerLevel + 1;
                Integer glowstoneCost = potionPowerUpgrades.get(nextPowerLevel);
                if (glowstoneCost != null) {
                    lore.add(Component.text("Luck Upgrade: " + glowstoneCost + " glowstone dust", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
            if (!atMaxDuration) {
                int nextTimeLevel = timeLevel + 1;
                int[] timeUpgrade = potionTimeUpgrades.get(nextTimeLevel);
                if (timeUpgrade != null) {
                    lore.add(Component.text("Duration Upgrade: " + timeUpgrade[0] + " redstone dust", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
        } else {
            // Standard potion lore (for vanilla effects like speed, strength, etc.)
            boolean atMaxPower = powerLevel >= potionMaxPowerLevel;
            boolean atMaxDuration = timeLevel >= potionMaxTimeLevel;
            boolean isInstant = isInstantEffect(effectKey);
            
            // Power line
            String powerLabel = effectName;
            if (atMaxPower) {
                lore.add(Component.text(powerLabel + ": +" + (powerLevel + 1) + " (MAX)", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(powerLabel + ": +" + (powerLevel + 1) + " (Max: +" + (potionMaxPowerLevel + 1) + ")", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            // Duration line (skip for instant effects)
            if (!isInstant) {
                if (atMaxDuration) {
                    lore.add(Component.text("Duration: " + formatDuration(duration) + " (MAX)", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("Duration: " + formatDuration(duration) + " (Max: " + formatDuration(maxDuration) + ")", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                }
            } else {
                // Show "Instant" label for instant effects
                lore.add(Component.text("Duration: Instant", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            
            // Add upgrade cost lines if not at max
            if (!atMaxPower) {
                int nextPowerLevel = powerLevel + 1;
                Integer glowstoneCost = potionPowerUpgrades.get(nextPowerLevel);
                if (glowstoneCost != null) {
                    lore.add(Component.text(powerLabel + " Upgrade: " + glowstoneCost + " glowstone dust", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
            // Duration upgrade line (skip for instant effects - they can't be upgraded with redstone)
            if (!isInstant && !atMaxDuration) {
                int nextTimeLevel = timeLevel + 1;
                int[] timeUpgrade = potionTimeUpgrades.get(nextTimeLevel);
                if (timeUpgrade != null) {
                    lore.add(Component.text("Duration Upgrade: " + timeUpgrade[0] + " redstone dust", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
        }
        
        meta.lore(lore);
        
        potion.setItemMeta(meta);
        
        target.getInventory().addItem(potion);
        
        sender.sendMessage(Component.text("Gave ", NamedTextColor.GREEN)
            .append(Component.text(target.getName(), NamedTextColor.GOLD))
            .append(Component.text(" a random ", NamedTextColor.GREEN))
            .append(Component.text(potionName, NamedTextColor.GOLD))
            .append(Component.text(" potion (Time: ", NamedTextColor.GREEN))
            .append(Component.text(timeLevel + "", NamedTextColor.GOLD))
            .append(Component.text(", Power: ", NamedTextColor.GREEN))
            .append(Component.text(powerLevel + "", NamedTextColor.GOLD))
            .append(Component.text(")", NamedTextColor.GREEN)));
        
        target.sendMessage(Component.text("You received a random Master ", NamedTextColor.GREEN)
            .append(Component.text(potionName.substring(0, 1).toUpperCase() + potionName.substring(1), NamedTextColor.GOLD))
            .append(Component.text(" Potion!", NamedTextColor.GREEN)));
        
        return true;
    }
    
    /**
     * Handles /masterbrewing reload command
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("masterbrewing.admin")) {
            sender.sendMessage(Component.text("You don't have permission to reload configuration!", NamedTextColor.RED));
            return true;
        }
        
        reloadConfig();
        loadUpgradeTiers();
        
        sender.sendMessage(Component.text("MasterBrewing configuration reloaded!", NamedTextColor.GREEN));
        
        return true;
    }
    
    /**
     * Opens a virtual Master Brewing Stand inventory for a player
     * This allows players to use the /masterbrewing command to access a brewing GUI
     * Note: Virtual brewing stands support the same upgrade mechanics as placed stands
     * 
     * @param player The player to open the virtual brewing stand for
     */
    private void openVirtualBrewingStand(Player player) {
        org.bukkit.inventory.Inventory brewingInv = Bukkit.createInventory(null, InventoryType.BREWING, 
            Component.text("Master Brewing Stand", NamedTextColor.GOLD, TextDecoration.BOLD));
        
        UUID playerUUID = player.getUniqueId();
        
        // Load player's data from their individual file
        Object[] data = loadPlayerBrewingData(playerUUID);
        ItemStack[] savedContents = (ItemStack[]) data[0];
        int fuelLevel = (Integer) data[1];
        
        // Restore saved contents
        for (int i = 0; i < savedContents.length && i < brewingInv.getSize(); i++) {
            if (savedContents[i] != null) {
                brewingInv.setItem(i, savedContents[i].clone());
            }
        }
        
        // Store fuel level in memory for this session
        if (fuelLevel > 0) {
            virtualBrewingFuel.put(playerUUID, fuelLevel);
        }
        
        // Track this virtual brewing stand by player UUID
        virtualBrewingStands.put(playerUUID, brewingInv);
        
        player.openInventory(brewingInv);
        
        // Update fuel display after a tick (needs inventory to be open first)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            updateFuelDisplay(player);
        }, 1L);
    }
    
    /**
     * Updates the fuel level display in the brewing stand GUI
     * 
     * @param player The player viewing the brewing stand
     */
    private void updateFuelDisplay(Player player) {
        if (player.getOpenInventory() == null) return;
        if (player.getOpenInventory().getTopInventory().getType() != InventoryType.BREWING) return;
        
        UUID playerUUID = player.getUniqueId();
        int fuelLevel = virtualBrewingFuel.getOrDefault(playerUUID, 0);
        
        // FUEL_TIME property displays the fuel bar (0-20)
        player.getOpenInventory().setProperty(org.bukkit.inventory.InventoryView.Property.FUEL_TIME, fuelLevel);
    }
    
    /**
     * Handles inventory close events to save and clean up virtual brewing stands
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        
        // Check if this player has a virtual brewing stand open
        if (!virtualBrewingStands.containsKey(playerUUID)) {
            return;
        }
        
        org.bukkit.inventory.Inventory closedInv = event.getView().getTopInventory();
        
        // Verify it's the same inventory type (brewing)
        if (closedInv.getType() != InventoryType.BREWING) {
            return;
        }
        
        // Save the current contents
        ItemStack[] contents = new ItemStack[closedInv.getSize()];
        for (int i = 0; i < closedInv.getSize(); i++) {
            ItemStack item = closedInv.getItem(i);
            if (item != null) {
                contents[i] = item.clone();
            }
        }
        
        // Get fuel level from memory
        int fuelLevel = virtualBrewingFuel.getOrDefault(playerUUID, 0);
        
        // Save only THIS player's data to their individual file
        savePlayerBrewingData(playerUUID, contents, fuelLevel);
        
        // Clean up in-memory tracking
        virtualBrewingStands.remove(playerUUID);
        virtualBrewingFuel.remove(playerUUID);
    }
    
    /**
     * Handles inventory click events for virtual brewing stands
     * Processes brewing when items are placed in valid configuration
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onVirtualBrewingClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();
        
        // Only handle virtual brewing stands
        if (!virtualBrewingStands.containsKey(playerUUID)) {
            return;
        }
        
        getLogger().info("[VirtualBrew] Click detected in virtual brewing stand for " + player.getName());
        
        // Schedule brewing check after the click is processed
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // Get the player's currently open inventory
            if (player.getOpenInventory() == null) {
                getLogger().info("[VirtualBrew] No open inventory");
                return;
            }
            org.bukkit.inventory.Inventory topInv = player.getOpenInventory().getTopInventory();
            if (topInv == null || topInv.getType() != InventoryType.BREWING) {
                getLogger().info("[VirtualBrew] Not a brewing inventory");
                return;
            }
            
            processVirtualBrewing(topInv, playerUUID);
            
            // Always update fuel display after any click
            updateFuelDisplay(player);
        }, 2L);
    }
    
    /**
     * Handles inventory drag events for virtual brewing stands
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onVirtualBrewingDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();
        
        // Only handle virtual brewing stands
        if (!virtualBrewingStands.containsKey(playerUUID)) {
            return;
        }
        
        // Schedule brewing check after the drag is processed
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.getOpenInventory() == null) return;
            org.bukkit.inventory.Inventory topInv = player.getOpenInventory().getTopInventory();
            if (topInv == null || topInv.getType() != InventoryType.BREWING) return;
            
            processVirtualBrewing(topInv, playerUUID);
            
            // Always update fuel display after any drag
            updateFuelDisplay(player);
        }, 2L);
    }
    
    /**
     * Processes brewing in a virtual Master Brewing Stand
     * Checks if valid ingredients and potions are present, then performs the upgrade
     * 
     * @param inv The virtual brewing stand inventory
     */
    private void processVirtualBrewing(org.bukkit.inventory.Inventory inv, UUID playerUUID) {
        int totalUpgrades = 0;
        
        // Loop until we can't upgrade anymore
        while (true) {
            ItemStack ingredient = inv.getItem(3);
            if (ingredient == null) break;
            
            Material ingredientType = ingredient.getType();
            boolean isRedstone = ingredientType == Material.REDSTONE;
            boolean isGlowstone = ingredientType == Material.GLOWSTONE_DUST;
            if (!isRedstone && !isGlowstone) break;
            
            // Check fuel
            ItemStack fuel = inv.getItem(4);
            int currentFuel = virtualBrewingFuel.getOrDefault(playerUUID, 0);
            boolean hasBlazePowder = (fuel != null && fuel.getType() == Material.BLAZE_POWDER);
            if (currentFuel <= 0 && !hasBlazePowder) break;
            
            // Find potions that can be upgraded and get cost
            int materialCost = -1;
            List<Integer> potionSlots = new ArrayList<>();
            
            for (int slot = 0; slot < 3; slot++) {
                ItemStack potion = inv.getItem(slot);
                int cost = getUpgradeCost(potion, isRedstone, isGlowstone);
                if (cost > 0) {
                    potionSlots.add(slot);
                    if (materialCost < 0) {
                        materialCost = cost;
                    }
                }
            }
            
            if (potionSlots.isEmpty() || materialCost < 0) break;
            if (ingredient.getAmount() < materialCost) break;
            
            // Consume fuel if needed
            if (currentFuel <= 0) {
                fuel.setAmount(fuel.getAmount() - 1);
                if (fuel.getAmount() <= 0) {
                    inv.setItem(4, null);
                    fuel = null;
                } else {
                    inv.setItem(4, fuel);
                }
                currentFuel = 20;
            }
            currentFuel--;
            virtualBrewingFuel.put(playerUUID, currentFuel);
            
            // Consume ingredient
            ingredient.setAmount(ingredient.getAmount() - materialCost);
            if (ingredient.getAmount() <= 0) {
                inv.setItem(3, null);
            } else {
                inv.setItem(3, ingredient);
            }
            
            // Upgrade all potions using the shared helper
            for (int slot : potionSlots) {
                ItemStack potion = inv.getItem(slot);
                if (potion != null) {
                    upgradeMasterPotion(potion, isRedstone, isGlowstone);
                    inv.setItem(slot, potion);
                }
            }
            
            totalUpgrades++;
        }
        
        if (totalUpgrades > 0) {
            getLogger().info("[VirtualBrew] Completed " + totalUpgrades + " upgrade(s)");
        }
        
        // Update fuel display
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            updateFuelDisplay(player);
        }
    }
    
    // ==================== TAB COMPLETION ====================
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("help");
            
            if (sender.hasPermission("masterbrewing.give")) {
                completions.add("give");
            }
            
            if (sender.hasPermission("masterbrewing.admin")) {
                completions.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            completions.add("stand");
            completions.add("potion");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Player name for both stand and potion
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("potion")) {
            // OPTIMIZATION #3: Use map keys instead of hard-coded list
            completions.add("random");
            completions.addAll(POTION_NAME_TO_EFFECT_KEY.keySet());
        } else if (args.length == 5 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("potion")) {
            // Time level suggestions based on config + max
            completions.add("max");
            for (int i = 1; i <= maxTimeLevel; i++) {
                completions.add(String.valueOf(i));
            }
        } else if (args.length == 6 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("potion")) {
            // Power level suggestions based on config
            for (int i = 1; i <= maxPowerLevel; i++) {
                completions.add(String.valueOf(i));
            }
        }
        
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .collect(Collectors.toList());
    }
}