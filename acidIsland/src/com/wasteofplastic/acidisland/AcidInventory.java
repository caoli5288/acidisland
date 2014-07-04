package com.wasteofplastic.acidisland;

import java.util.ArrayList;
import java.util.Arrays;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * @author ben
 * This listener will check to see if a player has a water bucket and if so change it to acid bucket
 * It also checks for interactions with water bottles
 */
public class AcidInventory implements Listener {
    private final AcidIsland plugin;
    private ArrayList<String> lore = new ArrayList<String>(Arrays.asList(Locale.acidLore.split("\n")));
    
    public AcidInventory(AcidIsland acidIsland) {
	plugin = acidIsland;
    }

    /**
     * This covers items in a chest, etc. inventory, then change the name then
     * @param e
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInventoryOpen(InventoryOpenEvent e) {
	//plugin.getLogger().info("Inventory open event called");
	if (e.getPlayer().getWorld().getName().equalsIgnoreCase(Settings.worldName)) {
	    Inventory inventory = e.getInventory();
	    if (inventory.contains(Material.WATER_BUCKET)) {
		//plugin.getLogger().info("Inventory contains water bucket");
		ItemStack[] inv = inventory.getContents();
		for (ItemStack item : inv) {
		    if (item != null) {
			//plugin.getLogger().info(item.toString());
			if (item.getType() == Material.WATER_BUCKET) {
			    //plugin.getLogger().info("Found it!");
			    ItemMeta meta = item.getItemMeta();
			    meta.setDisplayName(Locale.acidBucket);
			    meta.setLore(lore);
			    item.setItemMeta(meta);
			}
		    }
		}
	    } else if (inventory.contains(Material.POTION)) {
		//plugin.getLogger().info("Inventory contains water bottle");
		ItemStack[] inv = inventory.getContents();
		for (ItemStack item : inv) {
		    if (item != null) {
			//plugin.getLogger().info(item.toString());
			if (item.getType() == Material.POTION && item.getDurability() == 0) {
			    //plugin.getLogger().info("Found it!");
			    ItemMeta meta = item.getItemMeta();
			    meta.setDisplayName(Locale.acidBottle);
			    meta.setLore(lore);
			    item.setItemMeta(meta);
			}
		    }
		}
	    }
	}
    }

    /**
     * If the player filled up the bucket themselves
     * @param e
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onBucketFill(PlayerBucketFillEvent e) {
	// plugin.getLogger().info("Player filled the bucket");
	if (e.getPlayer().getWorld().getName().equalsIgnoreCase(Settings.worldName)) {
	    // plugin.getLogger().info("Correct world");
	    ItemStack item = e.getItemStack();
	    if (item.getType().equals(Material.WATER_BUCKET)) {
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(Locale.acidBucket);
		meta.setLore(lore);
		item.setItemMeta(meta);
	    }
	}
    }

    /**
     * Checks to see if a player is drinking acid
     * @param e
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onWaterBottleDrink(final PlayerItemConsumeEvent e) {
	//plugin.getLogger().info(e.getEventName() + " called for " + e.getItem().getType().toString());	
	if (e.getItem().getType().equals(Material.POTION) && e.getPlayer().getWorld().getName().equalsIgnoreCase(Settings.worldName)) {
	    if (e.getItem().getDurability() == 0) {
		plugin.getLogger().info(e.getPlayer().getName() + " " + Locale.drankAcidAndDied);
		plugin.getServer().broadcastMessage(e.getPlayer().getDisplayName() + " " + Locale.drankAcid);
		final ItemStack item = new ItemStack(Material.GLASS_BOTTLE);
		e.getPlayer().setItemInHand(item);
		e.getPlayer().setHealth(0D);
		e.setCancelled(true);
	    }
	}
    }
    
    
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onWaterBottleFill(final PlayerInteractEvent e) {
	//plugin.getLogger().info(e.getEventName() + " called");	
	try {
	    if ((e.getAction().equals(Action.RIGHT_CLICK_AIR) && e.getMaterial().equals(Material.GLASS_BOTTLE))
		    || (e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.getClickedBlock().getType().equals(Material.CAULDRON))) {
		// They *may* have filled a bottle with water
		// Check inventory for POTIONS in a tick
		plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
		    @Override
		    public void run() {
			//plugin.getLogger().info("Checking inventory");
			PlayerInventory inv = e.getPlayer().getInventory();
			if (inv.contains(Material.POTION)) {
			    //plugin.getLogger().info("POTION in inventory");
			    //They have a POTION of some kind in inventory
			    int i = 0;
			    for (ItemStack item : inv.getContents()) {
				if (item != null) {
				    //plugin.getLogger().info(i + ":" + item.getType().toString());
				    if (item.getType().equals(Material.POTION) && item.getDurability() == 0) {
					//plugin.getLogger().info("Water bottle found!");
					ItemMeta meta = item.getItemMeta();
					meta.setDisplayName(Locale.acidBottle);
					//ArrayList<String> lore = new ArrayList<String>(Arrays.asList("Poison", "Beware!", "Do not drink!"));
					meta.setLore(lore);
					item.setItemMeta(meta);
					inv.setItem(i, item);
				    }
				}
				i++;
			    }
			}
		    }
		});
	    }
	    //plugin.getLogger().info("Action: " + e.getAction().name());
	} catch (Exception c) {
	    plugin.getLogger().info("Exception");
	    c.printStackTrace();
	}
    }
}