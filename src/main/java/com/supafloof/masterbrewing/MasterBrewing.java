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
 * ==================================================================================
 * MasterBrewing Plugin - Enhanced Potion Brewing System for Minecraft
 * ==================================================================================
 * 
 * This plugin creates a custom brewing system that bypasses vanilla Minecraft's
 * limitations on potion duration and power levels. It introduces "Master Brewing
 * Stands" and "Master Potions" with the following capabilities:
 * 
 * CORE FEATURES:
 * --------------
 * 1. UNLIMITED DURATION UPGRADES
 *    - Use redstone dust to increase potion duration beyond vanilla limits
 *    - Each upgrade tier requires more redstone for longer durations
 *    - Configurable via config.yml (upgrade-time entries)
 *    - Example: Level 1 = 10 minutes, Level 5 = 1 hour, Level 10 = 4 hours
 * 
 * 2. UNLIMITED POWER UPGRADES
 *    - Use glowstone dust to increase potion amplifier (strength)
 *    - Each upgrade tier requires more glowstone
 *    - Configurable via config.yml (upgrade-power entries)
 *    - Example: Speed I → Speed II → Speed III → ... → Speed X
 * 
 * 3. CUSTOM EFFECTS
 *    - FLY: Grants survival flight with configurable speed scaling per level
 *    - FORTUNE: Enhanced luck effect for better loot drops from mobs/chests
 *    - All 30+ vanilla potion effects are also supported
 * 
 * 4. INSTANT ACTIVATION
 *    - Master potions activate instantly on right-click (no drinking animation)
 *    - Prevents vanilla consumption mechanics from interfering
 *    - Better for combat situations where timing matters
 * 
 * 5. EFFECT PERSISTENCE
 *    - Active effects survive player logout/login
 *    - Effects persist through server restarts
 *    - Stored in per-player YAML files in plugins/MasterBrewing/playerdata/
 * 
 * 6. CONTINUOUS EFFECT REFRESH
 *    - Background task refreshes effects every 3 seconds
 *    - Bypasses vanilla's maximum duration limit (~10 minutes)
 *    - Shows expiration warnings at 30s and 10s remaining
 *    - Flight shows duration on action bar (HUD above hotbar)
 * 
 * 7. VIRTUAL BREWING STANDS
 *    - Players can access brewing via /masterbrewing command (no physical block)
 *    - Inventory contents persist per-player across sessions
 *    - Supports same upgrade mechanics as physical stands
 * 
 * 8. PER-POTION CONFIGURATION
 *    - Individual potions can have custom upgrade paths in config
 *    - Override global costs/durations for specific effects
 *    - Allows balancing (e.g., fly costs more than speed)
 * 
 * HOW BREWING WORKS:
 * ------------------
 * 1. Obtain a Master Brewing Stand via /masterbrewing give stand <player>
 * 2. Place the stand and insert any vanilla potion with an effect
 * 3. Add redstone dust to increase duration OR glowstone to increase power
 * 4. The brew completes instantly, consuming materials based on config tier
 * 5. The potion transforms into a Master Potion with upgraded stats
 * 6. Master Potions display with gold italic names and detailed lore
 * 7. Right-click to instantly consume and gain the effect
 * 
 * NBT DATA STRUCTURE:
 * -------------------
 * Master Brewing Stand items/blocks store:
 * - masterbrewing:master_brewing_stand (BYTE=1) - Marker identifying master stands
 * - When broken: Inventory slots serialized as JSON strings (brewingSlot0-4)
 * - masterbrewing:brewing_fuel_level (INT) - Remaining fuel charges (0-20)
 * 
 * Master Potions store:
 * - masterbrewing:master_potion (BYTE=1) - Marker identifying master potions
 * - masterbrewing:potion_time_level (INT) - Current duration upgrade level (0=base)
 * - masterbrewing:potion_power_level (INT) - Current amplifier level (0=level I)
 * - masterbrewing:potion_duration (INT) - Total duration in seconds
 * - masterbrewing:potion_effect_type (STRING) - Effect key (e.g., "speed", "fly")
 * 
 * CONFIG FORMAT (config.yml):
 * ---------------------------
 * upgrade-time:                    # Global duration upgrades (all potions)
 *   - "1,4,600"                    # Level 1: 4 redstone = 600 seconds (10 min)
 *   - "2,8,1200"                   # Level 2: 8 redstone = 1200 seconds (20 min)
 *   - "3,16,2400"                  # Level 3: 16 redstone = 2400 seconds (40 min)
 * 
 * upgrade-power:                   # Global power upgrades (all potions)
 *   - "1,4"                        # Level 1: 4 glowstone = amplifier 1 (II)
 *   - "2,8"                        # Level 2: 8 glowstone = amplifier 2 (III)
 *   - "3,16"                       # Level 3: 16 glowstone = amplifier 3 (IV)
 * 
 * speed:                           # Per-potion override example (optional)
 *   upgrade-time:
 *     - "1,2,300"                  # Speed has cheaper/shorter time upgrades
 *   upgrade-power:
 *     - "1,2"                      # Speed has cheaper power upgrades
 * 
 * PERMISSIONS:
 * ------------
 * masterbrewing.use   - Access /masterbrewing command (virtual brewing stand)
 * masterbrewing.give  - Use /masterbrewing give commands (admin)
 * masterbrewing.admin - Use /masterbrewing reload command
 * 
 * COMMANDS:
 * ---------
 * /masterbrewing              - Open virtual Master Brewing Stand GUI
 * /masterbrewing help         - Show help menu with upgrade tier tables
 * /masterbrewing give stand <player>  - Give Master Brewing Stand item
 * /masterbrewing give potion <player> <type> [time] [power] - Give Master Potion
 * /masterbrewing give potion <player> <type> max - Give max-level potion
 * /masterbrewing give potion <player> random     - Give random potion type/levels
 * /masterbrewing reload       - Reload configuration from disk
 * 
 * INTEGRATION NOTES:
 * ------------------
 * - Compatible with SpecialBooks plugin auto-pickup enchantment
 * - Uses Paper/Spigot API with Adventure components for text formatting
 * - Stores data using Bukkit's PersistentDataContainer (PDC) API
 * - Uses Gson library for JSON serialization of ItemStacks
 * - Effect persistence uses YAML configuration files
 * 
 * @author SupaFloof Games, LLC
 * @version 1.0.0
 * @see <a href="https://playmc.supafloof.com">SupaFloof Minecraft Server</a>
 */
public class MasterBrewing extends JavaPlugin implements Listener, TabCompleter {
    
    // ==================================================================================
    // NAMESPACED KEYS - Used for storing custom NBT data on items and blocks
    // ==================================================================================
    // All keys use format "masterbrewing:key_name" to ensure uniqueness across plugins.
    // These keys are used with Bukkit's PersistentDataContainer (PDC) API.
    
