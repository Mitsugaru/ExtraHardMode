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
package me.ryanhamshire.ExtraHardMode.config;

import me.ryanhamshire.ExtraHardMode.ExtraHardMode;
import me.ryanhamshire.ExtraHardMode.module.DataStoreModule;
import me.ryanhamshire.ExtraHardMode.service.ConfigNode;
import me.ryanhamshire.ExtraHardMode.service.ModularConfig;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration handler for the root config.yml file.
 */
public class RootConfig extends ModularConfig
{

    /**
     * @param plugin - plugin instance.
     */
    public RootConfig(ExtraHardMode plugin)
    {
        super(plugin);
    }

    @Override
    public void starting()
    {
        loadDefaults(plugin.getConfig());
        plugin.saveConfig();
        reload();
        rewriteConfig();
    }

    @Override
    public void closing()
    {
        plugin.reloadConfig();
        plugin.saveConfig();
    }

    @Override
    public void save()
    {
        plugin.saveConfig();
    }

    @Override
    public void set(String path, Object value)
    {
        final ConfigurationSection config = plugin.getConfig();
        config.set(path, value);
        plugin.saveConfig();
    }

    @Override
    public void reload()
    {
        plugin.reloadConfig();
        loadSettings(plugin.getConfig());
        boundsCheck();
    }

    @Override
    public void loadSettings(ConfigurationSection config)
    {
        for (final RootNode node : RootNode.values())
        {
            updateOption(node);
        }
    }

    @Override
    public void loadDefaults(ConfigurationSection config)
    {
        for (RootNode node : RootNode.values())
        {
            if (!config.contains(node.getPath()))
            {
                config.set(node.getPath(), node.getDefaultValue());
            }
        }
    }

