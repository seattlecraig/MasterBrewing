# MasterBrewing

**Create limitless potion upgrades with Master Brewing Stands!**

A Paper/Spigot plugin that extends Minecraft's brewing system with unlimited potion upgrades. Brew potions beyond vanilla limitations using configurable upgrade tiers for both duration and power.

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Minecraft](https://img.shields.io/badge/minecraft-1.21+-green.svg)
![License](https://img.shields.io/badge/license-MIT-yellow.svg)

## ✨ Features

### Core Functionality
- **Unlimited Upgrades**: Extend potion duration and power far beyond vanilla limits
- **Dual Upgrade Paths**: Use redstone for duration, glowstone for power
- **Independent Progression**: Upgrade time and power separately
- **Configurable Tiers**: Server admins control costs and max levels
- **Per-Potion Overrides**: Different potions can have unique upgrade paths

### Special Potions
- **✈️ Flight Potion**: Creative-style flight in survival mode with configurable speed
- **🍀 Fortune Potion**: Extreme luck levels for better loot and drops
- **All Vanilla Effects**: Every standard Minecraft potion effect supported

### Player Experience
- **Instant Activation**: Master Potions activate instantly - no drinking animation
- **Visual Distinction**: Gold italic names, comprehensive lore, custom colors
- **Clear Progression**: Lore shows current stats, max stats, and upgrade costs
- **Effect Persistence**: Effects survive logout and server restart
- **Multiple Effects**: Have multiple different potion effects active simultaneously

### Technical Features
- **NBT-Based Storage**: All data stored in Minecraft's NBT system
- **State Preservation**: Brewing stands remember their contents when broken
- **Effect Refresh System**: Background task ensures effects remain active
- **Performance Optimized**: Minimal server impact even with many players
- **No Dependencies**: Runs standalone on Paper/Spigot 1.21+

## 📦 Installation

1. Download `MasterBrewing.jar`
2. Place in your server's `/plugins/` folder
3. Restart your server
4. Configure `plugins/MasterBrewing/config.yml` (optional)
5. Give Master Brewing Stands to players: `/masterbrewing give stand <player>`

**Requirements:**
- Paper or Spigot 1.21+
- Java 21+

## 🎮 Quick Start

### For Players

1. **Obtain a Master Brewing Stand** from a server admin
2. **Brew a vanilla potion** (e.g., Speed using sugar)
3. **Upgrade with redstone** (increases duration) or **glowstone** (increases power)
4. **Right-click to consume** - effect activates instantly!

### For Server Admins

1. **Give brewing stands**: `/masterbrewing give stand <player>`
2. **Give potions**: `/masterbrewing give potion <player> <type> [time] [power]`
3. **Configure upgrades**: Edit `config.yml` to set costs and max levels
4. **Reload config**: `/masterbrewing reload`

## 📖 Documentation

Comprehensive guides are available for different audiences:

- **[End User Guide](enduser.md)** - For players using Master Potions
- **[Server Admin Guide](serveradmin.md)** - For configuring and managing the plugin
- **[Developer Guide](devguide.md)** - For developers wanting to understand or extend the code
- **[In-Game Book Guide](ingame_book.txt)** - For creating Minecraft books with instructions

## ⚙️ Configuration

### Default Upgrade Path

```yaml
# Time upgrades (redstone)
upgrade-time:
  - "1,1,360"       # Level 1: 1 redstone = 6 minutes
  - "2,4,720"       # Level 2: 4 redstone = 12 minutes
  - "3,16,1080"     # Level 3: 16 redstone = 18 minutes
  - "4,32,1440"     # Level 4: 32 redstone = 24 minutes
  - "5,64,1800"     # Level 5: 64 redstone = 30 minutes

# Power upgrades (glowstone)
upgrade-power:
  - "1,1"           # Level 1: 1 glowstone = Power II
  - "2,4"           # Level 2: 4 glowstone = Power III
  - "3,16"          # Level 3: 16 glowstone = Power IV
  - "4,32"          # Level 4: 32 glowstone = Power V
  - "5,64"          # Level 5: 64 glowstone = Power VI
```

### Per-Potion Customization

```yaml
# Fortune potion with custom 64-level progression
fortune:
  upgrade-time:
    - "1,1,360"
    - "2,2,720"
    # ... up to 64 levels possible
  upgrade-power:
    - "1,1"
    - "2,2"
    # ... up to 64 levels possible
```

See [Server Admin Guide](serveradmin.md) for complete configuration documentation.

## 🔧 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/masterbrewing help` | None | Show help and upgrade tables |
| `/masterbrewing give stand <player>` | `masterbrewing.give` | Give a Master Brewing Stand |
| `/masterbrewing give potion <player> <type> [time] [power]` | `masterbrewing.give` | Give a master potion |
| `/masterbrewing give potion <player> random` | `masterbrewing.give` | Give random potion |
| `/masterbrewing reload` | `masterbrewing.admin` | Reload configuration |

## 🎯 Use Cases

### Survival Servers
- Long-term progression system
- Endgame content for established players
- Reward for gathering resources

### PvP Servers  
- Powerful buffs for combat
- Strategic potion selection
- Balanced through material costs

### Economy Servers
- Valuable commodity for trading
- Shop integration (sell stands/potions)
- Resource sink for excess materials

### Event Servers
- High-tier rewards for competitions
- Unique prizes for achievements
- Special event-only potions

## 🔬 Technical Details

- **Architecture**: Single-file plugin following SupaFloof Games standards
- **Storage**: NBT-based persistent data on items and blocks
- **Effect System**: Background task running every 3 seconds
- **Data Persistence**: YAML files in `plugins/MasterBrewing/playerdata/`
- **Performance**: Negligible impact (<0.1ms per refresh cycle)
- **Optimization**: Static lookup maps, conditional refreshing, early bailouts

See [Developer Guide](devguide.md) for technical architecture details.

## 📊 Comparison with Vanilla

| Feature | Vanilla | MasterBrewing |
|---------|---------|---------------|
| Max Duration | 8 minutes | Unlimited (configurable) |
| Max Power | Level II | Unlimited (configurable) |
| Upgrade Cost | 1 item | Configurable progression |
| Flight in Survival | ❌ | ✅ |
| Effect Persistence | ❌ | ✅ (survives logout/restart) |
| Instant Use | ❌ | ✅ (no drinking animation) |
| Multiple Upgrades | ❌ (either/or) | ✅ (independent paths) |

## 🎨 Example Potions

**Speed X with 2 Hour Duration**
- Power Level: 9 (upgraded with glowstone 9 times)
- Time Level: 7 (upgraded with redstone 7 times)
- Total Cost: ~500 glowstone + ~500 redstone (depending on config)

**Fortune LXV with 60 Minutes**
- Power Level: 64 (maximum luck effect)
- Time Level: 6 (1 hour duration)
- Perfect for: Extended farming/fishing/mining sessions

**Flight V with 30 Minutes**
- Flight Speed: 220% of normal creative speed
- Duration: Half an hour of flying
- Perfect for: Building projects and exploration

## 🛠️ For Developers

### Building from Source

```bash
# Clone repository
git clone https://github.com/yourusername/MasterBrewing.git

# Build with Maven
cd MasterBrewing
mvn clean package

# Output: target/MasterBrewing-1.0.0.jar
```

### Code Structure

- Single Java file: `com.supafloof.masterbrewing.MasterBrewing`
- Extensive JavaDoc on all methods
- Event-driven architecture
- Performance-optimized with static lookup maps

### Extension Points

- Add custom potion effects (like Flight and Fortune)
- Modify upgrade cost calculations
- Add new commands and permissions
- Integrate with economy plugins

See [Developer Guide](devguide.md) for detailed technical documentation.

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes with clear commit messages
4. Add/update documentation as needed
5. Submit a pull request

## 📝 License

Copyright © 2024 SupaFloof Games, LLC

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/MasterBrewing/issues)
- **Documentation**: See guides in this repository
- **Contact**: SupaFloof Games, LLC

## 🌟 Acknowledgments

- Built for the Minecraft community
- Designed for Paper/Spigot servers
- Developed by SupaFloof Games, LLC

## 📋 Changelog

### Version 1.0.0
- Initial release
- Master Brewing Stands with unlimited upgrades
- Flight and Fortune custom potions
- Configurable upgrade tiers
- Per-potion upgrade overrides
- Effect persistence system
- Instant potion activation
- Comprehensive documentation

---

**Ready to brew beyond limits? Install MasterBrewing today!** ✨🧪

Made with ❤️ by SupaFloof Games, LLC