    /**
     * NBT key to mark an item or block as a Master Brewing Stand.
     * Stored as BYTE with value 1 (true).
     * Used on both ItemStack (inventory) and BlockState (placed block).
     */
    private NamespacedKey masterBrewingStandKey;
    
    /**
     * NBT key storing the current TIME (duration) upgrade level on a Master Potion.
     * Stored as INTEGER. Value 0 = unupgraded base potion, 1+ = upgraded.
     * This level determines which duration config entry to use.
     */
    private NamespacedKey potionTimeLevelKey;
    
    /**
     * NBT key storing the current POWER (amplifier) upgrade level on a Master Potion.
     * Stored as INTEGER. Value 0 = base amplifier (level I), 1 = amplifier 1 (level II), etc.
     * Directly maps to PotionEffect amplifier parameter (0-based internally).
     */
    private NamespacedKey potionPowerLevelKey;
    
    /**
     * NBT key storing the total duration of a Master Potion in SECONDS.
     * Stored as INTEGER. This is the actual duration applied when consumed.
     * Calculated from config based on time upgrade level.
     */
    private NamespacedKey potionDurationKey;
    
    /**
     * NBT key storing the effect type identifier of a Master Potion.
     * Stored as STRING (e.g., "speed", "strength", "fly", "fortune").
     * Used to look up PotionEffectType and determine which upgrade path to use.
     */
    private NamespacedKey potionEffectTypeKey;
    
    /**
     * NBT key marking an item as a Master Potion (vs regular vanilla potion).
     * Stored as BYTE with value 1 (true).
     * Enables instant-use behavior and prevents vanilla potion consumption.
     */
    private NamespacedKey masterPotionKey;
    
    // ==================================================================================
    // BREWING STAND INVENTORY PERSISTENCE KEYS
    // ==================================================================================
    // When a Master Brewing Stand block is broken, we serialize its inventory contents
    // to the dropped item's NBT. This allows restoration when placed again.
    // Each inventory slot gets its own key storing a JSON-serialized ItemStack.
    
    /** NBT key for brewing stand slot 0 (left potion bottle). Stored as JSON STRING. */
    private NamespacedKey brewingSlot0Key;
    
    /** NBT key for brewing stand slot 1 (middle potion bottle). Stored as JSON STRING. */
    private NamespacedKey brewingSlot1Key;
    
    /** NBT key for brewing stand slot 2 (right potion bottle). Stored as JSON STRING. */
    private NamespacedKey brewingSlot2Key;
    
    /** NBT key for brewing stand slot 3 (ingredient slot - top center). Stored as JSON STRING. */
    private NamespacedKey brewingSlot3Key;
    
    /** NBT key for brewing stand slot 4 (fuel slot - blaze powder). Stored as JSON STRING. */
    private NamespacedKey brewingSlot4Key;
    
    /** NBT key storing remaining fuel level (0-20) when stand is broken. Stored as INTEGER. */
    private NamespacedKey brewingFuelLevelKey;
    
    // ==================================================================================
    // RUNTIME DATA STRUCTURES - In-memory tracking for active sessions
    // ==================================================================================
    
    /**
     * Tracks active Master Potion effects for each online player.
     * Map structure: Player UUID -> List of ActiveMasterEffect objects
     * 
     * This map is the authoritative source for what effects a player has.
     * The background refresh task reads this map every 3 seconds to:
     * - Check for expired effects and clean them up
     * - Refresh effects that are close to expiring
     * - Show flight status on action bar
     * 
     * Lifecycle:
     * - Entry added when player consumes a Master Potion
     * - Entry removed when all effects expire or player logs out
     * - Loaded from disk (playerdata/*.yml) on player join
     * - Saved to disk on player quit and plugin disable
     * 
     * Thread safety: All access occurs on main Bukkit thread (no sync needed)
     */
    private Map<UUID, List<ActiveMasterEffect>> activeMasterEffects = new HashMap<>();
    
    /**
     * Tracks currently open virtual Master Brewing Stand inventories.
     * Map structure: Player UUID -> Bukkit Inventory object
     * 
     * Virtual brewing stands are opened via /masterbrewing command without
     * requiring a physical block. This map tracks which players have them open
     * so we can process brewing operations on inventory click events.
     * 
     * Lifecycle:
     * - Entry added when player runs /masterbrewing (opens GUI)
     * - Entry removed when player closes the inventory
     * - Contents saved to playerdata file on close
     */
    private Map<UUID, org.bukkit.inventory.Inventory> virtualBrewingStands = new HashMap<>();
    
    /**
     * Tracks fuel levels for currently open virtual brewing stands.
     * Map structure: Player UUID -> Remaining fuel charges (0-20)
     * 
     * Fuel is consumed during brewing (1 charge per brew operation).
     * One blaze powder provides 20 fuel charges (same as vanilla).
     * 
     * IMPORTANT: This map only holds data while a virtual stand is OPEN.
     * Data is loaded from disk when stand is opened, saved when closed.
     * This prevents memory bloat from storing fuel for all players.
     */
    private Map<UUID, Integer> virtualBrewingFuel = new HashMap<>();
    
    // ==================================================================================
    // CONFIGURATION DATA - Parsed from config.yml on enable/reload
    // ==================================================================================
    
    /**
     * Global time (duration) upgrade tiers applying to all potions by default.
     * Map structure: Upgrade level -> [redstone cost, duration in seconds]
     * 
     * Example entries from config:
     * - Level 1: [4, 600]   = 4 redstone dust for 600 seconds (10 minutes)
     * - Level 5: [16, 3600] = 16 redstone dust for 3600 seconds (1 hour)
     * 
     * Using TreeMap ensures levels iterate in ascending order (1, 2, 3, ...).
     * Individual potions can override these via potionUpgradePaths map.
     */
    private Map<Integer, int[]> timeUpgrades = new TreeMap<>();
    
    /**
     * Global power (amplifier) upgrade tiers applying to all potions by default.
     * Map structure: Upgrade level -> glowstone dust cost
     * 
     * Example entries from config:
     * - Level 1: 4 glowstone  (results in amplifier 1, displayed as "II")
     * - Level 5: 16 glowstone (results in amplifier 5, displayed as "VI")
     * 
     * Power upgrades don't have a duration component - they only increase
     * the amplifier/strength of the effect.
     */
    private Map<Integer, Integer> powerUpgrades = new TreeMap<>();
    
    /**
     * Per-potion custom upgrade paths that override global defaults.
     * Map structure: Effect key (e.g., "speed") -> UpgradePath object
     * 
     * Allows individual potions to have different costs/durations:
     * - Fly potions might cost more than speed potions
     * - Healing potions (instant) only support power upgrades
     * - Specific potions can have unique tier structures
     * 
     * If a potion isn't in this map, it uses global timeUpgrades/powerUpgrades.
     */
    private Map<String, UpgradePath> potionUpgradePaths = new HashMap<>();
    
