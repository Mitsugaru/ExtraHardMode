/*
    ExtraHardMode Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.ExtraHardMode.event;

import me.ryanhamshire.ExtraHardMode.ExtraHardMode;
import me.ryanhamshire.ExtraHardMode.config.RootConfig;
import me.ryanhamshire.ExtraHardMode.config.RootNode;
import me.ryanhamshire.ExtraHardMode.config.messages.MessageConfig;
import me.ryanhamshire.ExtraHardMode.config.messages.MessageNode;
import me.ryanhamshire.ExtraHardMode.module.BlockModule;
import me.ryanhamshire.ExtraHardMode.module.DataStoreModule;
import me.ryanhamshire.ExtraHardMode.module.DataStoreModule.PlayerData;
import me.ryanhamshire.ExtraHardMode.module.UtilityModule;
import me.ryanhamshire.ExtraHardMode.service.PermissionNode;
import me.ryanhamshire.ExtraHardMode.task.EvaporateWaterTask;
import me.ryanhamshire.ExtraHardMode.task.SetPlayerHealthAndFoodTask;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Event handler for player events.
 */
public class PlayerEventHandler implements Listener
{
    /**
     * Plugin instance.
     */
    private ExtraHardMode plugin;
    /**
     * General helpful stuff
     */
    private UtilityModule utils;
    /**
     * Config
     */
    RootConfig rootC;
    /**
     * Constructor.
     *
     * @param plugin - Plugin instance.
     */
    public PlayerEventHandler(ExtraHardMode plugin)
    {
        this.plugin = plugin;
        utils = plugin.getModuleForClass(UtilityModule.class);
        rootC = plugin.getModuleForClass(RootConfig.class);
    }

    /**
     * FEATURE: respawning players start without full health or food
     *
     * @param respawnEvent - Event that occurred.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent respawnEvent)
    {
        Player player = respawnEvent.getPlayer();
        World world = respawnEvent.getPlayer().getWorld();
        if (!rootC.getStringList(RootNode.WORLDS).contains(world.getName()) || player.hasPermission(PermissionNode.BYPASS.getNode()))
        {
            return;
        }
        SetPlayerHealthAndFoodTask task = new SetPlayerHealthAndFoodTask(player, rootC.getInt(RootNode.PLAYER_RESPAWN_HEALTH), rootC.getInt(RootNode.PLAYER_RESPAWN_FOOD_LEVEL));
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, 10L); // half-second
        // delay
        // FEATURE: players can't swim when they're carrying a lot of weight
        PlayerData playerData = plugin.getModuleForClass(DataStoreModule.class).getPlayerData(player.getName());
        playerData.cachedWeightStatus = false;
    }

    /**
     * when a player interacts with the world
     *
     * @param event - Event that occurred.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    void onPlayerInteract(PlayerInteractEvent event)
    {
        Player player = event.getPlayer();
        World world = event.getPlayer().getWorld();
        if (!rootC.getStringList(RootNode.WORLDS).contains(world.getName()) || player.hasPermission(PermissionNode.BYPASS.getNode()))
            return;
        Action action = event.getAction();

        // FEATURE: bonemeal doesn't work on mushrooms
        if (rootC.getBoolean(RootNode.NO_BONEMEAL_ON_MUSHROOMS) && action == Action.RIGHT_CLICK_BLOCK)
        {
            Block block = event.getClickedBlock();
            if (block.getType() == Material.RED_MUSHROOM || block.getType() == Material.BROWN_MUSHROOM)
            {
                // what's the player holding?
                Material materialInHand = player.getItemInHand().getType();

                // if bonemeal, cancel the event
                if (materialInHand == Material.INK_SACK) // bukkit labels bonemeal as ink sack
                {
                    event.setCancelled(true);
                }
            }
        }

        // FEATURE: seed reduction. some plants die even when a player uses
        // bonemeal.
        //TODO FIX!
        if (rootC.getBoolean(RootNode.WEAK_FOOD_CROPS) && action.equals(Action.RIGHT_CLICK_BLOCK))
        {
            Block block = event.getClickedBlock();
            if (utils.isPlant(block.getType()))
            {
                Material materialInHand = player.getItemInHand().getType();
                if (materialInHand == Material.INK_SACK && plugin.getModuleForClass(BlockModule.class).plantDies(block, Byte.MAX_VALUE))
                {
                    event.setCancelled(true);
                    block.setType(Material.LONG_GRASS); // dead shrub
                }
            }
        }

        // FEATURE: putting out fire up close catches the player on fire
        Block block = event.getClickedBlock();
        if (rootC.getBoolean(RootNode.EXTINGUISHING_FIRE_IGNITES_PLAYERS) && block != null && block.getType() != Material.AIR)
        {
            if (block.getRelative(event.getBlockFace()).getType() == Material.FIRE)
            {
                player.setFireTicks(100); // 20L ~ 1 seconds; 100L ~ 5 seconds
            }
        }
    }

    /**
     * when a player fills a bucket...
     *
     * @param event - Event that occurred.
     */
    @EventHandler(priority = EventPriority.LOW)
    void onPlayerFillBucket(PlayerBucketFillEvent event)
    {
        // FEATURE: can't move water source blocks
        if (rootC.getBoolean(RootNode.DONT_MOVE_WATER_SOURCE_BLOCKS))
        {
            Player player = event.getPlayer();
            World world = event.getPlayer().getWorld();
            if (!rootC.getStringList(RootNode.WORLDS).contains(world.getName()) || player.hasPermission(PermissionNode.BYPASS.getNode()))
                return;

            // only care about stationary (source) water
            Block block = event.getBlockClicked();
            if (block.getType() == Material.STATIONARY_WATER)
            {
                // cancel the event so that the water doesn't get removed
                event.setCancelled(true);

                // fill the player's bucket anyway
                // (beware, player may have a stack of empty buckets, and filled
                // buckets DON'T stack)
                int extraBuckets = player.getItemInHand().getAmount() - 1;
                player.getItemInHand().setType(Material.WATER_BUCKET);
                player.getItemInHand().setAmount(1);
                if (extraBuckets > 0)
                {
                    player.getInventory().addItem(new ItemStack(Material.BUCKET, extraBuckets));
                }

                // send the player data so that his client doesn't incorrectly show
                // the water as missing
                player.sendBlockChange(block.getLocation(), block.getTypeId(), block.getData());
            }
        }
    }

