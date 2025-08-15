package org.katacr.kaGeneration;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class KaGeneration extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("空岛矿石插件已启用 - 水和岩浆交汇生成钻石矿石");
    }

    @Override
    public void onDisable() {
        getLogger().info("空岛矿石插件已卸载");
    }

    @EventHandler
    public void onStoneGeneration(BlockFormEvent event) {
        // 只处理水和岩浆交汇形成石头的情况
        if (event.getNewState().getType() == Material.COBBLESTONE) {
            // 将新生成的石头替换为钻石矿石
            event.getNewState().setType(Material.DIAMOND_ORE);
        }
    }
}
