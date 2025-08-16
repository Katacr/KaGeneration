package org.katacr.kaGeneration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatMessageType;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public class KaGeneration extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private FileConfiguration langConfig;
    private final Map<String, Map<Material, Integer>> generationGroups = new HashMap<>();
    private final Map<String, Integer> groupPriorities = new HashMap<>();

    // 支持替换的方块类型
    private final List<Material> SUPPORTED_BLOCKS = Arrays.asList(Material.STONE, Material.COBBLESTONE);

    // 修改世界白名单存储结构
    private List<String> worldPatterns = new ArrayList<>();
    private final List<Pattern> compiledPatterns = new ArrayList<>();

    // 功能开关
    private boolean lavaBucketEnabled = true;
    private boolean allowWaterInNether = true;
    private boolean generateWaterFromIce = true;
    private String languageCode = "zh_CN"; // 默认语言

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        config = getConfig();

        // 加载语言设置
        loadLanguageSetting();

        // 加载语言文件
        loadLangFile();

        // 加载配置
        loadConfigSettings();

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        // 注册命令
        Objects.requireNonNull(this.getCommand("kageneration")).setExecutor(this);
        Objects.requireNonNull(this.getCommand("kg")).setExecutor(this);
        // 注册命令补全器
        Objects.requireNonNull(this.getCommand("kageneration")).setTabCompleter(this);
        Objects.requireNonNull(this.getCommand("kg")).setTabCompleter(this);
        // 注册 PlaceholderAPI 扩展
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new KagenerationPlaceholder(this).register();
            getLogger().info(getLang("logs.placeholder_hooked"));
        }

        getLogger().info(getLang("logs.enable"));
    }

    // 加载语言设置
    private void loadLanguageSetting() {
        languageCode = config.getString("Language", "zh_CN");
        getLogger().info("已选择语言: " + languageCode);
    }

    // 加载语言文件
    private void loadLangFile() {
        try {
            File langDir = new File(getDataFolder(), "lang");

            // 确保语言目录存在
            if (!langDir.exists()) {
                // 处理 mkdirs() 的返回值
                boolean dirsCreated = langDir.mkdirs();
                if (dirsCreated) {
                    getLogger().info("已创建语言目录: " + langDir.getAbsolutePath());
                } else {
                    getLogger().warning("无法创建语言目录: " + langDir.getAbsolutePath());
                    // 尝试使用默认目录
                    langDir = getDataFolder();
                }
            }

            // 构建语言文件路径
            String langFileName = "lang_" + languageCode + ".yml";
            File langFile = new File(langDir, langFileName);

            // 如果语言文件不存在，尝试从资源复制
            if (!langFile.exists()) {
                // 尝试从JAR中复制默认语言文件
                saveResource("lang/" + langFileName, false);

                // 如果复制失败，使用默认语言文件
                if (!langFile.exists()) {
                    getLogger().warning("找不到语言文件: " + langFileName + ", 使用默认语言文件");
                    langFile = new File(langDir, "lang_zh_CN.yml");
                    if (!langFile.exists()) {
                        saveResource("lang/lang_zh_CN.yml", false);
                    }
                }
            }

            // 加载语言文件
            langConfig = YamlConfiguration.loadConfiguration(langFile);
            getLogger().info("已加载语言文件: " + langFile.getName());

            // 加载默认语言文件（内置于JAR中）
            InputStream defaultLangStream = getResource("lang/lang_" + languageCode + ".yml");
            if (defaultLangStream != null) {
                YamlConfiguration defaultLangConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultLangStream, StandardCharsets.UTF_8));
                langConfig.setDefaults(defaultLangConfig);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "加载语言文件失败", e);
            langConfig = null;
        }
    }

    // 获取语言字符串
    private String getLang(String path) {
        return getLang(path, new HashMap<>());
    }

    // 获取语言字符串（带变量替换）
    private String getLang(String path, Map<String, String> replacements) {
        // 安全检查：确保 langConfig 不为 null
        if (langConfig == null) {
            return ChatColor.translateAlternateColorCodes('&', "&c语言文件未加载: " + path);
        }

        String message = langConfig.getString(path, path);

        // 替换变量
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        // 转换颜色代码
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getPlayerGroup(Player player) {
        return getApplicableGroup(player);
    }

    // 添加获取玩家优先级的方法
    public int getPlayerPriority(Player player) {
        return getApplicablePriority(player);
    }

    // 获取适用的优先级
    private int getApplicablePriority(Player player) {
        // 如果没有找到玩家，使用默认组优先级
        if (player == null) {
            return groupPriorities.getOrDefault("default", 0);
        }

        int highestPriority = -1;

        // 遍历所有配置的权限组
        for (Map.Entry<String, Integer> entry : groupPriorities.entrySet()) {
            String groupName = entry.getKey();
            int priority = entry.getValue();

            // 检查玩家是否有该组权限
            String permissionNode = "kageneration." + groupName;
            if (player.hasPermission(permissionNode) && priority > highestPriority) {
                highestPriority = priority;
            }
        }

        // 如果没有找到任何权限组，使用默认组优先级
        if (highestPriority == -1) {
            return groupPriorities.getOrDefault("default", 0);
        }

        return highestPriority;
    }

    // PlaceholderAPI 扩展类
    private static class KagenerationPlaceholder extends PlaceholderExpansion {
        private final KaGeneration plugin;

        public KagenerationPlaceholder(KaGeneration plugin) {
            this.plugin = plugin;
        }

        @Override
        public @NotNull String getIdentifier() {
            return "kageneration";
        }

        @Override
        public @NotNull String getAuthor() {
            return plugin.getDescription().getAuthors().toString();
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
            if (player == null) return "";

            // 处理 %kageneration_level% 变量
            if ("level".equalsIgnoreCase(identifier)) {
                return plugin.getPlayerGroup(player);
            }

            // 处理 %kageneration_priority% 变量
            if ("priority".equalsIgnoreCase(identifier)) {
                return String.valueOf(plugin.getPlayerPriority(player));
            }

            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command cmd, @NotNull String alias, String[] args) {
        // 只处理 kageneration 和 kg 命令
        if (!cmd.getName().equalsIgnoreCase("kageneration") && !cmd.getName().equalsIgnoreCase("kg")) {
            return Collections.emptyList();
        }

        // 一级命令补全
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("reload", "info", "help"), new ArrayList<>());
        }

        // 二级命令补全（根据一级命令）
        if (args.length == 2) {
            String firstArg = args[0].toLowerCase();

            // reload 命令的二级补全
            switch (firstArg) {
                case "reload" -> {
                    return Collections.emptyList(); // reload 没有二级参数
                }


                // info 命令的二级补全
                case "info" -> {
                    return Collections.emptyList(); // info 没有二级参数
                }


                // help 命令的二级补全
                case "help" -> {
                    return Collections.emptyList(); // help 没有二级参数
                }
            }
        }
        // 没有更多参数需要补全
        return Collections.emptyList();
    }


    @Override
    public void onDisable() {
        // 安全检查：确保 langConfig 不为 null
        if (langConfig != null) {
            getLogger().info(getLang("logs.disable"));
        } else {
            getLogger().info("KaGeneration插件已禁用");
        }
    }

    // 显示帮助信息
    private void showHelp(CommandSender sender) {
        sender.sendMessage(getLang("commands.help.title"));
        sender.sendMessage(getLang("commands.help.reload"));
        sender.sendMessage(getLang("commands.help.info"));
        sender.sendMessage(getLang("commands.help.help"));

        // 添加权限说明
        if (sender.hasPermission("kageneration.reload")) {
            sender.sendMessage(getLang("commands.help.all_permissions"));
        } else {
            sender.sendMessage(getLang("commands.help.limited_permissions"));
        }
    }

    // 加载所有配置设置
    private void loadConfigSettings() {
        // 加载世界白名单
        worldPatterns = config.getStringList("World");
        compileWorldPatterns();

        if (worldPatterns.isEmpty()) {
            getLogger().info(getLang("logs.worlds.all"));
        } else {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("patterns", String.join(", ", worldPatterns));
            getLogger().info(getLang("logs.worlds.specific", replacements));
        }

        // 加载功能开关
        lavaBucketEnabled = config.getBoolean("Setting.get_lava_bucket", true);
        allowWaterInNether = config.getBoolean("Setting.allow_water_in_nether", true);
        generateWaterFromIce = config.getBoolean("Setting.generate_water_from_ice", true);

        // 加载生成组配置
        loadGenerationGroups();
    }

    // 发送 ActionBar 消息的辅助方法
    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.translateAlternateColorCodes('&', message)));
    }

    // 检查世界是否匹配
    private boolean isWorldEnabled(String worldName) {
        // 如果没有配置任何模式，所有世界都启用
        if (compiledPatterns.isEmpty()) return false;

        // 检查世界名称是否匹配任何模式
        for (Pattern pattern : compiledPatterns) {
            Matcher matcher = pattern.matcher(worldName);
            if (matcher.matches()) {
                return false;
            }
        }

        return true;
    }

    // 编译世界匹配模式
    private void compileWorldPatterns() {
        compiledPatterns.clear();
        for (String patternStr : worldPatterns) {
            try {
                Pattern pattern = Pattern.compile(patternStr);
                compiledPatterns.add(pattern);
            } catch (Exception e) {
                getLogger().warning("无效的正则表达式: " + patternStr + " - " + e.getMessage());
            }
        }
    }

    // 加载生成组配置
    private void loadGenerationGroups() {
        generationGroups.clear();
        groupPriorities.clear();

        ConfigurationSection generationSection = config.getConfigurationSection("Generation");
        if (generationSection == null) {
            getLogger().warning("未找到 Generation 配置部分");
            return;
        }

        for (String groupName : generationSection.getKeys(false)) {
            ConfigurationSection groupSection = generationSection.getConfigurationSection(groupName);
            if (groupSection == null) {
                getLogger().warning("组 " + groupName + " 的配置无效");
                continue;
            }

            // 获取优先级
            int priority = groupSection.getInt("priority", 0);
            groupPriorities.put(groupName, priority);

            // 获取矿石概率
            Map<Material, Integer> oreChances = new HashMap<>();
            for (String oreName : groupSection.getKeys(false)) {
                if (oreName.equals("priority")) continue;

                Material material = Material.getMaterial(oreName.toUpperCase());
                if (material != null) {
                    int chance = groupSection.getInt(oreName, 0);
                    oreChances.put(material, chance);
                } else {
                    getLogger().warning("无效的矿石类型: " + oreName);
                }
            }

            generationGroups.put(groupName, oreChances);
            getLogger().info("加载权限组: " + groupName + " (优先级: " + priority + ")");
        }
    }

    @EventHandler
    public void onRockGeneration(BlockFormEvent event) {
        BlockState newState = event.getNewState();
        Material newType = newState.getType();

        // 只处理石头和原石生成
        if (!SUPPORTED_BLOCKS.contains(newType)) return;

        World world = event.getBlock().getWorld();
        if (isWorldEnabled(world.getName())) {
            // 不在白名单中的世界，按原版处理
            return;
        }        // 查找附近玩家
        Player nearestPlayer = findNearestPlayer(event.getBlock().getLocation());

        // 获取适用的生成组
        String applicableGroup = getApplicableGroup(nearestPlayer);

        // 获取该组的矿石概率
        Map<Material, Integer> oreChances = generationGroups.get(applicableGroup);
        if (oreChances == null || oreChances.isEmpty()) return;

        // 计算总概率
        int totalChance = oreChances.values().stream().mapToInt(Integer::intValue).sum();
        if (totalChance <= 0) return;

        // 随机选择矿石
        int random = new Random().nextInt(totalChance);
        int cumulative = 0;

        for (Map.Entry<Material, Integer> entry : oreChances.entrySet()) {
            cumulative += entry.getValue();
            if (random < cumulative) {
                newState.setType(entry.getKey());
                return;
            }
        }
    }

    // 黑曜石转换功能
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 检查功能是否启用
        if (!lavaBucketEnabled) return;

        // 只处理右键点击方块事件
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        // 检查世界白名单
        World world = null;
        if (clickedBlock != null) {
            world = clickedBlock.getWorld();
        }
        if (world != null && isWorldEnabled(world.getName())) {
            // 不在白名单中的世界，不执行转换
            return;
        }
        // 检查玩家是否手持空桶
        if (itemInHand.getType() != Material.BUCKET) return;

        // 检查点击的方块是否是黑曜石
        if (clickedBlock == null || clickedBlock.getType() != Material.OBSIDIAN) return;


        // 取消事件（防止桶被使用）
        event.setCancelled(true);

        // 移除黑曜石方块
        clickedBlock.setType(Material.AIR);

        // 减少玩家手中的桶数量
        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // 给予玩家岩浆桶
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(Material.LAVA_BUCKET));

        // 如果背包满了，掉落岩浆桶
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(Material.LAVA_BUCKET));
        }

        // 播放音效
        String soundName = getLang("features.lava_bucket.sound");
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            getLogger().warning("无效的音效名称: " + soundName);
        }

        // 发送提示消息
        sendActionBar(player, getLang("features.lava_bucket.success"));
    }

    // 允许在下界放置水桶
    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        // 检查功能是否启用
        if (!allowWaterInNether) return;

        Player player = event.getPlayer();
        Block clickedBlock = event.getBlockClicked();
        Material bucket = event.getBucket();

        // 只处理水桶
        if (bucket != Material.WATER_BUCKET) return;

        // 检查是否在下界
        if (player.getWorld().getEnvironment() != World.Environment.NETHER) return;

        // 取消原版事件（防止水被蒸发）
        event.setCancelled(true);

        // 获取目标位置
        Block targetBlock = clickedBlock.getRelative(event.getBlockFace());

        // 放置水方块
        targetBlock.setType(Material.WATER);

        // 播放放置水的声音
        String soundName = getLang("features.water_in_nether.sound");
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(targetBlock.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            getLogger().warning("无效的音效名称: " + soundName);
        }

        // 更新玩家物品（水桶变空桶）
        if (event.getItemStack() != null && event.getItemStack().getAmount() > 1) {
            event.getItemStack().setAmount(event.getItemStack().getAmount() - 1);
            player.getInventory().addItem(new ItemStack(Material.BUCKET));
        } else if (event.getItemStack() != null) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        }

        // 发送提示消息
        sendActionBar(player, getLang("features.water_in_nether.success"));
    }

    // 添加冰块破坏事件处理
    @EventHandler
    public void onIceBreak(BlockBreakEvent event) {
        // 检查功能是否启用
        if (!generateWaterFromIce) return;

        Block block = event.getBlock();

        // 检查是否是冰块
        if (block.getType() != Material.ICE) return;

        // 检查是否在下界
        if (block.getWorld().getEnvironment() != World.Environment.NETHER) return;


        // 检查玩家是否有精准采集
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE && player.getInventory().getItemInMainHand().getEnchantments().containsKey(Enchantment.SILK_TOUCH)) {
            // 有精准采集时不生成水源
            return;
        }

        // 取消事件（防止冰块被破坏）
        event.setCancelled(true);

        // 将冰块替换为水源
        block.setType(Material.WATER);

        // 播放冰块破碎音效
        String soundName = getLang("features.ice_to_water.sound");
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(block.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            getLogger().warning("无效的音效名称: " + soundName);
        }

        // 发送提示消息
        sendActionBar(player, getLang("features.ice_to_water.success"));
    }

    // 查找最近的玩家（无范围限制）
    private Player findNearestPlayer(org.bukkit.Location location) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Player player : Bukkit.getOnlinePlayers()) {
            // 检查世界是否相同
            if (!player.getWorld().equals(location.getWorld())) continue;

            double distance = player.getLocation().distance(location);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = player;
            }
        }

        return nearest;
    }

    // 获取适用的生成组
    private String getApplicableGroup(Player player) {
        // 如果没有找到玩家，使用默认组
        if (player == null) return "default";

        String highestPriorityGroup = "default";
        int highestPriority = -1;

        // 遍历所有配置的权限组
        for (Map.Entry<String, Integer> entry : groupPriorities.entrySet()) {
            String groupName = entry.getKey();
            int priority = entry.getValue();

            // 检查玩家是否有该组权限
            String permissionNode = "kageneration." + groupName;
            if (player.hasPermission(permissionNode) && priority > highestPriority) {
                highestPriority = priority;
                highestPriorityGroup = groupName;
            }
        }

        return highestPriorityGroup;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        // 如果没有参数，显示帮助信息
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        // 处理重载命令
        if (args[0].equalsIgnoreCase("reload")) {
            // 重载配置
            reloadConfig(sender);
            return true;
        }

        // 处理信息命令
        if (args[0].equalsIgnoreCase("info")) {
            // 显示配置信息
            showConfigInfo(sender);
            return true;
        }

        // 处理帮助命令
        if (args[0].equalsIgnoreCase("help")) {
            // 显示帮助信息
            showHelp(sender);
            return true;
        }

        // 未知命令
        sender.sendMessage(getLang("commands.unknown"));
        return false;
    }

    // 显示配置信息
    private void showConfigInfo(CommandSender sender) {
        sender.sendMessage(getLang("commands.info.title"));

        // 准备变量替换
        Map<String, String> replacements = new HashMap<>();

        // 显示世界模式
        if (worldPatterns.isEmpty()) {
            replacements.put("patterns", getLang("commands.info.all_worlds"));
        } else {
            replacements.put("patterns", String.join(", ", worldPatterns));
        }
        sender.sendMessage(getLang("commands.info.world_patterns", replacements));

        // 显示功能状态
        replacements.put("status", lavaBucketEnabled ? getLang("status.enabled") : getLang("status.disabled"));
        sender.sendMessage(getLang("commands.info.lava_bucket", replacements));

        replacements.put("status", allowWaterInNether ? getLang("status.enabled") : getLang("status.disabled"));
        sender.sendMessage(getLang("commands.info.water_in_nether", replacements));

        replacements.put("status", generateWaterFromIce ? getLang("status.enabled") : getLang("status.disabled"));
        sender.sendMessage(getLang("commands.info.ice_to_water", replacements));

        // 获取当前权限组信息
        String applicableGroup = "default";
        String groupDisplay;

        if (sender instanceof Player player) {
            applicableGroup = getApplicableGroup(player);
            replacements.put("group", applicableGroup);
            groupDisplay = getLang("commands.info.player_group", replacements);
        } else {
            groupDisplay = getLang("commands.info.default_group");
        }

        sender.sendMessage(groupDisplay);

        // 显示当前权限组的矿石概率
        Map<Material, Integer> oreChances = generationGroups.get(applicableGroup);
        if (oreChances == null || oreChances.isEmpty()) {
            sender.sendMessage(getLang("commands.info.no_ore_config"));
            return;
        }

        sender.sendMessage(getLang("commands.info.ore_chances_title"));

        // 计算总概率
        int totalChance = oreChances.values().stream().mapToInt(Integer::intValue).sum();

        // 显示每种矿石的概率
        for (Map.Entry<Material, Integer> entry : oreChances.entrySet()) {
            Material material = entry.getKey();
            int chance = entry.getValue();
            double percentage = (double) chance / totalChance * 100;

            replacements.put("ore", material.toString().toLowerCase());
            replacements.put("chance", String.valueOf(chance));
            replacements.put("percentage", String.format("%.1f", percentage));

            sender.sendMessage(getLang("commands.info.ore_chance", replacements));
        }

        replacements.put("total", String.valueOf(totalChance));
        sender.sendMessage(getLang("commands.info.total_chance", replacements));
    }

    private void reloadConfig(CommandSender sender) {
        // 检查权限
        if (!sender.hasPermission("kageneration.reload")) {
            sender.sendMessage(getLang("commands.reload.no_permission"));
            return;
        }

        try {
            // 重新加载主配置
            reloadConfig();
            config = getConfig();

            // 保存当前语言设置
            String previousLanguage = languageCode;

            // 加载新的语言设置
            loadLanguageSetting();

            // 检查语言是否变更
            if (!previousLanguage.equals(languageCode)) {
                // 重新加载语言文件
                loadLangFile();
                sender.sendMessage(getLang("commands.reload.language_changed", Collections.singletonMap("language", languageCode)));
            }

            // 加载其他配置设置
            loadConfigSettings();

            sender.sendMessage(getLang("commands.reload.success"));

            // 准备变量替换
            Map<String, String> replacements = new HashMap<>();
            replacements.put("patterns", worldPatterns.isEmpty() ? getLang("commands.info.all_worlds") : String.join(", ", worldPatterns));
            sender.sendMessage(getLang("commands.info.world_patterns", replacements));

            replacements.put("status", lavaBucketEnabled ? getLang("status.enabled") : getLang("status.disabled"));
            sender.sendMessage(getLang("commands.info.lava_bucket", replacements));

            replacements.put("status", allowWaterInNether ? getLang("status.enabled") : getLang("status.disabled"));
            sender.sendMessage(getLang("commands.info.water_in_nether", replacements));

            replacements.put("status", generateWaterFromIce ? getLang("status.enabled") : getLang("status.disabled"));
            sender.sendMessage(getLang("commands.info.ice_to_water", replacements));
        } catch (Exception e) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("error", e.getMessage());
            sender.sendMessage(getLang("commands.reload.failure", replacements));

            getLogger().log(Level.SEVERE, getLang("logs.config_reload_failed", replacements));
        }
    }
}