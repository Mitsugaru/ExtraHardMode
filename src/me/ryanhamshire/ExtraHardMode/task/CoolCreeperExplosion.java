package me.ryanhamshire.ExtraHardMode.task;

import me.ryanhamshire.ExtraHardMode.ExtraHardMode;
import me.ryanhamshire.ExtraHardMode.module.UtilityModule;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

/**
 * Created with IntelliJ IDEA. User: max Date: 2/22/13 Time: 3:59 PM To change
 * this template use File | Settings | File Templates.
 */
public class CoolCreeperExplosion implements Runnable {
   Creeper deadBomber;
   Creeper suicideBomber;
   ExtraHardMode plugin;
   UtilityModule utils;
   BukkitScheduler scheduler;

   final int numOfExplosions = 5;
   final int ticksBetweenExplosions = 4;
   final int ticksBeforeCatapult = 3;
   final int ticksBeforeSuicide = 8;
   int mainDelay = 0;

   public CoolCreeperExplosion(Creeper entity, ExtraHardMode plugin) {
      deadBomber = entity;
      this.plugin = plugin;
      utils = plugin.getModuleForClass(UtilityModule.class);
      scheduler = plugin.getServer().getScheduler();
   }

   /**
    * Contains the mainLogic for creating a cool explosion
    */
   @Override
   public void run() {
      /*
       * Using Runnables and Asyncs seems to be the only way to introduce delays
       * between actions without blocking the main thread
       */

      // For simplicity we let the creeper die and spawn a new one at the same
      // location
      World world = deadBomber.getWorld();
      Location location = deadBomber.getLocation();
      suicideBomber = world.spawn(location, deadBomber.getClass());
      if(suicideBomber instanceof Creeper) {
         if(deadBomber.isPowered())
            suicideBomber.setPowered(true);
      }
      // Everyone loves fireworks
      for(int i = 0; i < numOfExplosions; i++) {
         mainDelay += ticksBetweenExplosions;
         scheduler.runTaskLaterAsynchronously(plugin, new Firework(), mainDelay);
      }
      // Catapult into air and explode midair
      mainDelay += ticksBeforeCatapult;
      scheduler.runTaskLaterAsynchronously(plugin, new AscendToHeaven(), mainDelay);
   }

   private class Firework implements Runnable {
      @Override
      public void run() {
         utils.fireWorkRandomColors(FireworkEffect.Type.CREEPER, deadBomber.getLocation());
      }
   }

   /**
    * Give creeper helium and let hom slowly float upwards, toss a cigarette at
    * him when he's at his highest and watch him go boom
    * 
    */
   private class AscendToHeaven implements Runnable {
      @Override
      public void run() {
         // Catapult Creeper into sky, afterwards explode in midair
         if(suicideBomber != null) {
            int ticksInbetween = 1;
            suicideBomber.setTarget(null);
            for(int i = 0; i < 10; i++) {
               scheduler.runTaskLaterAsynchronously(plugin, new RiseToGlory(), ticksInbetween);
               ticksInbetween += ticksInbetween;
            }
            scheduler.runTaskLaterAsynchronously(plugin, new Suicide(), ticksBeforeSuicide);
         }
      }
   }

   /**
    * Creeper slowly rises to the sky and will be delivered from his sins with a
    * big bang
    */
   private class RiseToGlory implements Runnable {
      @Override
      public void run() {
         if(suicideBomber != null) {
            Vector holyGrail = suicideBomber.getVelocity().setY(0.5);
            suicideBomber.setVelocity(holyGrail);
         }
      }
   }

   /**
    * Creepers prize to pay is death! Kill him with a big bang.
    */
   private class Suicide implements Runnable {
      @Override
      public void run() {
         // equal to tnt
         CreateExplosionTask boomBoom = new CreateExplosionTask(suicideBomber.getLocation(), 4F);
         boomBoom.run();
         if(suicideBomber != null)
            suicideBomber.remove();
      }
   }

}