package com.dungeonbuilder.needinfo;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class NeedInfoPlugin extends JavaPlugin {

	@Override
	public void onEnable() {
		PluginManager pm = Bukkit.getPluginManager();
		pm.registerEvents(new NeedEvents(), this);
		getCommand("needinfoplugintest").setExecutor(new TestExecutor());
	}

	@Override
	public void onDisable() {
	}
}
