package org.katacr.kaGeneration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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

import java.util.*;

public class KaGeneration extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private final Map<String, Map<Material, Integer>> generationGroups = new HashMap<>();
    private final Map<String, Integer> groupPriorities = new HashMap<>();

    // 支持替换的方块类型
    private final List<Material> SUPPORTED_BLOCKS = Arrays.asList(Material.STONE, Material.COBBLESTONE);

    // 世界白名单
    private List<String> enabledWorlds = new ArrayList<>();

    // 功能开关
    private boolean lavaBucketEnabled = true;
    private boolean allowWaterInNether = true;
    private boolean generateWaterFromIce = true;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        config = getConfig();

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
            getLogger().info("已挂钩到 PlaceholderAPI");
        }

        getLogger().info(ChatColor.GREEN + "空岛刷矿插件已启用");
    }

    public String getPlayerGroup(Player player) {
        return getApplicableGroup(player);
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

            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command cmd, @NotNull String alias, String[] args) {
        // 只处理 kageneration 和 kg 命令
        if (!cmd.getName().equalsIgnoreCase("kageneration") &&
                !cmd.getName().equalsIgnoreCase("kg")) {
            return Collections.emptyList();
        }

        // 一级命令补全
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0],
                    Arrays.asList("reload", "info", "help"),
                    new ArrayList<>());
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
        getLogger().info(ChatColor.RED + "插件已禁用");
    }

    // 显示帮助信息
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "==== KaGeneration 插件帮助 ====");
        sender.sendMessage(ChatColor.YELLOW + "/kg reload" + ChatColor.WHITE + " - 重载插件配置");
        sender.sendMessage(ChatColor.YELLOW + "/kg info" + ChatColor.WHITE + " - 显示当前配置状态");
        sender.sendMessage(ChatColor.YELLOW + "/kg help" + ChatColor.WHITE + " - 显示此帮助信息");
    }

    // 加载所有配置设置
    private void loadConfigSettings() {
        // 修复：加载世界白名单
        enabledWorlds = config.getStringList("World");
        if (enabledWorlds.isEmpty()) {
            getLogger().info("未配置世界白名单，将在所有世界生效");
        } else {
            getLogger().info("已启用世界白名单: " + String.join(", ", enabledWorlds));
        }

        // 加载功能开关
        lavaBucketEnabled = config.getBoolean("Setting.get_lava_bucket", true);
        allowWaterInNether = config.getBoolean("Setting.allow_water_in_nether", true);
        generateWaterFromIce = config.getBoolean("Setting.generate_water_from_ice", true);

        // 加载生成组配置
        loadGenerationGroups();
    }

    // 加载生成组配置
    private void loadGenerationGroups() {
        generationGroups.clear();
        groupPriorities.clear();

        ConfigurationSection generationSection = config.getConfigurationSection("Generation");
        if (generationSection == null) return;

        for (String groupName : generationSection.getKeys(false)) {
            ConfigurationSection groupSection = generationSection.getConfigurationSection(groupName);
            if (groupSection == null) continue;

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
                }
            }

            generationGroups.put(groupName, oreChances);
        }
    }

    @EventHandler
    public void onRockGeneration(BlockFormEvent event) {
        BlockState newState = event.getNewState();
        Material newType = newState.getType();

        // 只处理石头和原石生成
        if (!SUPPORTED_BLOCKS.contains(newType)) return;

        // 检查世界白名单
        World world = event.getBlock().getWorld();
        if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(world.getName())) {
            // 不在白名单中的世界，按原版处理
            return;
        }

        // 查找附近玩家
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

        // 检查玩家是否手持空桶
        if (itemInHand.getType() != Material.BUCKET) return;

        // 检查点击的方块是否是黑曜石
        if (clickedBlock == null || clickedBlock.getType() != Material.OBSIDIAN) return;

        // 检查世界白名单
        World world = clickedBlock.getWorld();
        if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(world.getName())) {
            // 不在白名单中的世界，不执行转换
            return;
        }

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
        player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1.0f, 1.0f);

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

        // 检查世界白名单
        if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(player.getWorld().getName())) {
            return;
        }

        // 取消原版事件（防止水被蒸发）
        event.setCancelled(true);

        // 获取目标位置
        Block targetBlock = clickedBlock.getRelative(event.getBlockFace());

        // 放置水方块
        targetBlock.setType(Material.WATER);

        // 播放放置水的声音
        player.playSound(targetBlock.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);

        // 更新玩家物品（水桶变空桶）
        if (event.getItemStack() != null && event.getItemStack().getAmount() > 1) {
            event.getItemStack().setAmount(event.getItemStack().getAmount() - 1);
            player.getInventory().addItem(new ItemStack(Material.BUCKET));
        } else if (event.getItemStack() != null) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        }

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

        // 检查世界白名单
        if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(block.getWorld().getName())) {
            return;
        }

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
        player.playSound(block.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);

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

        for (Map.Entry<String, Integer> entry : groupPriorities.entrySet()) {
            String groupName = entry.getKey();
            int priority = entry.getValue();

            // 检查玩家是否有该组权限
            if (player.hasPermission("kageneration." + groupName) && priority > highestPriority) {
                highestPriority = priority;
                highestPriorityGroup = groupName;
            }
        }

        return highestPriorityGroup;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             @NotNull String[] args) {
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
        sender.sendMessage(ChatColor.RED + "未知命令，输入 /kg help 查看帮助");
        return false;
    }

    // 显示配置信息
    private void showConfigInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "==== KaGeneration 插件配置状态 ====");

        // 显示世界白名单
        if (enabledWorlds.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "世界白名单: " + ChatColor.GREEN + "所有世界");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "世界白名单: " + ChatColor.GREEN + String.join(", ", enabledWorlds));
        }

        // 显示功能状态
        sender.sendMessage(ChatColor.YELLOW + "黑曜石转换功能: " +
                (lavaBucketEnabled ? ChatColor.GREEN + "启用" : ChatColor.RED + "禁用"));

        sender.sendMessage(ChatColor.YELLOW + "下界水桶功能: " +
                (allowWaterInNether ? ChatColor.GREEN + "启用" : ChatColor.RED + "禁用"));

        sender.sendMessage(ChatColor.YELLOW + "下界冰块生成水源功能: " +
                (generateWaterFromIce ? ChatColor.GREEN + "启用" : ChatColor.RED + "禁用"));

        // 获取当前权限组信息
        String applicableGroup = "default";
        String groupDisplay = "默认组";

        if (sender instanceof Player player) {
            applicableGroup = getApplicableGroup(player);
            groupDisplay = "您的级别: " + ChatColor.GREEN + applicableGroup;
        }

        sender.sendMessage(ChatColor.YELLOW + groupDisplay);

        // 显示当前权限组的矿石概率
        Map<Material, Integer> oreChances = generationGroups.get(applicableGroup);
        if (oreChances == null || oreChances.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "未找到该组的矿石配置");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "==== 矿石生成概率 ====");

        // 计算总概率
        int totalChance = oreChances.values().stream().mapToInt(Integer::intValue).sum();

        // 显示每种矿石的概率
        for (Map.Entry<Material, Integer> entry : oreChances.entrySet()) {
            Material material = entry.getKey();
            int chance = entry.getValue();
            double percentage = (double) chance / totalChance * 100;

            sender.sendMessage(ChatColor.YELLOW + " - " +
                    material.toString().toLowerCase() + ": " +
                    ChatColor.GREEN + chance + "%" +
                    ChatColor.GRAY + " (" + String.format("%.1f", percentage) + "%)");
        }

        sender.sendMessage(ChatColor.YELLOW + "总概率: " + ChatColor.GREEN + totalChance + "%");
    }

    private void reloadConfig(CommandSender sender) {
        // 检查权限
        if (!sender.hasPermission("kageneration.reload")) {
            sender.sendMessage(ChatColor.RED + "你没有执行此命令的权限！");
            return;
        }

        try {
            // 重新加载配置
            reloadConfig();
            config = getConfig();
            loadConfigSettings();

            sender.sendMessage(ChatColor.GREEN + "配置已成功重载！");
            sender.sendMessage(ChatColor.YELLOW + "已启用世界: " + (enabledWorlds.isEmpty() ? "所有世界" : String.join(", ", enabledWorlds)));
            sender.sendMessage(ChatColor.YELLOW + "黑曜石转换功能: " + (lavaBucketEnabled ? "启用" : "禁用"));
            sender.sendMessage(ChatColor.YELLOW + "下界水桶功能: " + (allowWaterInNether ? "启用" : "禁用"));
            sender.sendMessage(ChatColor.YELLOW + "下界冰块生成水源功能: " + (generateWaterFromIce ? "启用" : "禁用"));
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "重载失败: " + e.getMessage());
            getLogger().severe("配置重载失败: " + e.getMessage());
        }
    }
}