/*
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
package me.ryanhamshire.ExtraHardMode.module;

import me.ryanhamshire.ExtraHardMode.ExtraHardMode;
import me.ryanhamshire.ExtraHardMode.config.RootConfig;
import me.ryanhamshire.ExtraHardMode.config.RootNode;
import me.ryanhamshire.ExtraHardMode.service.EHMModule;
import me.ryanhamshire.ExtraHardMode.task.BlockPhysicsCheckTask;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Module that manages blocks and physics logic.
 */
public class BlockModule extends EHMModule
{

    /**
     * which materials beyond sand and gravel should be subject to gravity
     */
    private final List<Material> fallingBlocks = new ArrayList<Material>();

    private RootConfig rootC;

    /**
     * Constructor.
     *
     * @param plugin - plugin instance.
     */
    public BlockModule(ExtraHardMode plugin)
    {
        super(plugin);
        rootC = plugin.getModuleForClass(RootConfig.class);
    }

    /**
     * Schedule the physics task
     *
     * @param block           - Target block.
     * @param recursionCount  - Number of times to execute.
     * @param skipCenterBlock - Whether to skip the center block or not.
     */
    public void physicsCheck(Block block, int recursionCount, boolean skipCenterBlock)
    {
        int id = plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new BlockPhysicsCheckTask(plugin, block, recursionCount), 5L);
        // check if it was scheduled. If not, notify in console.
        if (id == -1)
        {
            plugin.getLogger().severe("Failed schedule BlockPhysicsCheck task!");
        }
    }

    /**
     * Makes a block subject to gravity
     *
     * @param block - Block to apply physics to.
     */
    public void applyPhysics(Block block)
    {
        // grass and mycel become dirt when they fall
        if ((block.getType() == Material.GRASS || block.getType() == Material.MYCEL) && rootC.getBoolean(RootNode.MORE_FALLING_BLOCKS_TURN_TO_DIRT))
        {
            block.setType(Material.DIRT);
        }

        // create falling block
        FallingBlock fallingBlock = block.getWorld().spawnFallingBlock(block.getLocation(), block.getTypeId(), block.getData());
        fallingBlock.setDropItem(true);

        // remove original block
        block.setType(Material.AIR);
    }

    /**
     * Check if the given plant at the block dies.
     *
     * @param block        - Block to check.
     * @param newDataValue - Data value to replace.
     * @return True if plant died, else false.
     */
    public boolean plantDies(Block block, byte newDataValue)
    {
        World world = block.getWorld();
        if (!rootC.getStringList(RootNode.WORLDS).contains(world.getName()) || !rootC.getBoolean(RootNode.WEAK_FOOD_CROPS))
        {
            return false;
        }

        // not evaluated until the plant is nearly full grown
        if (newDataValue <= (byte) 6)
        {
            return false;
        }

        Material material = block.getType();
        if (material == Material.CROPS || material == Material.CARROT || material == Material.POTATO)
        {
            int deathProbability = rootC.getInt(RootNode.WEAK_FOOD_CROPS_LOSS_RATE);

            // plants in the dark always die
            if (block.getLightFromSky() < 10)
            {
                deathProbability = 100;
            }
            else
            {
                Biome biome = block.getBiome();

                // the desert environment is very rough on crops
                if ((biome == Biome.DESERT || biome == Biome.DESERT_HILLS)
                        && rootC.getBoolean(RootNode.ARID_DESSERTS))
                {
                    deathProbability += 50;
                }

                // unwatered crops are more likely to die
                Block belowBlock = block.getRelative(BlockFace.DOWN);
                byte moistureLevel = 0;
                if (belowBlock.getType() == Material.SOIL)
                {
                    moistureLevel = belowBlock.getData();
                }

                if (moistureLevel == 0)
                {
                    deathProbability += 25;
                }
            }

            if (plugin.random(deathProbability))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the list of falling blocks.
     *
     * @return List of materials of falling blocks.
     */
    public List<Material> getFallingBlocks()
    {
        return fallingBlocks;
    }

    @Override
    public void starting()
    {
        // parse this final list of additional falling blocks
        for (String materialName : rootC.getStringList(RootNode.MORE_FALLING_BLOCKS))
        {
            Material material = Material.getMaterial(materialName);
            if (material == null)
            {
                plugin.getLogger().warning("Additional Falling Blocks Configuration: Material not found: " + materialName + ".");
            }
            else
            {
                this.fallingBlocks.add(material);
            }
        }
    }

    @Override
    public void closing()
    {
        fallingBlocks.clear();
    }
}
