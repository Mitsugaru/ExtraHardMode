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

import java.util.AbstractMap.SimpleEntry;

import me.ryanhamshire.ExtraHardMode.ExtraHardMode;
import me.ryanhamshire.ExtraHardMode.config.RootConfig;
import me.ryanhamshire.ExtraHardMode.config.RootNode;
import me.ryanhamshire.ExtraHardMode.module.DataStoreModule;
import me.ryanhamshire.ExtraHardMode.service.PermissionNode;

import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Task to spawn more monsters.
 */
public class MoreMonstersTask implements Runnable {
   // TODO Fix weird bug
   // TODO if block not valid check random block nearby
   // TODO check for nearby players, test the distance

   /**
    * Plugin instance.
    */
   private ExtraHardMode plugin;

   /**
    * Constructor.
    * 
    * @param plugin
    *           - Plugin instance.
    */
   public MoreMonstersTask(ExtraHardMode plugin) {
      this.plugin = plugin;
   }

   @Override
   public void run() {
      DataStoreModule dataStore = plugin.getModuleForClass(DataStoreModule.class);
      // spawn monsters from the last pass
      for(SimpleEntry<Player, Location> entry : dataStore.getPreviousLocations()) {
         Player player = entry.getKey();
         Location location = entry.getValue();
         Chunk chunk = location.getChunk();
         World world = location.getWorld();

         try {
            // chunk must be loaded, player must not be close, and there must be
            // no other players in the chunk
            if(location.getChunk().isLoaded() && player.isOnline() && location.distanceSquared(player.getLocation()) > 150) {
               boolean playerInChunk = false;
               for(Entity entity : chunk.getEntities()) {
                  if(entity.getType() == EntityType.PLAYER) {
                     playerInChunk = true;
                     break;
                  }
               }

               if(!playerInChunk) {
                  // spawn random monster(s)
                  if(world.getEnvironment() == Environment.NORMAL) {
                     int random = plugin.getRandom().nextInt();
                     EntityType monsterType;
                     int typeMultiplier = 1;

                     // decide which kind and how many
                     // silverfish are most common
                     if(random < 30) {
                        monsterType = EntityType.SILVERFISH;
                        // twice as many if silverfish
                        typeMultiplier = 2;
                     } else if(random < 47) {
                        monsterType = EntityType.SKELETON;
                     } else if(random < 64) {
                        monsterType = EntityType.ZOMBIE;
                     } else if(random < 81) {
                        monsterType = EntityType.CREEPER;
                     } else {
                        monsterType = EntityType.SPIDER;
                     }

                     int totalToSpawn = typeMultiplier;
                     for(int j = 0; j < totalToSpawn; j++) {
                        world.spawnEntity(location, monsterType);
                     }
                  } else if(world.getEnvironment() == Environment.NETHER) {
                     int random = plugin.getRandom().nextInt();

                     if(random < 80) {
                        world.spawnEntity(location, EntityType.PIG_ZOMBIE);
                     } else {
                        world.spawnEntity(location, EntityType.BLAZE);
                     }
                  }
               }
            }
         } catch(IllegalArgumentException ignored) {
         } // in case the player is in a different world from the saved location
      }

      // plan for the next pass
      dataStore.getPreviousLocations().clear();
      for(Player player : plugin.getServer().getOnlinePlayers()) {
         /*
          * TODO uncomment Location testLoc = player.getLocation();
          * testLoc.setX(936);testLoc.setY(4);testLoc.setZ(-1344);
          * test_single_verifyLocation(testLoc,player);
          */
         // test_verifyLocation(player);
         // END TESTS
         Location verifiedLocation = null;
         // only if player hasn't got bypass and is in survival check location
         if(!player.hasPermission(PermissionNode.BYPASS.getNode()) && player.getGameMode() == GameMode.SURVIVAL)
            verifiedLocation = verifyLocation(player.getLocation());
         if(verifiedLocation != null)
            dataStore.getPreviousLocations().add(new SimpleEntry<Player, Location>(player, verifiedLocation));
      }
   }

