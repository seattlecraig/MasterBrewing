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
    
    // OPTIMIZATION #1: Store locations as strings for O(1) lookup without object allocation
    // Format: "world,x,y,z"
    private Set<String> masterBrewingStandLocations = new HashSet<>();
    
    // Track active master potion effects per player
    // Map: Player UUID -> List of active effects with expiry times
    private Map<UUID, List<ActiveMasterEffect>> activeMasterEffects = new HashMap<>();
    
    // Configuration: upgrade tiers
    // Map: level -> [redstone cost, duration in seconds]
    private Map<Integer, int[]> timeUpgrades = new TreeMap<>();
    
    // Map: level -> glowstone cost
    private Map<Integer, Integer> powerUpgrades = new TreeMap<>();
    
    // Max levels based on config
    private int maxTimeLevel = 0;
    private int maxPowerLevel = 0;
    
    // OPTIMIZATION #3: Static maps for O(1) potion name lookup instead of O(n) switch
    private static final Map<String, String> POTION_NAME_TO_EFFECT_KEY = createPotionNameMap();
    
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
    
    // OPTIMIZATION #4: Dirty flags for file I/O optimization
    private volatile boolean standLocationsDirty = false;
    
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
    
    @Override
    public void onEnable() {
        // Display startup messages
        getServer().getConsoleSender().sendMessage(
            Component.text("[MasterBrewing] MasterBrewing Started!", NamedTextColor.GREEN)
        );
        getServer().getConsoleSender().sendMessage(
            Component.text("[MasterBrewing] By SupaFloof Games, LLC", NamedTextColor.LIGHT_PURPLE)
        );
        
        // Initialize persistent data keys
        masterBrewingStandKey = new NamespacedKey(this, "master_brewing_stand");
        potionTimeLevelKey = new NamespacedKey(this, "potion_time_level");
        potionPowerLevelKey = new NamespacedKey(this, "potion_power_level");
        potionDurationKey = new NamespacedKey(this, "potion_duration");
        potionEffectTypeKey = new NamespacedKey(this, "potion_effect_type");
        masterPotionKey = new NamespacedKey(this, "master_potion");
        
        // Save default config and load upgrade tiers
        saveDefaultConfig();
        loadUpgradeTiers();
        
        // Load saved master brewing stand locations
        loadMasterBrewingStandLocations();
        
        // Register events and commands
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("masterbrewing").setExecutor(this);
        getCommand("masterbrewing").setTabCompleter(this);
        
        // Start continuous effect refresh task (runs every second)
        startMasterPotionEffectTask();
        
        // Load effects for any players already online (in case of /reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerEffects(player.getUniqueId());
        }
        
        getLogger().info("MasterBrewing plugin enabled!");
    }
    
    @Override
    public void onDisable() {
        // OPTIMIZATION #4: Save stand locations only if dirty
        if (standLocationsDirty) {
            saveMasterBrewingStandLocations();
        }
        
        // Save all active player effects
        for (UUID uuid : activeMasterEffects.keySet()) {
            List<ActiveMasterEffect> effects = activeMasterEffects.get(uuid);
            savePlayerEffects(uuid, effects);
        }
        
        getLogger().info("MasterBrewing plugin disabled!");
    }
    
    /**
     * Saves master brewing stand locations to stands.yml
     * OPTIMIZATION #4: Now async with dirty flag
     */
    private void saveMasterBrewingStandLocations() {
        // OPTIMIZATION #4: Run file I/O asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            File standsFile = new File(getDataFolder(), "stands.yml");
            FileConfiguration standsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(standsFile);
            
            // OPTIMIZATION #1: Convert Set<String> directly to List<String>
            List<String> locations = new ArrayList<>(masterBrewingStandLocations);
            
            standsConfig.set("master-brewing-stands", locations);
            
            try {
                standsConfig.save(standsFile);
                getLogger().info("Saved " + locations.size() + " master brewing stand locations to stands.yml");
                standLocationsDirty = false;
            } catch (Exception e) {
                getLogger().warning("Failed to save master brewing stand locations: " + e.getMessage());
            }
        });
    }
    
    /**
     * Loads master brewing stand locations from stands.yml
     * OPTIMIZATION #1: Now loads into String set
     */
    private void loadMasterBrewingStandLocations() {
        masterBrewingStandLocations.clear();
        
        File standsFile = new File(getDataFolder(), "stands.yml");
        if (!standsFile.exists()) {
            getLogger().info("No stands.yml found, starting fresh");
            return;
        }
        
        FileConfiguration standsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(standsFile);
        List<String> locations = standsConfig.getStringList("master-brewing-stands");
        int loaded = 0;
        
        for (String locString : locations) {
            try {
                String[] parts = locString.split(",");
                if (parts.length != 4) continue;
                
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                
                Location loc = new Location(world, x, y, z);
                
                // Verify the block is still a brewing stand
                if (loc.getBlock().getType() == Material.BREWING_STAND) {
                    // OPTIMIZATION #1: Add string directly to set
                    masterBrewingStandLocations.add(locString);
                    loaded++;
                }
            } catch (Exception e) {
                getLogger().warning("Failed to load master brewing stand location: " + locString);
            }
        }
        
        getLogger().info("Loaded " + loaded + " master brewing stand locations from stands.yml");
    }
    
    /**
     * Loads upgrade tiers from config.yml
     */
    private void loadUpgradeTiers() {
        timeUpgrades.clear();
        powerUpgrades.clear();
        maxTimeLevel = 0;
        maxPowerLevel = 0;
        
        FileConfiguration config = getConfig();
        List<String> timeConfigs = config.getStringList("upgrade-time");
        List<String> powerConfigs = config.getStringList("upgrade-power");
        
        // Load time upgrades
        for (String entry : timeConfigs) {
            try {
                String[] parts = entry.split(",");
                if (parts.length != 3) {
                    getLogger().warning("Invalid upgrade-time entry: " + entry);
                    continue;
                }
                
                int level = Integer.parseInt(parts[0].trim());
                int redstoneCost = Integer.parseInt(parts[1].trim());
                int duration = Integer.parseInt(parts[2].trim());
                
                timeUpgrades.put(level, new int[]{redstoneCost, duration});
                maxTimeLevel = Math.max(maxTimeLevel, level);
            } catch (Exception e) {
                getLogger().warning("Failed to parse upgrade-time entry: " + entry);
            }
        }
        
        // Load power upgrades
        for (String entry : powerConfigs) {
            try {
                String[] parts = entry.split(",");
                if (parts.length != 2) {
                    getLogger().warning("Invalid upgrade-power entry: " + entry);
                    continue;
                }
                
                int level = Integer.parseInt(parts[0].trim());
                int glowstoneCost = Integer.parseInt(parts[1].trim());
                
                powerUpgrades.put(level, glowstoneCost);
                maxPowerLevel = Math.max(maxPowerLevel, level);
            } catch (Exception e) {
                getLogger().warning("Failed to parse upgrade-power entry: " + entry);
            }
        }
        
        getLogger().info("Loaded " + timeUpgrades.size() + " time upgrades (max level: " + maxTimeLevel + ")");
        getLogger().info("Loaded " + powerUpgrades.size() + " power upgrades (max level: " + maxPowerLevel + ")");
    }
    
    /**
     * Starts the unified luck potion brewing timer task
     * Runs every 5 ticks and decrements all active brewing timers
     */
    /**
     * Starts a repeating task that continuously refreshes master potion effects
     * OPTIMIZED: Runs every 3 seconds (instead of 1) and only refreshes effects that need it
     */
    private void startMasterPotionEffectTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long currentTime = System.currentTimeMillis();
            
            // OPTIMIZATION: Only check players who have active effects
            Set<UUID> playersToCheck = new HashSet<>(activeMasterEffects.keySet());
            
            for (UUID uuid : playersToCheck) {
                // OPTIMIZATION: Skip offline players without looking them up repeatedly
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                
                List<ActiveMasterEffect> effects = activeMasterEffects.get(uuid);
                if (effects == null || effects.isEmpty()) {
                    continue;
                }
                
                // Track if player has active fly effect for action bar (use arrays for lambda capture)
                final boolean[] hasActiveFly = {false};
                final String[] flyActionBar = {null};
                
                // Clean up expired effects and restore missing ones
                effects.removeIf(effect -> {
                    // Calculate remaining time
                    long remainingMillis = effect.expiryTime - currentTime;
                    int remainingSeconds = (int) (remainingMillis / 1000);
                    
                    // If effect has expired, remove it from tracking
                    if (currentTime >= effect.expiryTime) {
                        // Handle fly expiration
                        if (effect.effectTypeKey.equals("fly")) {
                            // Only disable flight if player is in survival/adventure
                            if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || 
                                player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                                player.setAllowFlight(false);
                                player.setFlying(false);
                                player.setFlySpeed(0.1f); // Reset to default
                            }
                            player.sendMessage(Component.text("Flight ended!", NamedTextColor.RED));
                        }
                        return true; // Remove from list
                    }
                    
                    // Show expiration warnings
                    if (remainingSeconds == 30 || remainingSeconds == 10) {
                        String effectName;
                        if (effect.effectTypeKey.equals("fly")) {
                            effectName = "Flight";
                        } else if (effect.effectTypeKey.equals("fortune")) {
                            effectName = "Fortune";
                        } else {
                            effectName = formatEffectName(PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effect.effectTypeKey)));
                        }
                        player.sendMessage(Component.text(effectName + " ending in " + remainingSeconds + " seconds!", 
                            NamedTextColor.YELLOW));
                    }
                    
                    // Handle fly effect specially
                    if (effect.effectTypeKey.equals("fly")) {
                        hasActiveFly[0] = true;
                        
                        // Maintain flight state only if needed
                        if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || 
                            player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                            if (!player.getAllowFlight()) {
                                player.setAllowFlight(true);
                            }
                            
                            // OPTIMIZATION: Only set flight speed if it's different from what we want
                            float desiredSpeed = 0.1f * (1.0f + (effect.amplifier * 0.2f));
                            desiredSpeed = Math.min(desiredSpeed, 1.0f);
                            float currentSpeed = player.getFlySpeed();
                            
                            // Only update if speed differs by more than 0.001 to avoid floating point issues
                            if (Math.abs(currentSpeed - desiredSpeed) > 0.001f) {
                                player.setFlySpeed(desiredSpeed);
                            }
                        }
                        
                        // Prepare action bar message
                        int displayLevel = effect.amplifier + 1;
                        String romanLevel = toRoman(displayLevel);
                        String durationStr = formatDuration(remainingSeconds);
                        flyActionBar[0] = "✈ Flight " + romanLevel + " • " + durationStr + " remaining";
                        
                        return false; // Keep in list
                    }
                    
                    // OPTIMIZATION: Only refresh effects that are close to expiring (< 30 seconds)
                    // This prevents constant fighting with SpecialBooks armor effects
                    if (remainingSeconds > 30) {
                        return false; // Don't refresh yet, keep in list
                    }
                    
                    // Handle normal potion effects (including fortune -> luck)
                    String mappedEffectKey = effect.effectTypeKey;
                    if (effect.effectTypeKey.equals("fortune")) {
                        mappedEffectKey = "luck"; // Fortune uses LUCK potion effect
                    }
                    
                    PotionEffectType effectType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(mappedEffectKey));
                    if (effectType == null) {
                        return true; // Remove invalid effect
                    }
                    
                    // Effect should still be active - check if player has it
                    PotionEffect currentEffect = player.getPotionEffect(effectType);
                    
                    // OPTIMIZATION: Only restore if effect is completely missing
                    // Don't fight with other plugins that may have applied effects with different durations
                    if (currentEffect == null) {
                        int durationTicks = remainingSeconds * 20;
                        // Reapply the effect with remaining duration
                        player.addPotionEffect(new PotionEffect(effectType, durationTicks, effect.amplifier, false, true, true), true);
                    }
                    // Don't check amplifier or adjust duration - let other plugins manage their own effects
                    
                    return false; // Keep in list
                });
                
                // Show fly action bar if active
                if (hasActiveFly[0] && flyActionBar[0] != null) {
                    player.sendActionBar(Component.text(flyActionBar[0], NamedTextColor.GOLD));
                }
                
                // Remove player from map if no effects left
                if (effects.isEmpty()) {
                    activeMasterEffects.remove(uuid);
                }
            }
        }, 60L, 60L); // OPTIMIZATION: Run every 3 seconds (60 ticks) instead of every 1 second
    }
    
    /**
     * Creates a Master Brewing Stand item with NBT tag
     */
    private ItemStack createMasterBrewingStand() {
        ItemStack brewingStand = new ItemStack(Material.BREWING_STAND);
        ItemMeta meta = brewingStand.getItemMeta();
        
        meta.displayName(Component.text("Master Brewing Stand", NamedTextColor.GOLD, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A special brewing stand that allows", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("unlimited potion upgrades.", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("", NamedTextColor.GRAY));
        lore.add(Component.text("Use redstone to increase duration", NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Use glowstone to increase power", NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        
        meta.lore(lore);
        meta.getPersistentDataContainer().set(masterBrewingStandKey, PersistentDataType.BYTE, (byte) 1);
        
        brewingStand.setItemMeta(meta);
        return brewingStand;
    }
    
    /**
     * Checks if an item is a Master Brewing Stand
     */
    private boolean isMasterBrewingStand(ItemStack item) {
        if (item == null || item.getType() != Material.BREWING_STAND) {
            return false;
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
    private boolean isMasterBrewingStandLocation(Location loc) {
        String locKey = locationToString(loc);
        return masterBrewingStandLocations.contains(locKey);
    }
    
    /**
     * OPTIMIZATION #1: Converts location to string key for storage
     */
    private String locationToString(Location loc) {
        return loc.getWorld().getName() + "," + 
               loc.getBlockX() + "," + 
               loc.getBlockY() + "," + 
               loc.getBlockZ();
    }
    
    // ==================== EVENT HANDLERS ====================
    
    /**
     * Handles placing Master Brewing Stands
     * OPTIMIZATION #1: Now stores location as string
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        
        if (isMasterBrewingStand(item)) {
            Location loc = event.getBlock().getLocation();
            // OPTIMIZATION #1: Add string key instead of Location object
            masterBrewingStandLocations.add(locationToString(loc));
            // OPTIMIZATION #4: Mark as dirty
            standLocationsDirty = true;
            
            event.getPlayer().sendMessage(Component.text("Master Brewing Stand placed!", NamedTextColor.GREEN));
        }
    }
    
    /**
     * Handles breaking Master Brewing Stands
     * OPTIMIZATION #1: Now removes location by string
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        
        if (block.getType() == Material.BREWING_STAND) {
            Location loc = block.getLocation();
            
            if (isMasterBrewingStandLocation(loc)) {
                // OPTIMIZATION #1: Remove string key instead of Location object
                masterBrewingStandLocations.remove(locationToString(loc));
                // OPTIMIZATION #4: Mark as dirty
                standLocationsDirty = true;
                
                // Drop Master Brewing Stand item
                event.setDropItems(false);
                block.getWorld().dropItemNaturally(loc, createMasterBrewingStand());
                
                event.getPlayer().sendMessage(Component.text("Master Brewing Stand broken!", NamedTextColor.YELLOW));
            }
        }
    }
    
    /**
     * Handles brewing completion in Master Brewing Stands
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBrew(BrewEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        
        // Only handle master brewing stands
        if (!isMasterBrewingStandLocation(loc)) {
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
        // Collect all valid potions that can be upgraded
        List<ItemStack> potionsToUpgrade = new ArrayList<>();
        int materialCost = 0;
        int upgradeDuration = 0;
        
        // Determine the upgrade level and cost based on the FIRST valid potion
        // All potions in the stand will be upgraded to the same level
        for (int slot = 0; slot < 3; slot++) {
            ItemStack potion = inv.getItem(slot);
            
            if (potion == null || !isPotion(potion)) {
                continue;
            }
            
            // Get current levels
            ItemMeta meta = potion.getItemMeta();
            if (meta == null) {
                continue;
            }
            
            int currentTimeLevel = meta.getPersistentDataContainer().getOrDefault(potionTimeLevelKey, PersistentDataType.INTEGER, 0);
            int currentPowerLevel = meta.getPersistentDataContainer().getOrDefault(potionPowerLevelKey, PersistentDataType.INTEGER, 0);
            
            // Get effect type key from NBT (handles both normal and fly potions)
            String effectTypeKey = meta.getPersistentDataContainer().get(potionEffectTypeKey, PersistentDataType.STRING);
            if (effectTypeKey == null) {
                // Not a master potion, try to get base effect
                PotionEffectType effectType = getBasePotionEffect(potion);
                if (effectType == null) {
                    // Can't determine effect type - skip this potion
                    continue;
                }
                effectTypeKey = effectType.getKey().getKey();
            }
            
            // Calculate material cost from FIRST valid potion only
            if (potionsToUpgrade.isEmpty()) {
                if (isRedstone) {
                    int nextLevel = currentTimeLevel + 1;
                    if (nextLevel > maxTimeLevel) {
                        continue; // Max level reached
                    }
                    
                    int[] upgrade = timeUpgrades.get(nextLevel);
                    if (upgrade == null) continue;
                    
                    materialCost = upgrade[0];
                    upgradeDuration = upgrade[1];
                    
                } else if (isGlowstone) {
                    int nextLevel = currentPowerLevel + 1;
                    if (nextLevel > maxPowerLevel) {
                        continue; // Max level reached
                    }
                    
                    Integer glowstoneCost = powerUpgrades.get(nextLevel);
                    if (glowstoneCost == null) continue;
                    
                    materialCost = glowstoneCost;
                }
            }
            
            potionsToUpgrade.add(potion);
        }
        
        // Check if we have any potions to upgrade
        if (potionsToUpgrade.isEmpty()) {
            return;
        }
        
        // Check if we have enough materials for the upgrade
        if (ingredient.getAmount() < materialCost) {
            return;
        }
        
        // Apply upgrade to ALL potions
        for (ItemStack potion : potionsToUpgrade) {
            PotionMeta meta = (PotionMeta) potion.getItemMeta();
            
            int currentTimeLevel = meta.getPersistentDataContainer().getOrDefault(potionTimeLevelKey, PersistentDataType.INTEGER, 0);
            int currentPowerLevel = meta.getPersistentDataContainer().getOrDefault(potionPowerLevelKey, PersistentDataType.INTEGER, 0);
            
            String effectTypeKey = meta.getPersistentDataContainer().get(potionEffectTypeKey, PersistentDataType.STRING);
            if (effectTypeKey == null) {
                PotionEffectType effectType = getBasePotionEffect(potion);
                if (effectType != null) {
                    effectTypeKey = effectType.getKey().getKey();
                }
            }
            
            PotionEffectType effectType = null;
            if (effectTypeKey != null && !effectTypeKey.equals("fly") && !effectTypeKey.equals("fortune")) {
                effectType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effectTypeKey));
            }
            
            // Update levels based on upgrade type
            int newTimeLevel = currentTimeLevel;
            int newPowerLevel = currentPowerLevel;
            
            if (isRedstone) {
                newTimeLevel = currentTimeLevel + 1;
            } else if (isGlowstone) {
                newPowerLevel = currentPowerLevel + 1;
            }
            
            // Store updated levels
            meta.getPersistentDataContainer().set(potionTimeLevelKey, PersistentDataType.INTEGER, newTimeLevel);
            meta.getPersistentDataContainer().set(potionPowerLevelKey, PersistentDataType.INTEGER, newPowerLevel);
            meta.getPersistentDataContainer().set(potionEffectTypeKey, PersistentDataType.STRING, effectTypeKey);
            meta.getPersistentDataContainer().set(masterPotionKey, PersistentDataType.BYTE, (byte) 1);
        
        // Get duration
        int duration;
        if (newTimeLevel == 0) {
            // Check if duration is already stored
            if (meta.getPersistentDataContainer().has(potionDurationKey, PersistentDataType.INTEGER)) {
                duration = meta.getPersistentDataContainer().get(potionDurationKey, PersistentDataType.INTEGER);
            } else {
                // For fly/fortune at level 0, use 180 seconds
                if (effectTypeKey.equals("fly") || effectTypeKey.equals("fortune")) {
                    duration = 180;
                } else if (effectType != null) {
                    // Extract vanilla potion duration before we clear it
                    duration = extractVanillaPotionDuration(meta, effectType);
                } else {
                    duration = 180;
                }
            }
        } else {
            // Use config duration for non-zero levels
            duration = timeUpgrades.get(newTimeLevel)[1];
        }
        
        // Update duration if this was a time upgrade
        if (isRedstone) {
            duration = upgradeDuration;
        } else {
            // Keep existing duration for power upgrades
            if (meta.getPersistentDataContainer().has(potionDurationKey, PersistentDataType.INTEGER)) {
                duration = meta.getPersistentDataContainer().get(potionDurationKey, PersistentDataType.INTEGER);
            } else {
                // For fly/fortune at level 0, use 180 seconds
                if (effectTypeKey.equals("fly") || effectTypeKey.equals("fortune")) {
                    duration = 180;
                } else if (effectType != null) {
                    // Extract vanilla potion duration before we clear it
                    duration = extractVanillaPotionDuration(meta, effectType);
                } else {
                    duration = 180;
                }
            }
        }
        
        // Store duration in NBT
        meta.getPersistentDataContainer().set(potionDurationKey, PersistentDataType.INTEGER, duration);
        
        // Clear base potion type to remove vanilla text
        meta.setBasePotionType(PotionType.WATER);
        
        // Set the correct color for this effect type
        if (effectTypeKey.equals("fly")) {
            meta.setColor(org.bukkit.Color.ORANGE);
        } else if (effectTypeKey.equals("fortune")) {
            meta.setColor(org.bukkit.Color.LIME); // Green for luck/fortune
        } else {
            meta.setColor(getPotionColor(effectType));
        }
        
        // Add custom effect - this displays the correct vanilla effect information (skip for fly)
        meta.clearCustomEffects();
        if (effectTypeKey.equals("fortune")) {
            // Fortune uses LUCK potion effect
            PotionEffectType luckEffect = PotionEffectType.LUCK;
            meta.addCustomEffect(new PotionEffect(luckEffect, duration * 20, newPowerLevel, false, false, false), true);
        } else if (!effectTypeKey.equals("fly") && effectType != null) {
            meta.addCustomEffect(new PotionEffect(effectType, duration * 20, newPowerLevel, false, false, false), true);
        }
        
        // Update display name
        String effectName;
        if (effectTypeKey.equals("fly")) {
            effectName = "Fly";
        } else if (effectTypeKey.equals("fortune")) {
            effectName = "Fortune";
        } else {
            effectName = formatEffectName(effectType);
        }
        int displayLevel = newPowerLevel + 1; // Power level 0 = I, 1 = II, etc.
        String romanLevel = toRoman(displayLevel);
        
        Component name = Component.text(effectName + " " + romanLevel, NamedTextColor.GOLD, TextDecoration.ITALIC)
            .decoration(TextDecoration.ITALIC, true);
        meta.displayName(name);
        
        // Update lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Master Potion", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
        
        // Add fly-specific lore
        if (effectTypeKey.equals("fly")) {
            // Calculate flight speed
            float flightSpeed = 0.1f * (1.0f + (newPowerLevel * 0.2f));
            flightSpeed = Math.min(flightSpeed, 1.0f);
            int speedPercent = (int)((flightSpeed / 0.1f) * 100);
            
            lore.add(Component.text("Flight Speed: " + speedPercent + "%", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Duration: " + formatDuration(duration), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        } else if (effectTypeKey.equals("fortune")) {
            // Add fortune-specific lore
            lore.add(Component.text("Luck: +" + (newPowerLevel + 1), NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Duration: " + formatDuration(duration), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        
        potion.setItemMeta(meta);
    }
        
        // Consume materials
        int newAmount = ingredient.getAmount() - materialCost;
        if (newAmount <= 0) {
            inv.setIngredient(null);
        } else {
            ingredient.setAmount(newAmount);
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
    
    // ==================== HELPER METHODS ====================
    
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
            sendHelp(sender);
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
            timeLevel = maxTimeLevel;
            powerLevel = maxPowerLevel;
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
        
        if (timeLevel > maxTimeLevel) {
            sender.sendMessage(Component.text("Time level cannot exceed " + maxTimeLevel + "!", NamedTextColor.RED));
            return true;
        }
        
        if (powerLevel > maxPowerLevel) {
            sender.sendMessage(Component.text("Power level cannot exceed " + maxPowerLevel + "!", NamedTextColor.RED));
            return true;
        }
        
        // Get duration from config for the specified time level
        int duration;
        if ((potionName.equals("fly") || potionName.equals("fortune")) && timeLevel == 0) {
            // Custom potions at level 0 have 3 minute (180 second) duration
            duration = 180;
        } else {
            if (!timeUpgrades.containsKey(timeLevel)) {
                sender.sendMessage(Component.text("Invalid time level: " + timeLevel + "! Valid levels are 1-" + maxTimeLevel, NamedTextColor.RED));
                return true;
            }
            duration = timeUpgrades.get(timeLevel)[1];
        }
        
        // Validate power level exists in config (skip for custom potions at level 0)
        if (!((potionName.equals("fly") || potionName.equals("fortune")) && powerLevel == 0)) {
            if (!powerUpgrades.containsKey(powerLevel)) {
                sender.sendMessage(Component.text("Invalid power level: " + powerLevel + "! Valid levels are 1-" + maxPowerLevel, NamedTextColor.RED));
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
        
        // Add fly-specific lore
        if (effectKey.equals("fly")) {
            // Calculate flight speed
            float flightSpeed = 0.1f * (1.0f + (powerLevel * 0.2f));
            flightSpeed = Math.min(flightSpeed, 1.0f);
            int speedPercent = (int)((flightSpeed / 0.1f) * 100);
            
            lore.add(Component.text("Flight Speed: " + speedPercent + "%", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Duration: " + formatDuration(duration), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        } else if (effectKey.equals("fortune")) {
            // Add fortune-specific lore
            lore.add(Component.text("Luck: +" + (powerLevel + 1), NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Duration: " + formatDuration(duration), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
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
        
        // Get random time and power levels
        int timeLevel = 1 + new Random().nextInt(maxTimeLevel);
        int powerLevel = 1 + new Random().nextInt(maxPowerLevel);
        
        // Get effect type using NamespacedKey (skip for custom fly potion)
        PotionEffectType effectType = null;
        if (!effectKey.equals("fly")) {
            effectType = PotionEffectType.getByKey(NamespacedKey.minecraft(effectKey));
            if (effectType == null) {
                sender.sendMessage(Component.text("Failed to load random potion effect type: " + effectKey, NamedTextColor.RED));
                return true;
            }
        }
        
        // Get duration from config
        int duration = timeUpgrades.get(timeLevel)[1];
        
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
        } else {
            meta.setColor(getPotionColor(effectType));
        }
        
        // Add custom effect (skip for fly since it has no PotionEffectType)
        meta.clearCustomEffects();
        if (!effectKey.equals("fly")) {
            meta.addCustomEffect(new PotionEffect(effectType, duration * 20, powerLevel, false, false, false), true);
        }
        
        // Set display name
        String effectName = effectKey.equals("fly") ? "Fly" : formatEffectName(effectType);
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
            
            lore.add(Component.text("Flight Speed: " + speedPercent + "%", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Duration: " + formatDuration(duration), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
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