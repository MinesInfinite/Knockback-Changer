package com.beast.kbchanger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;

public class Kbchanger extends JavaPlugin implements Listener, CommandExecutor {
    private File configFile;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("kb").setExecutor(this);
        getLogger().info("KBChanger has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("KBChanger has been disabled!");
    }

    private void loadConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            saveDefaultConfig();
            getLogger().info("Created new config file!");
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        updateConfigDefaults();
    }

    private void updateConfigDefaults() {
        boolean updated = false;

        // Messages
        if (!config.contains("messages.reload")) {
            config.set("messages.reload", "&aKnockback configuration reloaded!");
            updated = true;
        }

        // Knockback Settings
        if (!config.contains("knockback.w-tap.enabled")) {
            config.set("knockback.w-tap.enabled", true);
            updated = true;
        }
        if (!config.contains("knockback.w-tap.multiplier")) {
            config.set("knockback.w-tap.multiplier", 0.2);
            updated = true;
        }
        if (!config.contains("knockback.cancel-carbon-kb")) {
            config.set("knockback.cancel-carbon-kb", true);
            updated = true;
        }

        // Ground KB
        if (!config.contains("knockback.ground.horizontal")) {
            config.set("knockback.ground.horizontal", 0.4);
            updated = true;
        }
        if (!config.contains("knockback.ground.vertical")) {
            config.set("knockback.ground.vertical", 0.3);
            updated = true;
        }

        // Air KB
        if (!config.contains("knockback.air.horizontal")) {
            config.set("knockback.air.horizontal", 0.5);
            updated = true;
        }
        if (!config.contains("knockback.air.vertical")) {
            config.set("knockback.air.vertical", 0.4);
            updated = true;
        }

        // Limits
        if (!config.contains("knockback.cap-horizontal")) {
            config.set("knockback.cap-horizontal", false);
            updated = true;
        }
        if (!config.contains("knockback.cap-vertical")) {
            config.set("knockback.cap-vertical", false);
            updated = true;
        }
        if (!config.contains("knockback.h-limit")) {
            config.set("knockback.h-limit", 3.0);
            updated = true;
        }
        if (!config.contains("knockback.v-limit")) {
            config.set("knockback.v-limit", 3.0);
            updated = true;
        }

        if (updated) {
            try {
                config.save(configFile);
                getLogger().info("Updated config with new defaults!");
            } catch (IOException e) {
                getLogger().warning("Failed to save config file!");
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        if (config.getBoolean("knockback.cancel-carbon-kb", true)) {
            // Cancel Carbon's knockback while keeping damage
            victim.setVelocity(new Vector(0, 0, 0));

            // Apply our knockback with 1 tick delay
            Bukkit.getScheduler().runTaskLater(this, () -> {
                applyCustomKnockback(victim, attacker);
            }, 1L);
        } else {
            // Just modify the existing knockback
            applyCustomKnockback(victim, attacker);
        }
    }

    private void applyCustomKnockback(Player victim, Player attacker) {
        boolean isAirborne = !victim.isOnGround();
        boolean wTapEnabled = config.getBoolean("knockback.w-tap.enabled", true);
        double wTapMultiplier = config.getDouble("knockback.w-tap.multiplier", 0.2);

        // Get base KB values
        double horizontalKB = isAirborne
                ? config.getDouble("knockback.air.horizontal", 0.5)
                : config.getDouble("knockback.ground.horizontal", 0.4);
        double verticalKB = isAirborne
                ? config.getDouble("knockback.air.vertical", 0.4)
                : config.getDouble("knockback.ground.vertical", 0.3);

        // Calculate direction
        Vector direction = attacker.getLocation().getDirection().setY(0).normalize();
        Vector knockback = new Vector(
                direction.getX() * horizontalKB,
                verticalKB,
                direction.getZ() * horizontalKB
        );

        // Apply W-Tap multiplier if conditions are met
        if (wTapEnabled && attacker.isSprinting() && isAirborne) {
            knockback.multiply(1 + wTapMultiplier);
        }

        // Apply limits if enabled
        if (config.getBoolean("knockback.cap-horizontal", false)) {
            double hLimit = config.getDouble("knockback.h-limit", 3.0);
            knockback.setX(Math.min(Math.abs(knockback.getX()), hLimit) * Math.signum(knockback.getX()));
            knockback.setZ(Math.min(Math.abs(knockback.getZ()), hLimit) * Math.signum(knockback.getZ()));
        }

        if (config.getBoolean("knockback.cap-vertical", false)) {
            double vLimit = config.getDouble("knockback.v-limit", 3.0);
            knockback.setY(Math.min(Math.abs(knockback.getY()), vLimit) * Math.signum(knockback.getY()));
        }

        // Apply the final knockback
        victim.setVelocity(knockback);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("kb")) {
            return false;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("kbchanger.reload")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            loadConfig();
            String message = config.getString("messages.reload", "&aKnockback configuration reloaded!");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return true;
        }

        // Show help if no valid arguments
        sender.sendMessage(ChatColor.GOLD + "KBChanger Commands:");
        sender.sendMessage(ChatColor.YELLOW + "/kb reload - Reload the configuration");
        return true;
    }
}