   // TODO move this into a utility class
   /**
    * Tests if a a given location is elligible to be spawned on
    * 
    * @return a valid Location or null if the location is invalid
    */
   private Location verifyLocation(Location location) {
      Block playerBlock = location.getBlock();
      World world = location.getWorld();
      RootConfig config = plugin.getModuleForClass(RootConfig.class);

      if(plugin.getEnabledWorlds().contains(world)) {
         // Only spawn monsters in normal world. End is crowded with endermen
         // and nether is too extreme anyway, add config later
         int lightLvl = location.getBlock().getLightFromSky();
         if(world.getEnvironment() == Environment.NORMAL && (location.getY() < config.getInt(RootNode.MONSTER_SPAWNS_IN_LIGHT_MAX_Y) && lightLvl < 3)) {
            // the playerBlock should always be air, but if the player stands
            // on a slab he actually is in the slab, checking a few blocks under
            // because player could have jumped etc..
            if(playerBlock.getType().equals(Material.AIR)) {
               for(int i = 0; i <= 3; i++) {
                  playerBlock = location.getBlock().getRelative(BlockFace.DOWN, 1);

                  if(playerBlock.getType().equals(Material.AIR)) {
                     location.subtract(0, 1, 0);
                     playerBlock = location.getBlock();
                     // the playerBlock is now the block where the monster
                     // should spawn on, next up: verify block
                  } else {
                     break;
                  }
               }
            }
            // no spawning on steps, stairs and transparent blocks
            if(playerBlock.getType().name().endsWith("STEP") || playerBlock.getType().name().endsWith("STAIRS")
                  || playerBlock.getType().isTransparent() || !playerBlock.getType().isOccluding() || playerBlock.getType().equals(Material.AIR)) {
               // don't spawn here
               return null;
            }
            // Now test if there is enough space above the block for the monster
            // to spawn, for simplicty we are going to check a 3x3x3 cube above
            // the block. Any monster could potentially spawn in there.
            /*
             * [(j=1|k=-1)] [(j=1|k=0)] [(j=1|k=1)] i=height [(j=0|k=-1)]
             * [(0|0)] [(j=0|k=1)] [(j=-1|k=-1)][(j=-1|k=0)][(j=-1|k=1)]
             */
            boolean isClear = true; // if there is a single block which
                                    // obstructs the spawn we will abort
            Location checkCube = location;
            for(int i = 0; i <= 2; i++) {
               for(int j = -1; j <= 1; j++) {
                  for(int k = -1; k <= 1; k++) {
                     // Location.add() performs the action on the object! We
                     // have to subtract it again
                     // i is height, j/k can be swapped
                     checkCube.add(j, i, k);
                     // if anything obstructs abort. Could possibly add more non
                     // obstructing Materials
                     if(!checkCube.getBlock().getType().equals(Material.AIR) & !checkCube.getBlock().getType().equals(Material.TORCH)) {
                        isClear = false;
                     }
                     checkCube.subtract(j, i, k);
                     if(!isClear)
                        break;
                  }
                  if(!isClear)
                     break;
               }
               if(!isClear)
                  break; // don't check further, one obstructing block is enough
            }
            // Once we get here the block should be eligible, just check if area
            // above is clear
            if(isClear) {
               return location;
            } else {
               return null;
            }
         }

      }
      return null;
   }

   /**
    * This tests the method verifyLocation. It requires a player to be ingame.
    * It will generate 20 random Locations around the player, notify if a
    * verified location has been found and will place a torch for visualisation
    * at the given location.
    * 
    * @param testPlayer
    *           player to test monsters with
    */
   public void test_verifyLocation(Player testPlayer) {
      for(int i = 0; i < 20; i++) {
         // Generate positive and negative randoms
         int offsetX = plugin.getRandom().nextInt(100) - 50;
         int offsetY = plugin.getRandom().nextInt(20) - 10;
         int offsetZ = plugin.getRandom().nextInt(100) - 50;
         Location randomLoc = testPlayer.getLocation().add(offsetX, offsetY, offsetZ);
         test_single_verifyLocation(randomLoc, testPlayer);
      }
   }

   public void test_single_verifyLocation(Location loc, Player testPlayer) {
      Location verifiedRandomLoc = verifyLocation(loc);
      if(verifiedRandomLoc != null) {
         plugin.sendMessage(testPlayer, "Location (" + ((int) verifiedRandomLoc.getX()) + "|" + ((int) verifiedRandomLoc.getY()) + "|"
               + ((int) verifiedRandomLoc.getZ()) + ") verified");
         verifiedRandomLoc.getBlock().setType(Material.DIAMOND_BLOCK);
      }
   }
}
