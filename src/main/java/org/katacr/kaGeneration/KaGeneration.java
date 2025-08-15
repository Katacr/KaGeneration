package org.katacr.kaGeneration;

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

        getLogger().info(ChatColor.GREEN + "空岛矿石生成插件已启用");
    }

    @Override
    public void onDisable() {
        getLogger().info(ChatColor.RED + "插件已禁用");
    }

    // 加载所有配置设置
    private void loadConfigSettings() {
        // 加载世界白名单
        enabledWorlds = config.getStringList("World");
        if (enabledWorlds.isEmpty()) {
            getLogger().info("未配置世界白名单，将在所有世界生效");
        } else {
            getLogger().info("已启用世界白名单: " + String.join(", ", enabledWorlds));
        }

        // 加载功能开关
        lavaBucketEnabled = config.getBoolean("Setting.get_lava_bucket", true);
        allowWaterInNether = config.getBoolean("Setting.allow_water_in_nether", true);

        getLogger().info("黑曜石转换功能: " + (lavaBucketEnabled ? "启用" : "禁用"));
        getLogger().info("下界水桶功能: " + (allowWaterInNether ? "启用" : "禁用"));

        // 加载冰块生成水源功能开关
        generateWaterFromIce = config.getBoolean("Setting.generate_water_from_ice", true);
        getLogger().info("下界冰块生成水源功能: " + (generateWaterFromIce ? "启用" : "禁用"));

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
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 只处理重载命令
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            // 重载配置
            reloadConfig(sender);
            return true;
        }

        return false;
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
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "重载失败: " + e.getMessage());
            getLogger().severe("配置重载失败: " + e.getMessage());
        }
    }
}