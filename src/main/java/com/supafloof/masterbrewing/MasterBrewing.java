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
    
    // Stores locations of placed master brewing stands
    private Set<Location> masterBrewingStandLocations = new HashSet<>();
    
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
        // Save master brewing stand locations
        saveMasterBrewingStandLocations();
        
        // Save all active player effects
        for (UUID uuid : activeMasterEffects.keySet()) {
            savePlayerEffects(uuid);
        }
        
        getLogger().info("MasterBrewing plugin disabled!");
    }
    
    /**
     * Saves master brewing stand locations to stands.yml
     */
    private void saveMasterBrewingStandLocations() {
        File standsFile = new File(getDataFolder(), "stands.yml");
        FileConfiguration standsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(standsFile);
        
        List<String> locations = new ArrayList<>();
        for (Location loc : masterBrewingStandLocations) {
            // Format: world,x,y,z
            String locString = loc.getWorld().getName() + "," + 
                              loc.getBlockX() + "," + 
                              loc.getBlockY() + "," + 
                              loc.getBlockZ();
            locations.add(locString);
        }
        
        standsConfig.set("master-brewing-stands", locations);
        
        try {
            standsConfig.save(standsFile);
            getLogger().info("Saved " + locations.size() + " master brewing stand locations to stands.yml");
        } catch (Exception e) {
            getLogger().warning("Failed to save master brewing stand locations: " + e.getMessage());
        }
    }
    
    /**
     * Loads master brewing stand locations from stands.yml
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
                    masterBrewingStandLocations.add(loc);
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
     * Starts a repeating task that continuously refreshes master potion effects
     * Runs every second to check if effects were prematurely removed and reapply them
     */
    private void startMasterPotionEffectTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long currentTime = System.currentTimeMillis();
            
            // Check all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
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
                        String effectName = effect.effectTypeKey.equals("fly") ? "Flight" : 
                            formatEffectName(PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effect.effectTypeKey)));
                        player.sendMessage(Component.text(effectName + " ending in " + remainingSeconds + " seconds!", 
                            NamedTextColor.YELLOW));
                    }
                    
                    // Handle fly effect specially
                    if (effect.effectTypeKey.equals("fly")) {
                        hasActiveFly[0] = true;
                        
                        // Maintain flight state
                        if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || 
                            player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                            if (!player.getAllowFlight()) {
                                player.setAllowFlight(true);
                            }
                            
                            // Recalculate and set flight speed
                            float flightSpeed = 0.1f * (1.0f + (effect.amplifier * 0.2f));
                            flightSpeed = Math.min(flightSpeed, 1.0f);
                            player.setFlySpeed(flightSpeed);
                        }
                        
                        // Prepare action bar message
                        int displayLevel = effect.amplifier + 1;
                        String romanLevel = toRoman(displayLevel);
                        String durationStr = formatDuration(remainingSeconds);
                        flyActionBar[0] = "✈ Flight " + romanLevel + " • " + durationStr + " remaining";
                        
                        return false; // Keep in list
                    }
                    
                    // Handle normal potion effects
                    PotionEffectType effectType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effect.effectTypeKey));
                    if (effectType == null) {
                        return true; // Remove invalid effect
                    }
                    
                    // Effect should still be active - check if player has it
                    PotionEffect currentEffect = player.getPotionEffect(effectType);
                    
                    // If the effect is MISSING (was removed by something), restore it
                    if (currentEffect == null) {
                        int durationTicks = remainingSeconds * 20;
                        // Reapply the effect with remaining duration
                        player.addPotionEffect(new PotionEffect(effectType, durationTicks, effect.amplifier, false, true, true), true);
                    }
                    // If effect exists but wrong amplifier, fix it
                    else if (currentEffect.getAmplifier() != effect.amplifier) {
                        int durationTicks = remainingSeconds * 20;
                        
                        player.removePotionEffect(effectType);
                        player.addPotionEffect(new PotionEffect(effectType, durationTicks, effect.amplifier, false, true, true), true);
                    }
                    
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
        }, 20L, 20L); // Run every second (20 ticks)
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
     */
    private boolean isMasterBrewingStandLocation(Location loc) {
        return masterBrewingStandLocations.contains(loc);
    }
    
    // ==================== EVENT HANDLERS ====================
    
    /**
     * Handles placing Master Brewing Stands
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        
        if (isMasterBrewingStand(item)) {
            Location loc = event.getBlock().getLocation();
            masterBrewingStandLocations.add(loc);
            
            event.getPlayer().sendMessage(Component.text("Master Brewing Stand placed!", NamedTextColor.GREEN));
        }
    }
    
    /**
     * Handles breaking Master Brewing Stands
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        
        if (block.getType() == Material.BREWING_STAND) {
            Location loc = block.getLocation();
            
            if (isMasterBrewingStandLocation(loc)) {
                masterBrewingStandLocations.remove(loc);
                
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
        // Process all three potion slots
        for (int slot = 0; slot < 3; slot++) {
            ItemStack potion = inv.getItem(slot);
            
            if (potion == null || !isPotion(potion)) {
                continue;
            }
            
            // Get current levels
            ItemMeta meta = potion.getItemMeta();
            if (meta == null) continue;
            
            int currentTimeLevel = meta.getPersistentDataContainer().getOrDefault(potionTimeLevelKey, PersistentDataType.INTEGER, 0);
            int currentPowerLevel = meta.getPersistentDataContainer().getOrDefault(potionPowerLevelKey, PersistentDataType.INTEGER, 0);
            
            // Get effect type key from NBT (handles both normal and fly potions)
            String effectTypeKey = meta.getPersistentDataContainer().get(potionEffectTypeKey, PersistentDataType.STRING);
            if (effectTypeKey == null) {
                // Not a master potion, try to get base effect
                PotionEffectType effectType = getBasePotionEffect(potion);
                if (effectType == null) continue;
                effectTypeKey = effectType.getKey().getKey();
            }
            
            // Get PotionEffectType (null for fly)
            PotionEffectType effectType = null;
            if (!effectTypeKey.equals("fly")) {
                effectType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effectTypeKey));
                if (effectType == null) continue;
            }
            
            if (isRedstone) {
                // Upgrade time
                int nextLevel = currentTimeLevel + 1;
                if (nextLevel > maxTimeLevel) {
                    continue; // Max level reached
                }
                
                int[] upgrade = timeUpgrades.get(nextLevel);
                if (upgrade == null) continue;
                
                int redstoneCost = upgrade[0];
                int duration = upgrade[1];
                
                // Check if enough redstone
                if (ingredient.getAmount() < redstoneCost) {
                    continue;
                }
                
                // Consume redstone
                ingredient.setAmount(ingredient.getAmount() - redstoneCost);
                
                // Apply upgrade
                applyTimeUpgrade(potion, effectTypeKey, effectType, currentPowerLevel, nextLevel, duration);
                
            } else if (isGlowstone) {
                // Upgrade power
                int nextLevel = currentPowerLevel + 1;
                if (nextLevel > maxPowerLevel) {
                    continue; // Max level reached
                }
                
                Integer glowstoneCost = powerUpgrades.get(nextLevel);
                if (glowstoneCost == null) continue;
                
                // Check if enough glowstone
                if (ingredient.getAmount() < glowstoneCost) {
                    continue;
                }
                
                // Consume glowstone
                ingredient.setAmount(ingredient.getAmount() - glowstoneCost);
                
                // Apply upgrade
                applyPowerUpgrade(potion, effectTypeKey, effectType, currentTimeLevel, nextLevel);
            }
        }
        
        // Update ingredient in inventory
        inv.setIngredient(ingredient);
    }
    
    /**
     * Applies time upgrade to a potion
     */
    /**
     * Applies time upgrade to a potion
     */
    private void applyTimeUpgrade(ItemStack potion, String effectTypeKey, PotionEffectType effectType, int currentPowerLevel, int newTimeLevel, int duration) {
        if (!(potion.getItemMeta() instanceof PotionMeta)) return;
        
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        
        // If this is the first time upgrade and no duration was stored, preserve vanilla duration first
        if (!meta.getPersistentDataContainer().has(potionDurationKey, PersistentDataType.INTEGER)) {
            if (!effectTypeKey.equals("fly") && effectType != null) {
                int vanillaDuration = extractVanillaPotionDuration(meta, effectType);
                meta.getPersistentDataContainer().set(potionDurationKey, PersistentDataType.INTEGER, vanillaDuration);
            }
        }
        
        // Store time level
        meta.getPersistentDataContainer().set(potionTimeLevelKey, PersistentDataType.INTEGER, newTimeLevel);
        meta.getPersistentDataContainer().set(potionDurationKey, PersistentDataType.INTEGER, duration);
        meta.getPersistentDataContainer().set(potionEffectTypeKey, PersistentDataType.STRING, effectTypeKey);
        meta.getPersistentDataContainer().set(masterPotionKey, PersistentDataType.BYTE, (byte) 1);
        
        // Clear base potion type to remove vanilla text
        meta.setBasePotionType(PotionType.WATER);
        
        // Set the correct color for this effect type
        if (effectTypeKey.equals("fly")) {
            meta.setColor(org.bukkit.Color.ORANGE);
        } else {
            meta.setColor(getPotionColor(effectType));
        }
        
        // Add custom effect - this displays the correct vanilla effect information (skip for fly)
        meta.clearCustomEffects();
        if (!effectTypeKey.equals("fly") && effectType != null) {
            meta.addCustomEffect(new PotionEffect(effectType, duration * 20, currentPowerLevel, false, false, false), true);
        }
        
        // Update display name
        String effectName = effectTypeKey.equals("fly") ? "Fly" : formatEffectName(effectType);
        int displayLevel = currentPowerLevel + 1; // Power level 0 = I, 1 = II, etc.
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
            float flightSpeed = 0.1f * (1.0f + (currentPowerLevel * 0.2f));
            flightSpeed = Math.min(flightSpeed, 1.0f);
            int speedPercent = (int)((flightSpeed / 0.1f) * 100);
            
            lore.add(Component.text("Flight Speed: " + speedPercent + "%", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Duration: " + formatDuration(duration), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        
        potion.setItemMeta(meta);
    }
    
    /**
     * Applies power upgrade to a potion
     */
    private void applyPowerUpgrade(ItemStack potion, String effectTypeKey, PotionEffectType effectType, int currentTimeLevel, int newPowerLevel) {
        if (!(potion.getItemMeta() instanceof PotionMeta)) return;
        
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        
        // Store power level
        meta.getPersistentDataContainer().set(potionPowerLevelKey, PersistentDataType.INTEGER, newPowerLevel);
        meta.getPersistentDataContainer().set(potionEffectTypeKey, PersistentDataType.STRING, effectTypeKey);
        meta.getPersistentDataContainer().set(masterPotionKey, PersistentDataType.BYTE, (byte) 1);
        
        // Get duration - preserve vanilla duration if no time upgrades applied yet
        int duration = 0;
        if (currentTimeLevel > 0) {
            // Use upgraded duration
            int[] upgrade = timeUpgrades.get(currentTimeLevel);
            if (upgrade != null) {
                duration = upgrade[1];
            }
        } else {
            // Check if vanilla potion already had duration stored
            if (meta.getPersistentDataContainer().has(potionDurationKey, PersistentDataType.INTEGER)) {
                duration = meta.getPersistentDataContainer().get(potionDurationKey, PersistentDataType.INTEGER);
            } else {
                // For fly at level 0, use 180 seconds
                if (effectTypeKey.equals("fly")) {
                    duration = 180;
                } else if (effectType != null) {
                    // Extract vanilla potion duration before we clear it
                    duration = extractVanillaPotionDuration(meta, effectType);
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
        } else {
            meta.setColor(getPotionColor(effectType));
        }
        
        // Add custom effect - this displays the correct vanilla effect information (skip for fly)
        meta.clearCustomEffects();
        if (!effectTypeKey.equals("fly") && effectType != null) {
            meta.addCustomEffect(new PotionEffect(effectType, duration * 20, newPowerLevel, false, false, false), true);
        }
        
        // Update display name
        String effectName = effectTypeKey.equals("fly") ? "Fly" : formatEffectName(effectType);
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
        }
        
        meta.lore(lore);
        
        potion.setItemMeta(meta);
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
            // Handle normal potion effects
            PotionEffectType effectType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effectTypeKey));
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
        String effectName = effectTypeKey.equals("fly") ? "Fly" : formatEffectName(PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effectTypeKey)));
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
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        
        // Save effects before removing
        savePlayerEffects(uuid);
        
        // Remove from active tracking
        activeMasterEffects.remove(uuid);
    }
    
    /**
     * Restore tracked effects when player reconnects
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        
        // Load saved effects
        loadPlayerEffects(uuid);
    }
    
    /**
     * Saves a player's active master potion effects to their playerdata file
     */
    private void savePlayerEffects(UUID uuid) {
        List<ActiveMasterEffect> effects = activeMasterEffects.get(uuid);
        
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
                
                // Validate effect type exists (skip validation for fly)
                if (!effectKey.equals("fly")) {
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
            return PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effectKey));
        }
        
        // Get from base potion type
        PotionType potionType = meta.getBasePotionType();
        if (potionType == null) {
            return null;
        }
        
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
        
        return PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effectName));
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
                return org.bukkit.Color.fromRGB(0x4a4217);
            case "strength":
                return org.bukkit.Color.fromRGB(0xffc700);
            case "instant_health":
                return org.bukkit.Color.fromRGB(0xf82423);
            case "instant_damage":
                return org.bukkit.Color.fromRGB(0xa9656a);
            case "jump_boost":
                return org.bukkit.Color.fromRGB(0xfdff84);
            case "nausea":
                return org.bukkit.Color.fromRGB(0x551d4a);
            case "regeneration":
                return org.bukkit.Color.fromRGB(0xcd5cab);
            case "resistance":
                return org.bukkit.Color.fromRGB(0x9146f0);
            case "fire_resistance":
                return org.bukkit.Color.fromRGB(0xff9900);
            case "water_breathing":
                return org.bukkit.Color.fromRGB(0x2e5299);
            case "invisibility":
                return org.bukkit.Color.fromRGB(0xf6f6f6);
            case "blindness":
                return org.bukkit.Color.fromRGB(0x1f1f23);
            case "night_vision":
                return org.bukkit.Color.fromRGB(0xc2ff66);
            case "hunger":
                return org.bukkit.Color.fromRGB(0x587653);
            case "weakness":
                return org.bukkit.Color.fromRGB(0x484d48);
            case "poison":
                return org.bukkit.Color.fromRGB(0x87a363);
            case "wither":
                return org.bukkit.Color.fromRGB(0x352a27);
            case "health_boost":
                return org.bukkit.Color.fromRGB(0xf87d23);
            case "absorption":
                return org.bukkit.Color.fromRGB(0x2552a5);
            case "saturation":
                return org.bukkit.Color.fromRGB(0xf82423);
            case "glowing":
                return org.bukkit.Color.fromRGB(0x94a061);
            case "levitation":
                return org.bukkit.Color.fromRGB(0xceffff);
            case "luck":
                return org.bukkit.Color.fromRGB(0x59c106);
            case "unluck":
                return org.bukkit.Color.fromRGB(0xc0a44d);
            case "slow_falling":
                return org.bukkit.Color.fromRGB(0xf3cfb9);
            case "conduit_power":
                return org.bukkit.Color.fromRGB(0x1dc2d1);
            case "dolphins_grace":
                return org.bukkit.Color.fromRGB(0x88a3be);
            case "bad_omen":
                return org.bukkit.Color.fromRGB(0x0b6138);
            case "hero_of_the_village":
                return org.bukkit.Color.fromRGB(0x44ff44);
            case "darkness":
                return org.bukkit.Color.fromRGB(0x292721);
            default:
                return org.bukkit.Color.fromRGB(0x385DC6); // Default blue
        }
    }
    
    /**
     * Formats duration in seconds to readable string
     */
    private String formatDuration(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return secs > 0 ? minutes + "m " + secs + "s" : minutes + "m";
        } else {
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        }
    }
    
    // ==================== COMMANDS ====================
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "give":
                return handleGive(sender, args);
            case "reload":
                return handleReload(sender);
            case "help":
                sendHelp(sender);
                return true;
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
        
        // Determine time and power levels
        boolean isMaxVariant = false;
        boolean useDefaults = false;
        
        if (args.length == 4) {
            // No levels specified, use defaults (1,1 or 0,0 for fly)
            useDefaults = true;
        } else if (args.length == 5 && args[4].equalsIgnoreCase("max")) {
            // Max variant
            isMaxVariant = true;
        } else if (args.length < 6) {
            // Invalid - need either nothing (defaults), "max", or both levels
            sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> <potion>", NamedTextColor.RED)
                .append(Component.text(" - defaults: 1,1 (or 0,0 for fly)", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> <potion> <time_lvl> <power_lvl>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> <potion> max", NamedTextColor.RED));
            sender.sendMessage(Component.text("Usage: /masterbrewing give potion <player> random", NamedTextColor.RED));
            return true;
        }
        
        // Map potion names to effect type keys
        String effectKey;
        switch (potionName) {
            case "fly":
                effectKey = "fly";
                break;
            case "speed":
                effectKey = "speed";
                break;
            case "slowness":
            case "slow":
                effectKey = "slowness";
                break;
            case "haste":
                effectKey = "haste";
                break;
            case "mining_fatigue":
                effectKey = "mining_fatigue";
                break;
            case "strength":
                effectKey = "strength";
                break;
            case "instant_health":
            case "healing":
                effectKey = "instant_health";
                break;
            case "instant_damage":
            case "harming":
                effectKey = "instant_damage";
                break;
            case "jump_boost":
            case "leaping":
                effectKey = "jump_boost";
                break;
            case "nausea":
                effectKey = "nausea";
                break;
            case "regeneration":
                effectKey = "regeneration";
                break;
            case "resistance":
                effectKey = "resistance";
                break;
            case "fire_resistance":
                effectKey = "fire_resistance";
                break;
            case "water_breathing":
                effectKey = "water_breathing";
                break;
            case "invisibility":
                effectKey = "invisibility";
                break;
            case "blindness":
                effectKey = "blindness";
                break;
            case "night_vision":
                effectKey = "night_vision";
                break;
            case "hunger":
                effectKey = "hunger";
                break;
            case "weakness":
                effectKey = "weakness";
                break;
            case "poison":
                effectKey = "poison";
                break;
            case "wither":
                effectKey = "wither";
                break;
            case "health_boost":
                effectKey = "health_boost";
                break;
            case "absorption":
                effectKey = "absorption";
                break;
            case "saturation":
                effectKey = "saturation";
                break;
            case "glowing":
                effectKey = "glowing";
                break;
            case "levitation":
                effectKey = "levitation";
                break;
            case "luck":
                effectKey = "luck";
                break;
            case "unluck":
                effectKey = "unluck";
                break;
            case "slow_falling":
                effectKey = "slow_falling";
                break;
            case "conduit_power":
                effectKey = "conduit_power";
                break;
            case "dolphins_grace":
                effectKey = "dolphins_grace";
                break;
            case "bad_omen":
                effectKey = "bad_omen";
                break;
            case "hero_of_the_village":
                effectKey = "hero_of_the_village";
                break;
            case "darkness":
                effectKey = "darkness";
                break;
            default:
                sender.sendMessage(Component.text("Invalid potion type: " + potionName, NamedTextColor.RED));
                sender.sendMessage(Component.text("Available types: fly, speed, slowness, haste, mining_fatigue, strength, healing, harming, leaping, nausea, regeneration, resistance, fire_resistance, water_breathing, invisibility, blindness, night_vision, hunger, weakness, poison, wither, health_boost, absorption, saturation, glowing, levitation, luck, unluck, slow_falling, conduit_power, dolphins_grace, bad_omen, hero_of_the_village, darkness", NamedTextColor.GRAY));
                return true;
        }
        
        // Get effect type using NamespacedKey (skip for custom fly potion)
        PotionEffectType effectType = null;
        if (!effectKey.equals("fly")) {
            effectType = PotionEffectType.getByKey(NamespacedKey.minecraft(effectKey));
            if (effectType == null) {
                sender.sendMessage(Component.text("Failed to load potion effect type: " + effectKey, NamedTextColor.RED));
                return true;
            }
        }
        
        int timeLevel;
        int powerLevel;
        
        if (useDefaults) {
            // Special case: fly defaults to 0,0 (base speed, 3 min duration)
            if (potionName.equals("fly")) {
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
                timeLevel = Integer.parseInt(args[4]);
                powerLevel = Integer.parseInt(args[5]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Time level and power level must be valid numbers!", NamedTextColor.RED));
                return true;
            }
        }
        
        // Validate levels (allow 0 for fly potion)
        boolean isFlyWithZeroLevels = potionName.equals("fly") && (timeLevel == 0 || powerLevel == 0);
        if (!isFlyWithZeroLevels && (timeLevel < 1 || powerLevel < 1)) {
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
        if (potionName.equals("fly") && timeLevel == 0) {
            // Fly potion at level 0 has 3 minute (180 second) duration
            duration = 180;
        } else {
            if (!timeUpgrades.containsKey(timeLevel)) {
                sender.sendMessage(Component.text("Invalid time level: " + timeLevel + "! Valid levels are 1-" + maxTimeLevel, NamedTextColor.RED));
                return true;
            }
            duration = timeUpgrades.get(timeLevel)[1];
        }
        
        // Validate power level exists in config (skip for fly at level 0)
        if (!(potionName.equals("fly") && powerLevel == 0)) {
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
     */
    private boolean handleGiveRandomPotion(CommandSender sender, Player target) {
        // Define all available potion types
        String[] potionTypes = {
            "fly", "speed", "slowness", "haste", "mining_fatigue", "strength",
            "healing", "harming", "leaping", "nausea", "regeneration",
            "resistance", "fire_resistance", "water_breathing", "invisibility",
            "blindness", "night_vision", "hunger", "weakness", "poison",
            "wither", "health_boost", "absorption", "saturation", "glowing",
            "levitation", "luck", "unluck", "slow_falling", "conduit_power",
            "dolphins_grace", "bad_omen", "hero_of_the_village", "darkness"
        };
        
        // Pick random potion type
        Random random = new Random();
        String potionName = potionTypes[random.nextInt(potionTypes.length)];
        
        // Pick random levels
        int timeLevel = random.nextInt(maxTimeLevel) + 1; // 1 to maxTimeLevel
        int powerLevel = random.nextInt(maxPowerLevel) + 1; // 1 to maxPowerLevel
        
        // Map potion name to effect key
        String effectKey;
        switch (potionName) {
            case "slowness":
                effectKey = "slowness";
                break;
            case "haste":
                effectKey = "haste";
                break;
            case "mining_fatigue":
                effectKey = "mining_fatigue";
                break;
            case "healing":
                effectKey = "instant_health";
                break;
            case "harming":
                effectKey = "instant_damage";
                break;
            case "leaping":
                effectKey = "jump_boost";
                break;
            case "nausea":
                effectKey = "nausea";
                break;
            case "resistance":
                effectKey = "resistance";
                break;
            default:
                effectKey = potionName; // Most match directly
                break;
        }
        
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
            // Potion types + random
            completions.add("random");
            completions.add("fly");
            completions.addAll(Arrays.asList(
                "speed", "slowness", "haste", "mining_fatigue", "strength",
                "healing", "harming", "leaping", "nausea", "regeneration",
                "resistance", "fire_resistance", "water_breathing", "invisibility",
                "blindness", "night_vision", "hunger", "weakness", "poison",
                "wither", "health_boost", "absorption", "saturation", "glowing",
                "levitation", "luck", "unluck", "slow_falling", "conduit_power",
                "dolphins_grace", "bad_omen", "hero_of_the_village", "darkness"
            ));
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