    /**
     * Highest time upgrade level available across ALL configurations.
     * Calculated during config loading by finding max level in global + per-potion paths.
     * Used for command validation and tab completion suggestions.
     */
    private int maxTimeLevel = 0;
    
    /**
     * Highest power upgrade level available across ALL configurations.
     * Calculated during config loading by finding max level in global + per-potion paths.
     * Used for command validation and tab completion suggestions.
     */
    private int maxPowerLevel = 0;
    
    // ==================================================================================
    // STATIC LOOKUP TABLES - Initialized once at class load for O(1) lookups
    // ==================================================================================
    
    /**
     * Maps user-friendly potion names to internal Minecraft effect keys.
     * Provides O(1) lookup performance instead of O(n) switch statements.
     * 
     * Key = User input name (command argument, e.g., "healing", "leaping")
     * Value = Minecraft effect key (e.g., "instant_health", "jump_boost")
     * 
     * Special custom effect entries:
     * - "fortune" -> "fortune" (uses LUCK internally but displayed as Fortune)
     * - "fly" -> "fly" (custom effect, not a vanilla PotionEffectType)
     * 
     * This map is immutable after static initialization for thread safety.
     */
    private static final Map<String, String> POTION_NAME_TO_EFFECT_KEY = createPotionNameMap();
    
    /**
     * Set of effect keys that are "instant" effects with no meaningful duration.
     * Instant effects cannot be upgraded with redstone (time/duration upgrades).
     * 
     * Contents: instant_health, instant_damage, saturation
     * 
     * Why these are instant:
     * - instant_health: Heals immediately when consumed
     * - instant_damage: Damages immediately when consumed  
     * - saturation: Restores hunger instantly
     * 
     * This set is immutable after static initialization.
     */
    private static final Set<String> INSTANT_EFFECTS = createInstantEffectsSet();
    
    /**
     * Creates the immutable set of instant effect keys.
     * 
     * Instant effects have no duration and cannot be upgraded with redstone.
     * They apply their effect immediately upon consumption.
     * 
     * Why each effect is classified as instant:
     * - instant_health: Heals the player immediately (no ongoing effect)
     * - instant_damage: Damages the player immediately (no ongoing effect)
     * - saturation: Restores hunger bars instantly (technically has duration but negligible)
     * 
     * @return Unmodifiable Set containing instant effect key strings
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
     * Creates the immutable mapping of user-friendly potion names to internal effect keys.
     * 
     * This map serves multiple purposes:
     * 1. Validates user input in /masterbrewing give potion commands
     * 2. Translates user-friendly names to Minecraft's internal effect names
     * 3. Provides the list of available potions for tab completion
     * 
     * Translation examples (user name → internal key):
     * - "healing" → "instant_health" (Minecraft uses different internal name)
     * - "leaping" → "jump_boost" (Minecraft uses different internal name)
     * - "speed" → "speed" (same name, but still needed for validation)
     * 
     * Custom effects not in vanilla:
     * - "fortune" → "fortune" (displays as Fortune, uses LUCK effect internally)
     * - "fly" → "fly" (custom flight effect, no PotionEffectType equivalent)
     * 
     * @return Unmodifiable Map of potion names to effect keys
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
     * Holds the complete upgrade path configuration for a specific potion type.
     * 
     * Each UpgradePath contains all the information needed to upgrade a potion:
     * - timeUpgrades: Map of level -> [redstone cost, duration seconds]
     * - powerUpgrades: Map of level -> glowstone cost
     * - maxTimeLevel: Highest available time upgrade for this potion
     * - maxPowerLevel: Highest available power upgrade for this potion
     * 
     * Usage:
     * Potions can have custom upgrade paths defined in config.yml that differ
     * from the global defaults. This allows balancing individual potions:
     * - Fly potions might cost more redstone/glowstone
     * - Speed potions might have more upgrade tiers
     * - Healing potions might only support power upgrades (instant effect)
     * 
     * The upgrade maps use the same format as global maps, allowing seamless
     * fallback to defaults when a potion doesn't have custom configuration.
     */
    private static class UpgradePath {
        /** Time (duration) upgrades: level -> [redstone cost, duration in seconds] */
        Map<Integer, int[]> timeUpgrades;
        
        /** Power (amplifier) upgrades: level -> glowstone cost */
        Map<Integer, Integer> powerUpgrades;
        
        /** Highest time upgrade level available in this path */
        int maxTimeLevel;
        
        /** Highest power upgrade level available in this path */
        int maxPowerLevel;
        
        /**
         * Constructs an UpgradePath and automatically calculates max levels.
         * 
         * @param timeUpgrades Map of time upgrade level -> [cost, duration]
         * @param powerUpgrades Map of power upgrade level -> cost
         */
        UpgradePath(Map<Integer, int[]> timeUpgrades, Map<Integer, Integer> powerUpgrades) {
            this.timeUpgrades = timeUpgrades;
            this.powerUpgrades = powerUpgrades;
            // Calculate max levels by finding highest key in each map
            this.maxTimeLevel = timeUpgrades.isEmpty() ? 0 : Collections.max(timeUpgrades.keySet());
            this.maxPowerLevel = powerUpgrades.isEmpty() ? 0 : Collections.max(powerUpgrades.keySet());
        }
    }
    
    /**
     * Represents a single active Master Potion effect on a player.
     * 
     * This class tracks all information needed to maintain an effect:
     * - effectTypeKey: What effect is active (e.g., "speed", "fly", "fortune")
     * - amplifier: How strong it is (0 = level I, 1 = level II, etc.)
     * - expiryTime: When it expires (milliseconds since epoch, System.currentTimeMillis() format)
     * 
     * Used by the background refresh task to:
     * - Determine if an effect has expired (currentTime >= expiryTime)
     * - Calculate remaining duration for warnings and display
     * - Reapply the vanilla effect with correct amplifier
     * - Handle special effects like flight
     * 
     * Serialization format for disk storage (playerdata/*.yml):
     * "effectTypeKey,amplifier,expiryTime" as a comma-separated string
     * Example: "speed,3,1699459200000" = Speed IV expiring at that timestamp
     */
    private static class ActiveMasterEffect {
        /** Effect identifier string (e.g., "speed", "fly", "fortune", "regeneration") */
        String effectTypeKey;
        
        /** Amplifier level (0-based: 0 = level I, 1 = level II, etc.) */
        int amplifier;
        
        /** System time in milliseconds when this effect should expire */
        long expiryTime;
        
