# MasterBrewing

**Enhanced Potion Brewing for Minecraft Servers**

MasterBrewing is a Paper/Spigot plugin that breaks the boundaries of vanilla potion brewing. Create potions with unlimited duration upgrades, unlimited power levels, and enjoy effects that persist through logout and server restarts.

## ✨ Features

### Unlimited Upgrades
- **Duration**: Use Redstone Dust to extend potion duration far beyond vanilla limits—hours instead of minutes
- **Power**: Use Glowstone Dust to increase effect strength—Speed X, Strength VIII, and beyond

### Custom Effects
- **Flight Potion**: Grants survival mode flight with speed that increases per power level
- **Fortune Potion**: Enhanced luck for better loot from mobs and chests

### Instant Activation
Master Potions activate instantly on right-click—no drinking animation, perfect for combat situations.

### Effect Persistence
- Effects survive player logout and login
- Effects persist through server restarts
- Timer continues while offline (return in 10 minutes with 5 minutes left = 5 minutes remaining when you're back)

### Virtual Brewing Stands
Access Master Brewing anywhere with `/masterbrewing`—no physical block required. Your virtual stand remembers its contents between sessions.

### Configurable Everything
- Set custom upgrade costs per tier
- Configure different costs for different potions (make Fly expensive, Speed cheap)
- Define your own upgrade paths with custom durations

## 📦 Installation

1. Download the latest release
2. Place the JAR in your server's `plugins/` folder
3. Restart your server
4. Configure `plugins/MasterBrewing/config.yml` to your liking

## 🎮 Quick Start

1. Get a Master Brewing Stand: `/masterbrewing give stand <player>`
2. Place any vanilla potion with an effect in the stand
3. Add Blaze Powder for fuel
4. Add Redstone Dust to increase duration OR Glowstone Dust to increase power
5. Upgrades happen instantly!
6. Right-click your Master Potion to activate it

## 📖 Documentation

| Document | Description |
|----------|-------------|
| [End User Guide](enduser.md) | Complete guide for players using Master Potions |
| [Server Admin Guide](serveradmin.md) | Installation, configuration, permissions, and commands |
| [Developer Guide](devguide.md) | Code architecture, API, and extension points |

## 🔧 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/masterbrewing` | `masterbrewing.use` | Open virtual brewing stand |
| `/masterbrewing help` | — | Show help and upgrade tiers |
| `/masterbrewing give stand <player>` | `masterbrewing.give` | Give Master Brewing Stand |
| `/masterbrewing give potion <player> <type> [time] [power]` | `masterbrewing.give` | Give Master Potion |
| `/masterbrewing reload` | `masterbrewing.admin` | Reload configuration |

## 🔐 Permissions

| Permission | Description |
|------------|-------------|
| `masterbrewing.use` | Access virtual brewing stand |
| `masterbrewing.give` | Give Master Brewing items |
| `masterbrewing.admin` | Reload configuration |

## ⚙️ Configuration Example

```yaml
# Global upgrade tiers
upgrade-time:
  - "1,4,600"      # Level 1: 4 redstone = 10 minutes
  - "2,8,1800"     # Level 2: 8 redstone = 30 minutes
  - "3,16,3600"    # Level 3: 16 redstone = 1 hour

upgrade-power:
  - "1,4"          # Level 1: 4 glowstone = Level II
  - "2,8"          # Level 2: 8 glowstone = Level III
  - "3,16"         # Level 3: 16 glowstone = Level IV

# Custom costs for specific potions
fly:
  upgrade-time:
    - "1,8,300"    # Fly is expensive!
    - "2,16,600"
  upgrade-power:
    - "1,8"
    - "2,16"
```

## 💡 Why MasterBrewing?

Vanilla Minecraft potions are limited:
- Maximum 8 minute duration
- Maximum Level II for most effects
- Effects lost on logout
- No survival flight

MasterBrewing removes these limits while maintaining balance through configurable material costs. Want a 4-hour Speed VIII potion? You can have it—if you can afford the materials.

## 🔌 Compatibility

- **Minecraft**: 1.20.x+
- **Server**: Paper (recommended), Spigot
- **Java**: 17+
- **SpecialBooks**: Auto-pickup enchantment supported

## 📝 License

Copyright © SupaFloof Games, LLC

## 🌐 Links

- **Server**: [playmc.supafloof.com](https://playmc.supafloof.com)