    /**
     * when a player empties a bucket...
     *
     * @param event - Event that occurred.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    void onPlayerEmptyBucket(PlayerBucketEmptyEvent event)
    {
        // FEATURE: can't move water source blocks
        if (rootC.getBoolean(RootNode.DONT_MOVE_WATER_SOURCE_BLOCKS) & !event.getPlayer().getGameMode().equals(GameMode.CREATIVE))
        {
            Player player = event.getPlayer();
            World world = event.getPlayer().getWorld();
            if (!rootC.getStringList(RootNode.WORLDS).contains(world.getName()) || player.hasPermission(PermissionNode.BYPASS.getNode()))
            {
                return;
            }
            // only care about water buckets
            if (player.getItemInHand().getType() == Material.WATER_BUCKET)
            {
                // plan to change this block into a non-source block on the next
                // tick
                Block block = event.getBlockClicked().getRelative(event.getBlockFace());
                EvaporateWaterTask task = new EvaporateWaterTask(block);
                plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, 15L);
            }
        }
    }

    /**
     * when a player changes worlds...
     *
     * @param event - Event that occurred.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    void onPlayerChangeWorld(PlayerChangedWorldEvent event)
    {
        World world = event.getFrom();

        if (!rootC.getStringList(RootNode.WORLDS).contains(world.getName()))
            return;

        // FEATURE: respawn the ender dragon when the last player leaves the end
        if (world.getEnvironment() != Environment.THE_END)
            return;

        if (world.getPlayers().size() > 0)
            return;

        // look for an ender dragon
        EnderDragon enderDragon = null;
        for (Entity entity : world.getEntities())
        {
            if (enderDragon != null && entity instanceof EnderDragon)
            {  //If there is already a dragon for whatever reason, remove it
                entity.remove();
            }
            if (entity instanceof EnderDragon)
            {
                enderDragon = (EnderDragon) entity;
            }
            // clean up any summoned minions
            if (entity.getType().equals(EntityType.ZOMBIE) || entity.getType().equals(EntityType.BLAZE))
            {
                entity.remove();
            }
        }

        // if he's there, full health
        if (enderDragon != null)
        {
            enderDragon.setHealth(enderDragon.getMaxHealth());
        }

        // otherwise, spawn one
        else
        {
            world.spawnEntity(new Location(world, 0, world.getMaxHeight() - 1, 0), EntityType.ENDER_DRAGON);
        }
    }

    /**
     * when a player moves...
     *
     * @param event - Event that occurred.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    void onPlayerMove(PlayerMoveEvent event)
    {
        // FEATURE: no swimming while heavy
        if (!rootC.getBoolean(RootNode.NO_SWIMMING_IN_ARMOR))
            return;

        // only care about moving up
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to.getY() <= from.getY())
            return;

        // only when in water
        Block fromBlock = from.getBlock();
        if (!fromBlock.isLiquid())
            return;

        Block toBlock = to.getBlock();
        if (!toBlock.isLiquid())
            return;

        // only when in deep water
        Block underFromBlock = fromBlock.getRelative(BlockFace.DOWN, 2);
        if (!underFromBlock.isLiquid())
            return;

        // only enabled worlds, and players without bypass permission
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!rootC.getStringList(RootNode.WORLDS).contains(world.getName()) || player.hasPermission(PermissionNode.BYPASS.getNode()))
            return;

        PlayerData playerData = plugin.getModuleForClass(DataStoreModule.class).getPlayerData(player.getName());
        MessageConfig messages = plugin.getModuleForClass(MessageConfig.class);
        // if no cached value, calculate
        if (!playerData.cachedWeightStatus)
        {
            // count worn clothing (counts double)
            PlayerInventory inventory = player.getInventory();
            int weight = 0;
            ItemStack[] armor = inventory.getArmorContents();
            for (ItemStack armorPiece : armor)
            {
                if (armorPiece != null && armorPiece.getType() != Material.AIR)
                {
                    weight += 2;
                }
            }

            // count contents
            for (ItemStack itemStack : inventory.getContents())
            {
                if (itemStack != null && itemStack.getType() != Material.AIR)
                {
                    weight++;
                    if (weight > 18)
                    {
                        break;
                    }
                }
            }

            playerData.cachedWeightStatus = weight > 18;
        }

        // if too heavy, not allowed to swim, if in boat can always swim
        if (playerData.cachedWeightStatus == true & !player.getGameMode().equals(GameMode.CREATIVE) & !player.isInsideVehicle())
        {
            event.setCancelled(true);
            plugin.sendMessage(player, messages.getString(MessageNode.NO_SWIMMING_IN_ARMOR));
        }
    }

    /**
     * when a player drops an item
     *
     * @param event - Event that occurred.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerDropItem(PlayerDropItemEvent event)
    {
        // FEATURE: players can't swim when they're carrying a lot of weight
        Player player = event.getPlayer();
        PlayerData playerData = plugin.getModuleForClass(DataStoreModule.class).getPlayerData(player.getName());
        playerData.cachedWeightStatus = false;
    }

    /**
     * when a player picks up an item
     *
     * @param event - Event that occurred.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerPickupItem(PlayerPickupItemEvent event)
    {
        // FEATURE: players can't swim when they're carrying a lot of weight
        Player player = event.getPlayer();
        PlayerData playerData = plugin.getModuleForClass(DataStoreModule.class).getPlayerData(player.getName());
        playerData.cachedWeightStatus = false;
    }

    /**
     * When a player interacts with an inventory.
     *
     * @param event - Event that occurred.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerInventoryClick(InventoryClickEvent event)
    {
        // FEATURE: players can't swim when they're carrying a lot of weight
        HumanEntity humanEntity = event.getWhoClicked();
        if (humanEntity instanceof Player)
        {
            Player player = (Player) humanEntity;
            PlayerData playerData = plugin.getModuleForClass(DataStoreModule.class).getPlayerData(player.getName());
            playerData.cachedWeightStatus = false;
        }
    }
}