        /**
         * Constructs an ActiveMasterEffect with all required tracking data.
         * 
         * @param effectTypeKey The effect identifier (e.g., "speed", "fly")
         * @param amplifier The amplifier level (0-based, 0 = level I)
         * @param expiryTime Expiration timestamp (System.currentTimeMillis() format)
         */
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
        // Save any currently open virtual brewing stands FIRST
        // This must happen before saving effects to prevent race conditions
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
        
        // Save all active player effects to disk for persistence
        // Use synchronous save during shutdown - async tasks may not complete
        for (UUID uuid : activeMasterEffects.keySet()) {
            List<ActiveMasterEffect> effects = activeMasterEffects.get(uuid);
            savePlayerEffectsSync(uuid, effects);
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
     * Checks if a location has a Master Brewing Stand.
     * OPTIMIZATION #1: Uses block's PersistentDataContainer for O(1) lookup.
     */
    
    // ==================================================================================
    // EVENT HANDLERS - Block placement, breaking, brewing, and potion consumption
    // ==================================================================================
    
    /**
     * Handles placement of Master Brewing Stand items.
     * 
     * When a player places an item that is a Master Brewing Stand:
     * 1. Verify the placed item has the master_brewing_stand NBT marker
     * 2. Schedule a delayed task (1 tick) to allow block state initialization
     * 3. Mark the placed BLOCK with our NBT marker (transfers from item to block)
     * 4. Restore saved inventory contents if stand was previously broken with items
     * 5. Restore saved fuel level
     * 6. Send confirmation message to the player
     * 
     * Why the 1-tick delay?
     * - Block state isn't fully initialized during BlockPlaceEvent
     * - Attempting to modify it immediately may fail or be overwritten
     * - Delayed task ensures block is ready for NBT operations
     * 
     * Inventory restoration process:
     * - Check if item NBT contains serialized slot data (JSON strings)
     * - Deserialize each slot using Gson
     * - Set each item in the brewing stand's inventory
     * - This preserves potions/ingredients when stand is picked up and replaced
     * 
     * @param event The block place event from Bukkit
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
     * Handles breaking of Master Brewing Stand blocks.
     * 
     * When a player breaks a block that is a Master Brewing Stand:
     * 1. Check if the block has our master_brewing_stand NBT marker
     * 2. Cancel the vanilla break event (prevents normal brewing stand drop)
     * 3. Create a new Master Brewing Stand item with our NBT marker
     * 4. Serialize and save all inventory contents to the item's NBT
     * 5. Save the current fuel level to the item's NBT
     * 6. Handle SpecialBooks auto-pickup compatibility
     * 7. Drop the item or add to player's inventory
     * 8. Remove the block
     * 
     * Why cancel the event?
     * - Prevents vanilla from dropping a regular (non-master) brewing stand
     * - Prevents SpecialBooks plugin from creating duplicate drops
     * - Gives us full control over what item drops and its NBT data
     * 
     * SpecialBooks compatibility:
     * - Checks if player's tool has the "auto_pickup" NBT from SpecialBooks
     * - If present: Add item directly to inventory (same as SpecialBooks behavior)
     * - If not present: Drop item naturally at block location
     * - Overflow items drop at player location if inventory is full
     * 
     * @param event The block break event from Bukkit
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
     * Handles brewing completion events in Master Brewing Stands.
     * 
     * This method intercepts vanilla brewing and applies our custom upgrade logic:
     * 1. Check if the brewing stand block has our master_brewing_stand NBT marker
     * 2. Check if the ingredient is redstone (time upgrade) or glowstone (power upgrade)
     * 3. If neither, allow vanilla brewing to proceed normally
     * 4. If upgrade ingredient: Cancel vanilla brewing to prevent unwanted results
     * 5. Store current potion states before any modification
     * 6. Schedule task to restore potions and apply our master brewing logic
     * 
     * Why cancel vanilla brewing for redstone/glowstone?
     * - Vanilla has its own recipes: Redstone → Long potion, Glowstone → Strong potion
     * - These would conflict with our upgrade system
     * - Example: Redstone on Speed potion would make "Long Speed" not "Upgraded Speed"
     * - Canceling lets us apply our custom tier-based upgrades instead
     * 
     * Event priority is LOWEST to run before other plugins that might modify brewing.
     * 
     * @param event The brew event from Bukkit
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
     * Processes master brewing upgrade logic for a brewing stand inventory.
     * 
     * This is the core upgrade method that handles the actual brewing:
     * 1. Scan all 3 potion slots (0, 1, 2) for upgradeable potions
     * 2. Calculate material cost based on the first valid potion's next upgrade tier
     * 3. Verify sufficient materials are present in ingredient slot
     * 4. Apply the upgrade to ALL valid potions in the stand
     * 5. Consume the required materials from ingredient slot
     * 
     * Material cost determination:
     * - Based on the NEXT level's config entry for the potion
     * - Current level 0 upgrading to 1: uses level 1 cost from config
     * - Current level 2 upgrading to 3: uses level 3 cost from config
     * - Cost is taken from potion-specific config if defined, else global config
     * 
     * All potions share the same cost (uses first potion's cost for all).
     * This matches vanilla brewing behavior where all 3 bottles are processed together.
     * 
     * @param inv The brewing stand inventory containing potions and ingredient
     * @param ingredient The ingredient ItemStack (redstone or glowstone)
     * @param isRedstone true if upgrading duration (time level)
     * @param isGlowstone true if upgrading power (amplifier level)
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
     * Extracts the base duration from a vanilla potion for initial upgrade calculations.
     * 
     * When a vanilla (non-master) potion is first upgraded, we need to know its
     * starting duration. This method determines that duration by:
     * 
     * 1. First checking custom effects on the PotionMeta (most accurate)
     * 2. Falling back to vanilla potion type name-based defaults
     * 
     * Vanilla duration conventions:
     * - LONG_ variants (e.g., LONG_SWIFTNESS): 8 minutes (480 seconds)
     * - STRONG_ variants (e.g., STRONG_SWIFTNESS): Usually 1.5-3 minutes
     * - Regular variants: 3 minutes (180 seconds)
     * - Instant effects (INSTANT_HEALTH, INSTANT_DAMAGE): 1 second (immediate)
     * 
     * @param meta The PotionMeta to extract duration from
     * @param effectType The PotionEffectType to look for in custom effects
     * @return Duration in seconds (defaults to 180 if unable to determine)
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
     * Handles right-clicking Master Potions for instant activation.
     * 
     * Master potions activate INSTANTLY on right-click without requiring the
     * vanilla drinking animation. This provides several benefits:
     * - Better combat usability (no 1.6 second drink delay)
     * - Consistent activation timing
     * - Works for custom effects like "fly" that have no vanilla consume behavior
     * 
     * Process flow:
     * 1. Verify action is RIGHT_CLICK_AIR or RIGHT_CLICK_BLOCK
     * 2. Verify held item is a potion with master_potion NBT marker
     * 3. Cancel the vanilla interaction event (prevents drink animation)
     * 4. Read effect data from potion's NBT (type, power level, duration)
     * 5. Calculate expiry time and add to activeMasterEffects tracking
     * 6. Apply the effect immediately:
     *    - For "fly": Enable flight via setAllowFlight/setFlying
     *    - For "fortune": Apply LUCK effect with custom amplifier
     *    - For vanilla effects: Apply via player.addPotionEffect()
     * 7. Consume one potion from the stack
     * 8. Play drink sound and show activation message
     * 
     * The effect is then maintained by the background refresh task which
     * runs every 3 seconds to keep effects active beyond vanilla limits.
     * 
     * @param event The player interact event from Bukkit
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
     * Prevents vanilla consumption of Master Potions.
     * 
     * This is a safety handler that catches any case where vanilla might try
     * to consume a master potion through normal drinking mechanics:
     * - Player holds right-click long enough to trigger vanilla consume
     * - Another plugin triggers potion consumption
     * - Edge cases not caught by onPlayerInteract
     * 
     * Master potions should ONLY activate through our instant-use handler
     * (onPlayerInteract), not through vanilla's drinking system, because:
     * - We need to track the effect in activeMasterEffects
     * - Vanilla would apply wrong duration/amplifier
     * - Custom effects like "fly" have no vanilla behavior
     * 
     * @param event The player item consume event from Bukkit
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
     * Saves player's active Master Potion effects when they disconnect.
     * 
     * When a player logs out:
     * 1. Get their active effects from the tracking map BEFORE removing
     * 2. Save effects to disk (async) so they persist
     * 3. Remove player from active tracking map to free memory
     * 
     * The effects will be restored when the player joins again via onPlayerJoin.
     * 
     * Note: Effects continue counting down while player is offline!
     * The expiryTime is an absolute timestamp, so if a player logs out with
     * 5 minutes remaining and returns 3 minutes later, they'll have 2 minutes left.
     * If they return after 5+ minutes, the effect will have expired.
     * 
     * @param event The player quit event from Bukkit
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
     * Restores player's active Master Potion effects when they reconnect.
     * 
     * When a player logs in:
     * 1. Load their saved effects from playerdata/{uuid}.yml
     * 2. Filter out any effects that expired while they were offline
     * 3. Add remaining effects to the active tracking map
     * 4. Schedule a task (1 tick delay) to restore effects after player loads:
     *    - Flight: Re-enable setAllowFlight, setFlying, and correct flight speed
     *    - Vanilla effects: Reapply via addPotionEffect with remaining duration
     * 5. Show restoration message with remaining time
     * 
     * The 1-tick delay is necessary because:
     * - Player object isn't fully initialized during PlayerJoinEvent
     * - setFlying() may fail if called too early
     * - Ensures player is fully loaded into the world first
     * 
     * @param event The player join event from Bukkit
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
     * Saves a player's active Master Potion effects to their playerdata file.
     * 
     * File location: plugins/MasterBrewing/playerdata/{uuid}.yml
     * 
     * Serialization format:
     * ```yaml
     * active-effects:
     *   - "speed,3,1699459200000"
     *   - "fly,1,1699460100000"
     * ```
     * Each entry is: "effectTypeKey,amplifier,expiryTime" (comma-separated)
     * 
     * This method runs ASYNCHRONOUSLY to prevent blocking the main thread
     * during disk I/O. File operations are slow and shouldn't delay gameplay.
     * 
     * If the effects list is null or empty, any existing file is deleted
     * to clean up stale data.
     * 
     * @param uuid The player's UUID
     * @param effects List of active effects to save (may be null or empty)
     */
    private void savePlayerEffects(UUID uuid, List<ActiveMasterEffect> effects) {
        // OPTIMIZATION #4: Run file I/O asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            File playerDataFolder = new File(getDataFolder(), "playerdata");
            if (!playerDataFolder.exists()) {
                playerDataFolder.mkdirs();
            }
            
            File playerFile = new File(playerDataFolder, uuid.toString() + ".yml");
            
            // Load existing config to preserve brewing slot data
            org.bukkit.configuration.file.YamlConfiguration config;
            if (playerFile.exists()) {
                config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(playerFile);
            } else {
                config = new org.bukkit.configuration.file.YamlConfiguration();
            }
            
            // Check if there's any brewing content to preserve
            boolean hasBrewingContent = false;
            for (int i = 0; i < 5; i++) {
                if (config.contains("slot" + i)) {
                    hasBrewingContent = true;
                    break;
                }
            }
            hasBrewingContent = hasBrewingContent || config.getInt("fuel", 0) > 0;
            
            if (effects == null || effects.isEmpty()) {
                // Clear active-effects from config
                config.set("active-effects", null);
                
                // Only delete file if there's also no brewing content
                if (!hasBrewingContent) {
                    if (playerFile.exists()) {
                        playerFile.delete();
                    }
                    return;
                }
                
                // Save the file with brewing content preserved (but no effects)
                try {
                    config.save(playerFile);
                } catch (Exception e) {
                    getLogger().warning("Failed to save player data for " + uuid + ": " + e.getMessage());
                }
                return;
            }
            
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
     * Saves a player's active Master Potion effects SYNCHRONOUSLY.
     * 
     * This is used during plugin disable (server shutdown) when async tasks
     * may not complete reliably. Preserves any existing brewing stand data
     * in the player's data file.
     * 
     * @param uuid The player's UUID
     * @param effects List of active effects to save (may be null or empty)
     */
    private void savePlayerEffectsSync(UUID uuid, List<ActiveMasterEffect> effects) {
        File playerDataFolder = new File(getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
        
        File playerFile = new File(playerDataFolder, uuid.toString() + ".yml");
        
        // Load existing config to preserve brewing slot data
        org.bukkit.configuration.file.YamlConfiguration config;
        if (playerFile.exists()) {
            config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(playerFile);
        } else {
            config = new org.bukkit.configuration.file.YamlConfiguration();
        }
        
        // Check if there's any brewing content to preserve
        boolean hasBrewingContent = false;
        for (int i = 0; i < 5; i++) {
            if (config.contains("slot" + i)) {
                hasBrewingContent = true;
                break;
            }
        }
        hasBrewingContent = hasBrewingContent || config.getInt("fuel", 0) > 0;
        
        if (effects == null || effects.isEmpty()) {
            // Clear active-effects from config
            config.set("active-effects", null);
            
            // Only delete file if there's also no brewing content
            if (!hasBrewingContent) {
                if (playerFile.exists()) {
                    playerFile.delete();
                }
                return;
            }
            
            // Save the file with brewing content preserved (but no effects)
            try {
                config.save(playerFile);
            } catch (Exception e) {
                getLogger().warning("Failed to save player data for " + uuid + ": " + e.getMessage());
            }
            return;
        }
        
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
    }
    
    /**
     * Loads a player's active Master Potion effects from their playerdata file.
     * 
     * File location: plugins/MasterBrewing/playerdata/{uuid}.yml
     * 
     * Process:
     * 1. Check if playerdata file exists for this UUID
     * 2. Read the "active-effects" list from YAML
     * 3. Parse each effect string: "effectTypeKey,amplifier,expiryTime"
     * 4. Skip any effects that have already expired (expiryTime <= currentTime)
     * 5. Validate that effect types are real (except custom fly/fortune)
     * 6. Add valid effects to the activeMasterEffects tracking map
     * 
     * Error handling:
     * - Missing file: Silently returns (player has no saved effects)
     * - Invalid format: Logs warning and skips that entry
     * - Unknown effect type: Logs warning and skips that entry
     * - Expired effects: Silently skipped (normal behavior)
     * 
     * Note: This runs synchronously on the main thread during player join.
     * File reads are fast enough that async isn't needed here.
     * 
     * @param uuid The player's UUID to load effects for
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
     * Saves a player's virtual brewing stand contents to their playerdata file.
     * 
     * IMPORTANT: This method must preserve existing "active-effects" data in the file!
     * The playerdata file contains BOTH brewing stand data AND active potion effects.
     * We load the existing config first to avoid overwriting effect data.
     * 
     * File structure (plugins/MasterBrewing/playerdata/{uuid}.yml):
     * ```yaml
     * active-effects:       # Preserved from existing file
     *   - "speed,3,1699459200000"
     * slot0: "base64..."    # Left potion bottle (Base64 ItemStack)
     * slot1: "base64..."    # Middle potion bottle
     * slot2: "base64..."    # Right potion bottle
     * slot3: "base64..."    # Ingredient slot
     * slot4: "base64..."    # Fuel slot (blaze powder)
     * fuel: 15              # Remaining fuel charges (0-20)
     * ```
     * 
     * ItemStacks are serialized using Bukkit's BukkitObjectOutputStream to Base64.
     * This preserves all NBT data including our custom master potion tags.
     * 
     * If no brewing content exists AND no active effects exist, the file is deleted.
     * 
     * @param playerUUID The player's UUID
     * @param contents Array of 5 ItemStacks (slots 0-4, may contain nulls)
     * @param fuelLevel Current fuel level (0-20)
     */
    private void savePlayerBrewingData(UUID playerUUID, ItemStack[] contents, int fuelLevel) {
        File playerFile = new File(getDataFolder(), "playerdata/" + playerUUID.toString() + ".yml");
        
        // Check if there's any brewing content to save
        boolean hasBrewingContent = false;
        for (ItemStack item : contents) {
            if (item != null) {
                hasBrewingContent = true;
                break;
            }
        }
        hasBrewingContent = hasBrewingContent || fuelLevel > 0;
        
        // Load existing config to preserve active-effects data
        org.bukkit.configuration.file.YamlConfiguration config;
        if (playerFile.exists()) {
            config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(playerFile);
        } else {
            config = new org.bukkit.configuration.file.YamlConfiguration();
        }
        
        // Check if there are active effects we need to preserve
        boolean hasActiveEffects = config.contains("active-effects") && 
                                   !config.getStringList("active-effects").isEmpty();
        
        // If no brewing content and no active effects, delete the file
        if (!hasBrewingContent && !hasActiveEffects) {
            if (playerFile.exists()) {
                playerFile.delete();
            }
            return;
        }
        
        // Clear old brewing slot data (in case slots were emptied)
        for (int i = 0; i < 5; i++) {
            config.set("slot" + i, null);
        }
        config.set("fuel", null);
        
        // Save current brewing slot contents
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                String base64 = itemStackToBase64(contents[i]);
                config.set("slot" + i, base64);
            }
        }
        
        // Save fuel level if present
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
     * Loads a player's virtual brewing stand contents from their playerdata file.
     * 
     * Reads Base64-encoded ItemStacks from the YAML file and deserializes them.
     * Called when a player opens their virtual brewing stand via /masterbrewing.
     * 
     * @param playerUUID The player's UUID
     * @return Two-element Object array: [0] = ItemStack[5] contents, [1] = Integer fuel level
     *         Contents array may contain nulls for empty slots. Fuel defaults to 0.
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
     * Serializes an ItemStack to a Base64-encoded string for storage.
     * 
     * Uses Bukkit's BukkitObjectOutputStream which preserves:
     * - Material type and amount
     * - All NBT data (including our custom master potion/brewing stand tags)
     * - Enchantments, display name, lore
     * - Damage/durability values
     * 
     * @param item The ItemStack to serialize
     * @return Base64 string representation, or null if serialization fails
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
     * Deserializes an ItemStack from a Base64-encoded string.
     * 
     * Reverses the itemStackToBase64() serialization, fully restoring:
     * - Material type and amount
     * - All NBT data (master potion tags, brewing stand tags, etc.)
     * - Enchantments, display name, lore
     * - Damage/durability values
     * 
     * @param base64 The Base64 string to deserialize
     * @return Restored ItemStack, or null if deserialization fails
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
    
    // ==================================================================================
    // HELPER METHODS - Core upgrade logic, display formatting, and utilities
    // ==================================================================================
    
    /**
     * Upgrades a Master Potion with the given ingredient type.
     * 
     * This is the SINGLE code path for ALL potion upgrades - both physical brewing
     * stands and virtual (command-based) brewing stands use this method.
     * 
     * Process:
     * 1. Validate potion is not null and is actually a potion item
     * 2. Read current upgrade levels from potion NBT
     * 3. Get the effect type from NBT (or detect from vanilla potion)
     * 4. Look up the next upgrade tier in config (potion-specific or global)
     * 5. Calculate new levels and duration
     * 6. Update all NBT tags on the potion
     * 7. Update visual display (name, lore, color, custom effects)
     * 
     * Redstone upgrades (isRedstone=true):
     * - Increases time level by 1
     * - Sets duration to the new tier's configured duration
     * - Cannot upgrade instant effects (returns -1)
     * 
     * Glowstone upgrades (isGlowstone=true):
     * - Increases power level by 1
     * - Keeps existing duration unchanged
     * - Amplifier shown as Roman numeral in name (I, II, III, etc.)
     * 
     * @param potion The potion ItemStack to upgrade (modified in place)
     * @param isRedstone true if upgrading with redstone (duration)
     * @param isGlowstone true if upgrading with glowstone (power)
     * @return Material cost consumed, or -1 if upgrade failed/not possible
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
     * Gets the material cost for the next upgrade without actually applying it.
     * 
     * This is a read-only method used to:
     * - Determine if a potion CAN be upgraded (returns -1 if not)
     * - Calculate how much material will be consumed
     * - Check before committing to the upgrade
     * 
     * Returns -1 (cannot upgrade) when:
     * - Item is null or not a potion
     * - Potion has no valid effect type
     * - Already at max level for the upgrade type
     * - Trying to upgrade instant effect with redstone
     * - Config doesn't have an entry for the next level
     * 
     * @param potion The potion to check
     * @param isRedstone true if checking redstone (time) upgrade cost
     * @param isGlowstone true if checking glowstone (power) upgrade cost
     * @return Material cost for next upgrade, or -1 if upgrade not possible
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
     * Updates a Master Potion's visual display: name, lore, color, and custom effects.
     * 
     * This is the SINGLE source of truth for how Master Potions appear to players.
     * Called after any upgrade to ensure consistent display formatting.
     * 
     * Display format:
     * - Name: "{EffectName} {RomanLevel}" in gold italic (e.g., "Speed IV")
     * - Color: Effect-specific color matching vanilla potion colors
     * - Lore lines:
     *   1. "Master Potion" in light purple (identifier)
     *   2. Effect-specific stat (power level, flight speed %, etc.)
     *   3. Duration with max shown (e.g., "Duration: 10m 0s (Max: 1h 0m 0s)")
     *   4. Next power upgrade cost if not at max
     *   5. Next duration upgrade cost if not at max
     * 
     * Special handling:
     * - Fly potions: Show "Flight Speed: X%" instead of generic power
     * - Fortune potions: Show "Luck: +X" instead of generic power
     * - Instant effects: Show "Duration: Instant" with no time upgrade line
     * - Max level indicators: Show "(MAX)" instead of upgrade cost
     * 
     * @param meta The PotionMeta to update (modified in place)
     * @param effectTypeKey Effect identifier (e.g., "speed", "fly", "fortune")
     * @param effectType PotionEffectType (null for custom effects like fly)
     * @param newTimeLevel Current time upgrade level
     * @param newPowerLevel Current power upgrade level
     * @param duration Current duration in seconds
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
     * Checks if an ItemStack is any type of potion (drinkable, splash, or lingering).
     * 
     * @param item The ItemStack to check
     * @return true if item is POTION, SPLASH_POTION, or LINGERING_POTION
     */
    private boolean isPotion(ItemStack item) {
        Material type = item.getType();
        return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION;
    }
    
    /**
     * Gets the primary PotionEffectType from a potion ItemStack.
     * 
     * Resolution order:
     * 1. Check NBT for stored effect type (already a master potion)
     * 2. Check base potion type (vanilla potion like SWIFTNESS, STRENGTH)
     * 3. Check custom effects list (for potions like luck, bad omen)
     * 
     * Handles name translations:
     * - "leaping" → "jump_boost"
     * - "swiftness" → "speed"
     * - "healing" → "instant_health"
     * - "harming" → "instant_damage"
     * 
     * Returns null for:
     * - Water bottles (no effect)
     * - Fly potions (custom effect, no PotionEffectType)
     * - Invalid/unknown potions
     * 
     * @param potion The potion ItemStack to examine
     * @return PotionEffectType of the potion, or null if none/unknown
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
     * Formats a PotionEffectType key into a human-readable display name.
     * 
     * Converts snake_case to Title Case:
     * - "speed" → "Speed"
     * - "instant_health" → "Instant Health"
     * - "fire_resistance" → "Fire Resistance"
     * - "jump_boost" → "Jump Boost"
     * 
     * @param effectType The PotionEffectType to format
     * @return Human-readable effect name with proper capitalization
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
     * Converts an integer to Roman numeral representation.
     * 
     * Used for displaying potion levels in the traditional Minecraft style:
     * - 1 → "I"
     * - 2 → "II"
     * - 4 → "IV"
     * - 9 → "IX"
     * - 10 → "X"
     * - 50 → "L"
     * - 100 → "C"
     * 
     * Supports numbers from 1 to 3999. Returns "I" for invalid input (≤0).
     * 
     * @param number The integer to convert (should be positive)
     * @return Roman numeral string representation
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
     * Gets the correct potion liquid color for a given effect type.
     * 
     * Colors are based on vanilla Minecraft 1.21+ potion colors.
     * These colors are displayed in:
     * - The potion bottle item appearance
     * - Splash particles when thrown
     * - The potion swirl particles around the player
     * 
     * Examples:
     * - Speed: Cyan (#33ebff)
     * - Strength: Red (#cc243c)
     * - Regeneration: Pink (#f89fba)
     * - Night Vision: Blue (#3066e6)
     * - Fire Resistance: Orange (#ffa733)
     * 
     * @param effectType The PotionEffectType to get color for
     * @return Bukkit Color matching vanilla's potion color, GRAY as fallback
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
     * Formats a duration in seconds to a human-readable time string.
     * 
     * Format examples:
     * - 30 seconds → "30s"
     * - 90 seconds → "1m 30s"
     * - 3600 seconds → "1h 0m 0s"
     * - 3661 seconds → "1h 1m 1s"
     * 
     * Hours are only shown if duration ≥ 1 hour.
     * Minutes are only shown if duration ≥ 1 minute.
     * 
     * @param seconds Duration in seconds
     * @return Formatted string like "5m 30s" or "1h 30m 0s"
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
    
    // ==================================================================================
    // COMMANDS - /masterbrewing command handling
    // ==================================================================================
    
    /**
     * Main command handler for /masterbrewing commands.
     * 
     * Dispatches to sub-handlers based on first argument:
     * - No args: Opens virtual brewing stand (if player has masterbrewing.use)
     * - "help": Shows help menu with upgrade tier tables
     * - "give": Gives master brewing items (requires masterbrewing.give)
     * - "reload": Reloads config (requires masterbrewing.admin)
     * 
     * @param sender Command sender (player or console)
     * @param command The command object
     * @param label The alias used
     * @param args Command arguments
     * @return true if command was handled
     */
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
     * Sends a formatted help message showing all available commands and upgrade tables.
     * 
     * Help includes:
     * - List of available commands based on sender's permissions
     * - Time upgrade table (Level, Redstone Cost, Duration)
     * - Power upgrade table (Level, Glowstone Cost, Potion Level)
     * 
     * Tables use Unicode box-drawing characters for clean formatting.
     * 
     * @param sender The command sender to show help to
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
     * Handles /masterbrewing give sub-commands.
     * 
     * Dispatches to specific handlers:
     * - "give stand <player>": Gives Master Brewing Stand item
     * - "give potion <player> <type> [time] [power]": Gives Master Potion
     * - "give potion <player> random": Gives random potion with random levels
     * - "give potion <player> <type> max": Gives max-level potion
     * 
     * Requires masterbrewing.give permission.
     * 
     * @param sender Command sender
     * @param args Full command arguments including "give"
     * @return true if command was handled
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
     * Handles /masterbrewing give stand <player> command.
     * 
     * Creates and gives a Master Brewing Stand item to the target player.
     * The item has the master_brewing_stand NBT marker and custom lore.
     * 
     * @param sender Command sender
     * @param args Full command arguments
     * @return true if command was handled
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
     * Handles /masterbrewing give potion <player> <type> [time] [power] command.
     * 
     * Creates and gives a Master Potion to the target player with specified parameters.
     * 
     * Argument formats:
     * - give potion <player> <type>              → Default levels (1,1 or 0,0 for fly/fortune)
     * - give potion <player> <type> <level>      → Same level for both time and power
     * - give potion <player> <type> <time> <pow> → Specific time and power levels
     * - give potion <player> <type> max          → Maximum available levels
     * - give potion <player> random              → Random type with random levels
     * 
     * Uses POTION_NAME_TO_EFFECT_KEY map for O(1) potion name validation and lookup.
     * 
     * @param sender Command sender
     * @param args Full command arguments
     * @return true if command was handled
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
     * Handles /masterbrewing give potion <player> random command.
     * 
     * Creates a potion with:
     * - Random effect type from POTION_NAME_TO_EFFECT_KEY keyset
     * - Random time level from 1 to maxTimeLevel (potion-specific max)
     * - Random power level from 1 to maxPowerLevel (potion-specific max)
     * 
     * Useful for loot crates, random rewards, or testing.
     * Uses per-potion max levels so each type stays within its valid range.
     * 
     * @param sender Command sender
     * @param target Player to receive the potion
     * @return true if command was handled
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
     * Handles /masterbrewing reload command.
     * 
     * Reloads configuration from config.yml without server restart:
     * - Clears existing upgrade tier maps
     * - Re-parses config.yml
     * - Rebuilds global and per-potion upgrade paths
     * - Recalculates max levels
     * 
     * Requires masterbrewing.admin permission.
     * Changes take effect immediately for new brewing operations.
     * Does NOT affect currently active potion effects on players.
     * 
     * @param sender Command sender
     * @return true if command was handled
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
     * Opens a virtual Master Brewing Stand GUI for a player.
     * 
     * Virtual brewing stands provide the same functionality as physical stands
     * without requiring a block to be placed. Useful for:
     * - Players in areas where they can't place blocks
     * - Convenience brewing while traveling
     * - Testing and admin purposes
     * 
     * Process:
     * 1. Create a brewing inventory with custom title
     * 2. Load any saved contents from player's data file
     * 3. Load saved fuel level
     * 4. Track this inventory in virtualBrewingStands map
     * 5. Open the GUI for the player
     * 6. Update fuel display after 1 tick
     * 
     * Contents persist across sessions - items placed in virtual stand
     * are saved to disk when closed and restored when opened again.
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
     * Updates the fuel level display in a virtual brewing stand GUI.
     * 
     * Uses Bukkit's InventoryView.Property.FUEL_TIME to show the fuel bar.
     * Value ranges from 0 (empty) to 20 (full from one blaze powder).
     * 
     * Called:
     * - After opening virtual brewing stand
     * - After each click event that might consume fuel
     * - After drag events
     * 
     * @param player The player viewing the virtual brewing stand
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
     * Handles inventory close events to save and clean up virtual brewing stands.
     * 
     * When a player closes their virtual brewing stand GUI:
     * 1. Verify it's actually a virtual brewing stand (in our tracking map)
     * 2. Save all inventory contents to player's data file
     * 3. Save current fuel level
     * 4. Remove from virtualBrewingStands tracking map
     * 5. Remove from virtualBrewingFuel tracking map
     * 
     * This ensures:
     * - Players don't lose items when closing the GUI
     * - Fuel level persists between sessions
     * - Memory is freed when stand is no longer in use
     * 
     * @param event The inventory close event from Bukkit
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
     * Handles inventory click events for virtual brewing stands.
     * 
     * This method processes brewing when items are placed in valid configuration:
     * 1. Verify click is in a virtual brewing stand (in our tracking map)
     * 2. Schedule a delayed task (2 ticks) to let the click complete
     * 3. Call processVirtualBrewing to check for and apply upgrades
     * 4. Update fuel display
     * 
     * The 2-tick delay is necessary because:
     * - Item isn't actually in the slot until after event completes
     * - We need to see the inventory in its post-click state
     * - Checking immediately would see pre-click state
     * 
     * Event priority is MONITOR to run after other plugins handle the click.
     * 
     * @param event The inventory click event from Bukkit
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
     * Handles inventory drag events for virtual brewing stands.
     * 
     * Drag events occur when player drags items across multiple slots.
     * Same logic as click handler - delay processing to see final state.
     * 
     * @param event The inventory drag event from Bukkit
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
     * Processes brewing upgrades in a virtual Master Brewing Stand.
     * 
     * This method loops continuously attempting upgrades until no more are possible.
     * This allows batch processing when multiple upgrades worth of materials are present.
     * 
     * For each upgrade iteration:
     * 1. Check ingredient slot (3) for redstone or glowstone
     * 2. Check fuel slot (4) for blaze powder if fuel is empty
     * 3. Find all potions (slots 0-2) that can be upgraded
     * 4. Calculate material cost from first valid potion
     * 5. Verify enough materials present
     * 6. Consume fuel (1 charge per upgrade, blaze powder = 20 charges)
     * 7. Consume ingredient materials
     * 8. Apply upgrade to all valid potions
     * 9. Repeat until out of materials, fuel, or max level reached
     * 
     * Brewing slot layout:
     * - Slot 0: Left potion bottle
     * - Slot 1: Middle potion bottle
     * - Slot 2: Right potion bottle
     * - Slot 3: Ingredient (redstone/glowstone)
     * - Slot 4: Fuel (blaze powder)
     * 
     * @param inv The virtual brewing stand inventory
     * @param playerUUID UUID of the player using the stand (for fuel tracking)
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
    
    // ==================================================================================
    // TAB COMPLETION - Provides command argument suggestions
    // ==================================================================================
    
    /**
     * Provides tab completion suggestions for /masterbrewing commands.
     * 
     * Completion tree:
     * /masterbrewing
     * ├── help
     * ├── give (requires masterbrewing.give)
     * │   ├── stand
     * │   │   └── <online player names>
     * │   └── potion
     * │       └── <online player names>
     * │           ├── random
     * │           └── <potion types from POTION_NAME_TO_EFFECT_KEY>
     * │               ├── max
     * │               └── <1 to maxTimeLevel>
     * │                   └── <1 to maxPowerLevel>
     * └── reload (requires masterbrewing.admin)
     * 
     * Suggestions are filtered to only show options starting with current input.
     * Permission checks ensure players only see commands they can use.
     * 
     * @param sender Command sender
     * @param command Command being completed
     * @param alias Alias used
     * @param args Arguments typed so far
     * @return List of completion suggestions
     */
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