package me.ryanhamshire.ExtraHardMode.module;

import me.ryanhamshire.ExtraHardMode.ExtraHardMode;
import me.ryanhamshire.ExtraHardMode.service.EHMModule;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;

/**
 * Put all the Utility Stuff here that doesn't fit into the other modules
 */
public class UtilityModule extends EHMModule {
   /**
    * Constructor.
    * 
    * @param plugin
    *           - Plugin instance.
    */
   public UtilityModule(ExtraHardMode plugin) {
      super(plugin);
   }

   /**
    * Generates a Firework with random colors and the given Firework Type
    * 
    * @param type
    * @return
    */
   public void fireWorkRandomColors(FireworkEffect.Type type, Location location) {
      Firework firework = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK);
      FireworkMeta fireworkMeta = firework.getFireworkMeta();

      // Generate the colors
      int rdmInt1 = plugin.getRandom().nextInt(255);
      int rdmInt2 = plugin.getRandom().nextInt(255);
      int rdmInt3 = plugin.getRandom().nextInt(255);
      Color mainColor = Color.fromRGB(rdmInt1, rdmInt2, rdmInt3);
      Color fadeColor = Color.fromRGB(rdmInt2, rdmInt3, rdmInt1);
      // mainColor = Color.BLUE;
      // fadeColor = Color.RED;

      FireworkEffect fwEffect = FireworkEffect.builder().withColor(mainColor).withFade(fadeColor).with(type).build();
      fireworkMeta.addEffect(fwEffect);
      fireworkMeta.setPower(1);
      firework.setFireworkMeta(fireworkMeta);
   }

   /**
    * Returns if Material is a plant that should be affected by the farming
    * Rules
    */
   public boolean isPlant(Material material) {
      if(material.equals(Material.CROPS) || material.equals(Material.POTATO) || material.equals(Material.CARROT)
            || material.equals(Material.MELON_STEM) || material.equals(Material.PUMPKIN_STEM))
         return true;
      return false;
   }

   @Override
   public void starting() {
   }

   @Override
   public void closing() {
   }
}
