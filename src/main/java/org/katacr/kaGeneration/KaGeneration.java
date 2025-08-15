package org.katacr.kaGeneration;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class KaGeneration extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        // 注册命令
        this.getCommand("kageneration").setExecutor(this);
        this.getCommand("kg").setExecutor(this);

        getLogger().info(ChatColor.GREEN + "空岛矿石生成插件已启用");
    }

    @Override
    public void onDisable() {
        getLogger().info(ChatColor.RED + "插件已禁用");
    }

    @EventHandler
    public void onStoneGeneration(BlockFormEvent event) {
        // 保持原始功能：只替换石头为钻石矿
        if (event.getNewState().getType() == org.bukkit.Material.STONE) {
            event.getNewState().setType(org.bukkit.Material.DIAMOND_ORE);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 只处理重载命令
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            // 重载插件
            reloadPlugin(sender);
            return true;
        }

        return false;
    }

    private void reloadPlugin(CommandSender sender) {
        // 检查权限
        if (!sender.hasPermission("kageneration.reload")) {
            sender.sendMessage(ChatColor.RED + "你没有执行此命令的权限！");
            return;
        }

        try {
            // 重新加载Bukkit插件
            Bukkit.getPluginManager().disablePlugin(this);
            Bukkit.getPluginManager().enablePlugin(this);

            sender.sendMessage(ChatColor.GREEN + "插件已成功重载！");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "重载失败: " + e.getMessage());
            getLogger().severe("插件重载失败: " + e.getMessage());
        }
    }
}