    public void rewriteConfig()
    {
        FileConfiguration config = new YamlConfiguration();
        RootConfig root = plugin.getModuleForClass(RootConfig.class);
        RootNode[] nodes = RootNode.values();
        for (RootNode node : nodes)
        {
            switch (node.getVarType())
            {
                case BOOLEAN:
                {
                    config.set(node.getPath(), root.getBoolean(node));
                    break;
                }
                case STRING:
                {
                    config.set(node.getPath(), root.getString(node));
                    break;
                }
                case INTEGER:
                {
                    config.set(node.getPath(), root.getInt(node));
                    break;
                }
                case DOUBLE:
                {
                    config.set(node.getPath(), root.getDouble(node));
                    break;
                }
                case LIST:
                {
                    config.set(node.getPath(), root.getStringList(node));
                    break;
                }
                default:
                {
                    break;
                }
            }
        }
        try //dunno how to get the file for the cfg to overwrite...
        {
            config.save(plugin.getDataFolder().getAbsolutePath() + File.separator + "config.yml");
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void boundsCheck()
    {
        // Check worlds
        List<String> list = getStringList(RootNode.WORLDS);
        List <World> worlds = null;
        if (list.isEmpty())
        {
            plugin.getLogger().warning(plugin.getTag() + " No worlds selected, enabling for default worlds!");
            worlds = plugin.getServer().getWorlds();
        }

        //Only verify worlds from the config file when there are actually worlds there, otherwise use default worlds
        if (worlds == null)
        {
            worlds = new ArrayList<World>();
            for (String name : list)
            {
                World world = plugin.getServer().getWorld(name);
                if (world != null)
                {
                    // Not going to notify on missing world as that will occur in the
                    // main plugin execution.
                    worlds.add(world);
                }
            }
        }
        //write back to file, could potentially also move into Rootnode, but we need a reference to the plugin
        list = new ArrayList <String> ();
        for (World world : worlds)
        list.add(world.getName());
        set(RootNode.WORLDS, list);
        updateOption(RootNode.WORLDS);

        // Check y coordinates
        validateYCoordinate(RootNode.STANDARD_TORCH_MIN_Y, worlds);
        validateYCoordinate(RootNode.MORE_MONSTERS_MAX_Y, worlds);
        validateYCoordinate(RootNode.MONSTER_SPAWNS_IN_LIGHT_MAX_Y, worlds);
        // Check percentages
        validatePercentage(RootNode.BROKEN_NETHERRACK_CATCHES_FIRE_PERCENT);
        validatePercentage(RootNode.MORE_MONSTERS_MULTIPLIER);
        validatePercentage(RootNode.ZOMBIES_REANIMATE_PERCENT);
        validatePercentage(RootNode.SKELETONS_KNOCK_BACK_PERCENT);
        validatePercentage(RootNode.SKELETONS_RELEASE_SILVERFISH);
        validatePercentage(RootNode.SKELETONS_DEFLECT_ARROWS);
        validatePercentage(RootNode.BONUS_UNDERGROUND_SPIDER_SPAWN_PERCENT);
        validatePercentage(RootNode.BONUS_WITCH_SPAWN_PERCENT);
        validatePercentage(RootNode.CHARGED_CREEPER_SPAWN_PERCENT);
        validatePercentage(RootNode.CREEPERS_DROP_TNT_ON_DEATH_PERCENT);
        validatePercentage(RootNode.NEAR_BEDROCK_BLAZE_SPAWN_PERCENT);
        validatePercentage(RootNode.BONUS_NETHER_BLAZE_SPAWN_PERCENT);
        validatePercentage(RootNode.FLAME_SLIMES_SPAWN_WITH_NETHER_BLAZE_PERCENT);
        validatePercentage(RootNode.NETHER_BLAZES_SPLIT_ON_DEATH_PERCENT);
        validatePercentage(RootNode.PLAYER_DEATH_ITEM_STACKS_FORFEIT_PERCENT);
        validatePercentage(RootNode.WEAK_FOOD_CROPS_LOSS_RATE);
        //Custom Checking
        validateCustom(RootNode.PLAYER_RESPAWN_HEALTH, 0, 20);
        validateCustom(RootNode.PLAYER_RESPAWN_FOOD_LEVEL, 0, 20);
    }

    /**
     * Validate Y coordinate limit for the given configuration option against the
     * list of enabled worlds.
     *
     * @param node   - Root node to validate.
     * @param worlds - List of worlds to check against.
     */
    private void validateYCoordinate(RootNode node, List<World> worlds)
    {
        int value = getInt(node);
        boolean changed = false;
        if (value < 0)
        {
            plugin.getLogger().warning(plugin.getTag() + " Y coordinate for " + node.getPath() + " cannot be less than 0.");
            set(node, 0);
            changed = true;
        }
        for (World world : worlds)
        {
            if (value > world.getMaxHeight())
            {
                plugin.getLogger().warning(
                        plugin.getTag() + " Y coordinate for " + node.getPath() + " is greater than the max height for world " + world.getName());
                set(node, world.getMaxHeight());
                value = world.getMaxHeight();
                changed = true;
            }
        }
        if (changed)
        {
            updateOption(node);
        }
    }

    /**
     * Validate percentage value for given configuration option.
     *
     * @param node - Root node to validate.
     */
    private void validatePercentage(RootNode node)
    {
        boolean changed = false;
        int value = getInt(node);
        if (value < 0)
        {
            plugin.getLogger().warning(plugin.getTag() + " Percentage for " + node.getPath() + " cannot be less than 0.");
            set(node, 0);
            changed = true;
        }
        else if (value > 100)
        {
            plugin.getLogger().warning(plugin.getTag() + " Percentage for " + node.getPath() + " cannot be greater than 100.");
            set(node, 100);
            changed = true;
        }
        if (changed)
        {
            updateOption(node);
        }
    }

    /**
     * Validates a configOption with custom bounds
     */
    private void validateCustom (RootNode node, int minVal, int maxVal)
    {
        boolean changed = false;
        int value = getInt(node);
        if (value < minVal)
        {
            plugin.getLogger().warning(plugin.getTag() + " Value for " + node.getPath() + "cannot be smaller than 0.");
            set(node, 0);
            changed = true;
        }
        else if (value > maxVal)
        {
            plugin.getLogger().warning(plugin.getTag() + " Value for " + node.getPath() + "cannot be greater than " + maxVal);
            set(node, maxVal);
            changed = true;
        }
        if (changed)
        {
            updateOption(node);
        }
    }
}
