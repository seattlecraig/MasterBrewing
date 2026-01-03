# MasterBrewing End User Guide

Welcome to MasterBrewing! This guide will help you understand how to use Master Brewing Stands and Master Potions to create powerful potions that go far beyond vanilla Minecraft's limits.

## What is MasterBrewing?

MasterBrewing is a plugin that introduces enhanced potion brewing. Unlike vanilla Minecraft where potions have fixed durations and strength levels, Master Potions can be upgraded to have much longer durations and more powerful effects.

## Master Brewing Stands

A Master Brewing Stand looks like a regular brewing stand but has special properties. You can identify one by its gold name and special lore text when you hover over it in your inventory.

### How to Get a Master Brewing Stand

You can obtain a Master Brewing Stand in two ways:
- An admin can give you one using a command
- Open a virtual Master Brewing Stand by typing `/masterbrewing` (if you have permission)

### Placing and Breaking Master Brewing Stands

When you place a Master Brewing Stand, it remembers its contents. If you break it, all items inside (potions, ingredients, fuel) are saved to the item. When you place it again, everything is restored exactly as you left it.

## How to Brew Master Potions

Master Brewing uses the same interface as regular brewing, but the ingredients work differently:

### Step 1: Start with a Base Potion
Place any vanilla potion that has an effect into one of the three potion slots. The potion must already have an effect (like Speed, Strength, Regeneration, etc.). Water bottles and Awkward Potions won't work.

### Step 2: Add Fuel
Just like regular brewing, you need Blaze Powder for fuel. Place Blaze Powder in the fuel slot (bottom left). One Blaze Powder provides 20 brewing charges.

### Step 3: Choose Your Upgrade

You have two upgrade paths:

**Duration Upgrade (Redstone Dust)**
- Add Redstone Dust to the ingredient slot (top center)
- This increases how long the potion effect lasts
- The higher the upgrade level, the more Redstone Dust required
- Each upgrade tier grants a specific duration

**Power Upgrade (Glowstone Dust)**
- Add Glowstone Dust to the ingredient slot (top center)
- This increases the strength/amplifier of the effect
- Speed II becomes Speed III, then Speed IV, and so on
- The higher the upgrade level, the more Glowstone Dust required

### Step 4: Brewing Happens Instantly
Unlike vanilla brewing which takes time, Master Brewing processes upgrades instantly when you have the right materials.

## Understanding Master Potions

Once a potion has been upgraded, it becomes a Master Potion with special properties:

### Visual Identification
- The potion name appears in gold italic text
- The name shows the effect and Roman numeral level (e.g., "Speed IV")
- The lore shows "Master Potion" in light purple
- Additional lore lines show current stats and upgrade costs

### Reading the Lore
When you hover over a Master Potion, you'll see:
- **Master Potion** - Identifies it as a Master Potion
- **Power Level** - Shows current level and maximum possible
- **Duration** - Shows current duration and maximum possible
- **Upgrade Costs** - Shows what materials are needed for the next upgrade (if not at max)

When a stat shows "(MAX)", you've reached the highest level for that upgrade type.

## Using Master Potions

Master Potions work differently from vanilla potions:

### Instant Activation
- Right-click to instantly activate the potion
- There is no drinking animation
- The effect is applied immediately
- This is faster than vanilla potions, which is useful in combat

### Effect Tracking
When you use a Master Potion, you'll see a message confirming:
- The effect name and level
- The duration
- That the potion has been activated

### Duration Warnings
As your effect nears expiration, you'll receive warnings:
- At 30 seconds remaining
- At 10 seconds remaining

This gives you time to prepare another potion if needed.

## Special Effects

MasterBrewing includes two custom effects not found in vanilla Minecraft:

### Fly Potion
- Grants survival flight (like Creative mode, but temporary)
- Flight speed increases with power level upgrades
- The action bar (above your hotbar) shows remaining flight time
- When the effect expires, you'll stop flying (be careful of fall damage!)

### Fortune Potion
- Enhances your luck for better loot drops
- Higher power levels mean even better luck
- Works on mob drops and chest loot

## Effect Persistence

One of the best features of Master Potions is that effects persist:

### Logout/Login
If you log out while an effect is active, it will be restored when you log back in. The timer continues while you're offline, so if you had 10 minutes left and return 5 minutes later, you'll have 5 minutes remaining.

### Server Restarts
Effects also survive server restarts. Your active effects are saved and restored automatically.

## Virtual Brewing Stand

If you have the `masterbrewing.use` permission, you can access a virtual Master Brewing Stand:

### Opening the Virtual Stand
- Type `/masterbrewing` with no arguments
- A brewing interface opens without needing a physical block

### Features
- Works exactly like a placed Master Brewing Stand
- Contents are saved when you close it
- Your saved items and fuel return when you open it again
- Each player has their own virtual stand

## Commands for Players

| Command | Description |
|---------|-------------|
| `/masterbrewing` | Opens your virtual Master Brewing Stand |
| `/masterbrewing help` | Shows help information and upgrade tier tables |

## Tips for Best Results

1. **Plan Your Upgrades** - Check the lore on your potion to see upgrade costs before adding materials

2. **Batch Brewing** - You can upgrade all three potion slots at once if they share the same effect type

3. **Balance Duration vs Power** - Sometimes a longer-lasting weaker effect is better than a short powerful one

4. **Watch Your Materials** - Higher upgrade levels require significantly more materials

5. **Upgrade Incrementally** - You must upgrade one level at a time; you can't skip levels

6. **Keep Fuel Ready** - Always have Blaze Powder in the fuel slot before adding ingredients

7. **Virtual Stand Convenience** - Use `/masterbrewing` when you're away from your base

## Potion Effects Available

Master Brewing supports all standard Minecraft potion effects plus the custom Fly and Fortune effects. Some examples:

- Speed, Slowness
- Strength, Weakness
- Regeneration, Poison
- Fire Resistance, Water Breathing
- Night Vision, Invisibility
- Jump Boost (Leaping), Slow Falling
- Haste, Mining Fatigue
- Resistance, Health Boost
- Absorption, Saturation
- Luck, Unluck
- Conduit Power, Dolphin's Grace
- And many more!

**Note:** Instant effects (Healing, Harming, Saturation) can only be upgraded with Glowstone for more power. They cannot be upgraded with Redstone since they have no duration.

## Summary

MasterBrewing transforms Minecraft's potion system into something much more powerful. With Master Potions, you can create effects that last for hours instead of minutes, and reach strength levels far beyond what vanilla brewing allows. Use your Master Brewing Stand wisely, and you'll have access to some of the most powerful buffs in the game!
