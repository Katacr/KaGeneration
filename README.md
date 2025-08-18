# KaGeneration Plugin Documentation

[中文介绍](https://github.com/Katacr/KaGeneration/blob/main/README_CN.md)
## Overview

KaGeneration is a Bukkit plugin specifically designed for Minecraft skyblock survival servers. It modifies the blocks generated when water and lava meet, making it possible for stone and cobblestone to be replaced by ores with a certain probability, thus ensuring the smooth progress of survival gameplay. The plugin provides rich configuration options and features, allowing server administrators to customize the ore generation mechanism according to their needs.

## Core Features

### 1. Ore Generation System
- When water and lava meet to generate stone or cobblestone, they are replaced by ores according to the configured probability
- Supports multiple ore types: diamond ore, iron ore, coal ore, etc.
- Multi-permission group system, where players with different permissions enjoy different ore generation probabilities
- Priority system: when a player has multiple permissions, the highest priority group is used
- ItemsAdder block support

### 2. World Whitelist
- Only takes effect in configured worlds
- Supports any number of worlds
- By default, it takes effect in all worlds (when the whitelist is empty)
- Supports regular expression judgment

### 3. Feature Switches
- **Obsidian Conversion**: Right-clicking obsidian with an empty bucket in hand can obtain a lava bucket
- **Nether Water Buckets**: Allows placing water buckets in the Nether (bypassing vanilla restrictions)
- **Ice Block Melting**: Breaking ice blocks in the Nether can generate a water source

### 4. Commands
- `/kg reload` - Reload plugin configuration
- `/kg info` - Display current configuration status
- `/kg place [world] [x] [y] [z] [block] -s` - Place blocks at designated locations ("-s" Silent execution)
- `/kg help` - Display help information

### 5. PlaceholderAPI
- `%kageneration_level%` - Returns the player's current ore group name, e.g.: `level2`
- `%kageneration_level_display%` - Returns the player's current ore group customize name, e.g.: `Lv.2`
- `%kageneration_priority%` - Returns the player's current priority, e.g.: `2`
- `%kageneration_chance:Material%` - Returns the ore chance ，e.g.:`%kageneration_chance:diamond_ore%` Return:`5`

## Configuration File

The configuration file is located at `plugins/KaGeneration/config.yml`