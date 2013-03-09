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

package me.ryanhamshire.ExtraHardMode.task;

import me.ryanhamshire.ExtraHardMode.ExtraHardMode;
import me.ryanhamshire.ExtraHardMode.config.RootConfig;
import me.ryanhamshire.ExtraHardMode.config.RootNode;
import me.ryanhamshire.ExtraHardMode.module.DataStoreModule;
import me.ryanhamshire.ExtraHardMode.service.PermissionNode;
import org.bukkit.*;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.AbstractMap.SimpleEntry;

/**
 * Task to spawn more monsters.
 */
public class MoreMonstersTask implements Runnable
{

    //TODO Return to this and make it actually spawn and not just take the old locations
    //TODO if block not valid check random block nearby
    //TODO check for nearby players, test the distance

    /**
     * Plugin instance.
     */
    private ExtraHardMode plugin;
    /**
     * Config instanz
     */
    private RootConfig rootC;
    /**
     * Constructor.
     *
     * @param plugin - Plugin instance.
     */
    public MoreMonstersTask(ExtraHardMode plugin)
    {
        this.plugin = plugin;
        rootC = plugin.getModuleForClass(RootConfig.class);
    }

    @Override
    public void run()
    {
        DataStoreModule dataStore = plugin.getModuleForClass(DataStoreModule.class);
        // spawn monsters from the last pass
        for (SimpleEntry<Player, Location> entry : dataStore.getPreviousLocations())
        {
            Player player = entry.getKey();
            Location location = entry.getValue();
            Chunk chunk = location.getChunk();
            World world = location.getWorld();

            try
            {
                // chunk must be loaded, player must not be close, and there must be
                // no other players in the chunk
                //TODO CHECK DISTANCE
                location = verifyLocation(location);
                if (location != null && location.getChunk().isLoaded() && player.isOnline() && location.distanceSquared(player.getLocation()) > 256)
                {
                    boolean playerInChunk = false;
                    for (Entity entity : chunk.getEntities())
                    {
                        if (entity.getType() == EntityType.PLAYER)
                        {
                            playerInChunk = true;
                            break;
                        }
                    }

                    if (!playerInChunk)
                    {
                        // spawn random monster(s)
                        if (world.getEnvironment() == Environment.NORMAL)
                        {
                            int randomMonster = plugin.getRandom().nextInt(90);
                            EntityType monsterType;
                            int typeMultiplier = 1;

                            // decide which kind and how many
                            // monsters are more or less evenly distributed
                            if (randomMonster < 5)
                            {
                                monsterType = EntityType.SILVERFISH; /*5%*/
                                typeMultiplier = 2;
                            }
                            else if (randomMonster < 25)
                            {
                                monsterType = EntityType.SKELETON;   /*20%*/
                            }
                            else if (randomMonster < 45)
                            {
                                monsterType = EntityType.ZOMBIE;     /*20%*/
                            }
                            else if (randomMonster < 65)
                            {
                                monsterType = EntityType.CREEPER;    /*20%*/
                            }
                            else
                            {
                                monsterType = EntityType.SPIDER;     /*25%*/
                            }

                            int totalToSpawn = typeMultiplier;
                            for (int j = 0; j < totalToSpawn; j++)
                            {
                                world.spawnEntity(location, monsterType);
                            }
                        }
                    }
                }
            } catch (IllegalArgumentException ignored)
            {
            } // in case the player is in a different world from the saved location
        }

        // plan for the next pass
        dataStore.getPreviousLocations().clear();
        for (Player player : plugin.getServer().getOnlinePlayers())
        {
            Location verifiedLocation = null;
            //only if player hasn't got bypass and is in survival check location
            if (!player.hasPermission(PermissionNode.BYPASS.getNode()) && player.getGameMode() == GameMode.SURVIVAL)
                verifiedLocation = verifyLocation(player.getLocation());
            if (verifiedLocation != null)
                dataStore.getPreviousLocations().add(new SimpleEntry<Player, Location>(player, verifiedLocation));
        }
    }

    //TODO move this into a utility class
    /**
     * Tests if a a given location is elligible to be spawned on
     *
     * @return a valid Location or null if the location is invalid
     */
    private Location verifyLocation(Location location)
    {
        Block playerBlock = location.getBlock();
        World world = location.getWorld();

        if (rootC.getStringList(RootNode.WORLDS).contains(world.getName()))
        {
            // Only spawn monsters in normal world. End is crowded with endermen
            // and nether is too extreme anyway, add config later
            int lightLvl = location.getBlock().getLightFromSky();
            if (world.getEnvironment() == Environment.NORMAL
                    && (location.getY() < rootC.getInt(RootNode.MORE_MONSTERS_MAX_Y) && lightLvl < 3))
            {
                // the playerBlock should always be air, but if the player stands
                // on a slab he actually is in the slab, checking a few blocks under because player could have jumped etc..
                if (playerBlock.getType().equals(Material.AIR))
                {
                    for (int i = 0; i <= 3; i++)
                    {
                        playerBlock = location.getBlock().getRelative(BlockFace.DOWN, 1);

                        if (playerBlock.getType().equals(Material.AIR))
                        {
                            location.subtract(0, 1, 0);
                            playerBlock = location.getBlock();
                            // the playerBlock is now the block where the monster
                            // should spawn on, next up: verify block
                        }
                        else
                        {
                            break;
                        }
                    }
                }
                // no spawning on steps, stairs and transparent blocks
                if (playerBlock.getType().name().endsWith("STEP") || playerBlock.getType().name().endsWith("STAIRS")
                        || playerBlock.getType().isTransparent() || !playerBlock.getType().isOccluding() || playerBlock.getType().equals(Material.AIR))
                {
                    // don't spawn here
                    return null;
                }

                if (quickVerify(location))
                    return location;
            }

        }
        return null;
    }

    /**
     * Simple check if there is enough space for a monster to spawn
     * @param loc
     * @return
     */
    private boolean quickVerify(Location loc)
    {
        //quickly check if 2 blocks above this is clear
        Block oneAbove = loc.getBlock();
        Block twoAbove = oneAbove.getRelative(BlockFace.UP, 1);
        return oneAbove.getType().equals(Material.AIR) && twoAbove.getType().equals(Material.AIR);
    }
}