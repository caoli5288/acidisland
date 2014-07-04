package com.wasteofplastic.acidisland;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Furnace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

/**
 * @author ben
 * Main AcidIsland class - provides an island minigame in a sea of acid
 */
public class AcidIsland extends JavaPlugin {
    // This plugin
    private static AcidIsland plugin;
    // The AcidIsland world
    public static World acidWorld = null;
    // Player YAMLs
    public YamlConfiguration playerFile;
    public File playersFolder;
    // Where challenges are stored
    private FileConfiguration challenges = null;
    private File challengeConfigFile = null;
    // Localization Strings
    private FileConfiguration locale = null;
    private File localeFile = null;
    // Where warps are stored
    public YamlConfiguration welcomeWarps;
    // Map of all warps stored as player, warp sign Location
    private HashMap<UUID, Object> warpList = new HashMap<UUID, Object>();
    // Top ten list of players
    private Map<UUID, Integer> topTenList;
    // Players object
    public PlayerCache players;
    /**
     * A database of where the sponges are stored a serialized location and
     * integer
     */
    public ConcurrentHashMap<String, Integer> spongeAreas = new ConcurrentHashMap<String, Integer>();
    // File locations
    String pluginMainDir = getDataFolder().toString();
    String spongeDbLocation = pluginMainDir + "/spongeAreas.dat";

    // Water return flow timers
    public LinkedList<SpongeFlowTimer> flowTimers = new LinkedList<SpongeFlowTimer>();

    // Sponge area size limits
    public int spongeAreaUpLimit;
    public int spongeAreaDownLimit;

    // List of transparent blocks
    public HashSet<Byte> transparentBlocks = new HashSet<Byte>();

    // Worker threads
    public Executor workerThreads = Executors.newCachedThreadPool();

    public boolean debug = false;
    public boolean flag = false;

    // Offline Messages
    private HashMap<UUID, List<String>> messages = new HashMap<UUID, List<String>>();
    private YamlConfiguration messageStore;


    /**
     * @return AcidIsland object instance
     */
    public static AcidIsland getPlugin() {
	return plugin;
    }

    /**
     * Returns the World object for the Acid Island world named in config.yml.
     * If the world does not exist then it is created.
     * 
     * @return islandWorld - Bukkit World object for the AcidIsland world
     */
    public static World getIslandWorld() {
	if (acidWorld == null) {
	    acidWorld = WorldCreator.name(Settings.worldName).type(WorldType.FLAT).environment(World.Environment.NORMAL)
		    .generator(new AcidChunkGenerator()).createWorld();
	    // Make the nether if it does not exist
	    if (plugin.getServer().getWorld(Settings.worldName + "_nether") == null) {
		Bukkit.getLogger().info("Creating AcidIsland's nether...");
		    WorldCreator.name(Settings.worldName + "_nether").type(WorldType.NORMAL).environment(World.Environment.NETHER).createWorld();
	    }
	}
	// Set world settings
	acidWorld.setWaterAnimalSpawnLimit(Settings.waterAnimalSpawnLimit);
	acidWorld.setMonsterSpawnLimit(Settings.monsterSpawnLimit);
	acidWorld.setAnimalSpawnLimit(Settings.animalSpawnLimit);
	return acidWorld;
    }

    /**
     * Called when an island is restarted or reset
     * 
     * @param player - player name String
     */
    public void deletePlayerIsland(final UUID player) {
	// Removes the island
	removeIsland(players.getIslandLocation(player));
	players.removeIsland(player);
    }

    /**
     * Displays the Top Ten list if it exists in chat
     * 
     * @param player
     *            - the requesting player
     * @return - true if successful, false if no Top Ten list exists
     */
    public boolean showTopTen(final Player player) {
	player.sendMessage(ChatColor.GOLD + Locale.topTenheader);
	if (topTenList == null) {
	    player.sendMessage(ChatColor.RED + Locale.topTenerrorNotReady);
	    return false;
	}
	int i = 1;
	for (Map.Entry<UUID, Integer> m : topTenList.entrySet()) {
	    final UUID playerUUID = m.getKey();
	    if (players.inTeam(playerUUID)) {
		final List<UUID> pMembers = players.getMembers(playerUUID);
		String memberList = "";
		for (UUID members : pMembers) {
		    memberList += players.getName(members) + ", ";
		}
		if (memberList.length()>2) {
		    memberList = memberList.substring(0, memberList.length() - 2);
		}
		player.sendMessage(ChatColor.AQUA + "#" + i + ": " + players.getName(playerUUID) + " (" + memberList + ") - " + Locale.levelislandLevel + " "+ m.getValue());
	    } else {
		player.sendMessage(ChatColor.AQUA + "#" + i + ": " + players.getName(playerUUID) + " - " + Locale.levelislandLevel + " " + m.getValue());
	    }
	    if (i++ == 10) {
		break;
	    }
	}
	return true;
    }


    @Override
    public ChunkGenerator getDefaultWorldGenerator(final String worldName, final String id) {
	return new AcidChunkGenerator();
    }

    /**
     * Converts a serialized location to a Location
     * @param s - serialized location in format "world:x:y:z"
     * @return Location
     */
    static public Location getLocationString(final String s) {
	if (s == null || s.trim() == "") {
	    return null;
	}
	final String[] parts = s.split(":");
	if (parts.length == 4) {
	    final World w = Bukkit.getServer().getWorld(parts[0]);
	    final int x = Integer.parseInt(parts[1]);
	    final int y = Integer.parseInt(parts[2]);
	    final int z = Integer.parseInt(parts[3]);
	    return new Location(w, x, y, z);
	}
	return null;
    }

    /**
     * Converts a location to a simple string representation
     * 
     * @param l
     * @return
     */
    static public String getStringLocation(final Location l) {
	if (l == null) {
	    return "";
	}
	return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    /**
     * Determines a safe teleport spot on player's island or the team island
     * they belong to.
     * 
     * @param p
     *            PlayerInfo for active player
     * @return Location of a safe teleport spot
     */
    public Location getSafeHomeLocation(final UUID p) {
	//getLogger().info("DEBUG: getSafeHomeLocation called for " + p.toString());
	// Try home location first
	Location l = players.getHomeLocation(p);
	if (l != null) {
	    if (isSafeLocation(l)) {
		return l;
	    }
	}
	//getLogger().info("DEBUG: Home location either isn't safe, or does not exist so try the island");
	// Home location either isn't safe, or does not exist so try the island
	// location
	if (players.inTeam(p)) {
	    l = players.getTeamIslandLocation(p);
	} else {
	    l = players.getIslandLocation(p);
	}
	if (isSafeLocation(l)) {
	    return l;
	}
	if (l == null) {
	    getLogger().severe("Island location is null!");
	}
	//getLogger().info("DEVUG: If these island locations are not safe, then we need to get creative");
	// If these island locations are not safe, then we need to get creative
	// Try the default location
	l = new Location(l.getWorld(), l.getBlockX() + 0.5, l.getBlockY() + 5, l.getBlockZ() + 2.5, 0F, 30F);
	if (isSafeLocation(l)) {
	    return l;
	}
	// Try higher up - 25 blocks high and then move down
	for (int y = l.getBlockY() + 25; y > 0; y--) {
	    final Location n = new Location(l.getWorld(), l.getBlockX(), y, l.getBlockZ());
	    if (isSafeLocation(n)) {
		return n;
	    }
	}
	// Try all the way up to the sky
	for (int y = l.getBlockY(); y < 255; y++) {
	    final Location n = new Location(l.getWorld(), l.getBlockX(), y, l.getBlockZ());
	    if (isSafeLocation(n)) {
		return n;
	    }
	}
	// Nothing worked
	return null;
    }



    /**
     * Sets the home location based on where the player is now
     * 
     * @param player
     * @return
     */
    public void homeSet(final Player player) {
	if (playerIsOnIsland(player)) {
	    players.setHomeLocation(player.getUniqueId(),player.getLocation());
	    player.sendMessage(ChatColor.GREEN + Locale.setHomehomeSet);
	} else {
	    player.sendMessage(ChatColor.RED + Locale.setHomeerrorNotOnIsland);
	}
    }

    /**
     * This teleports player to their island. If not safe place can be found
     * then the player is sent to spawn via /spawn command
     * 
     * @param player
     * @return
     */
    public boolean homeTeleport(final Player player) {
	Location home = null;
	home = getSafeHomeLocation(player.getUniqueId());

	if (home == null) {
	    if (!player.performCommand("spawn")) {
		player.teleport(player.getWorld().getSpawnLocation());
	    }
	    player.sendMessage(ChatColor.RED + Locale.setHomeerrorNoIsland);
	    return true;
	}

	removeMobs(home);
	player.teleport(home);
	player.sendMessage(ChatColor.GREEN + Locale.islandteleport);
	return true;
    }

    /**
     * Determines if an island is at a location in a 5 x 5 x 5 cube around the
     * location
     * 
     * @param loc
     * @return
     */
    public boolean islandAtLocation(final Location loc) {
	//getLogger().info("DEBUG checking islandAtLocation");
	if (loc == null) {
	    return true;
	}
	// Immediate check
	if (loc.getBlock().getType().equals(Material.BEDROCK)) {
	    return true;
	}
	// Look around
	final int px = loc.getBlockX();
	final int py = loc.getBlockY();
	final int pz = loc.getBlockZ();
	for (int x = -2; x <= 2; x++) {
	    for (int y = -2; y <= 2; y++) {
		for (int z = -2; z <= 2; z++) {
		    final Block b = new Location(loc.getWorld(), px + x, py + y, pz + z).getBlock();
		    // Check if there is a bedrock block there already, if not
		    // then it's free
		    if (b.getType().equals(Material.BEDROCK)) {
			return true;
		    }
		}
	    }
	}
	return false;
    }


    /**
     * Checks if this location is safe for a player to teleport to. Used by
     * warps and boat exits Unsafe is any liquid or air and also if there's no
     * space
     * 
     * @param l
     *            - Location to be checked
     * @return true if safe, otherwise false
     */
    public static boolean isSafeLocation(final Location l) {
	if (l == null) {
	    return false;
	}
	final Block ground = l.getBlock().getRelative(BlockFace.DOWN);
	final Block space1 = l.getBlock();
	final Block space2 = l.getBlock().getRelative(BlockFace.UP);
	if (ground.getType().equals(Material.AIR)) {
	    return false;
	}
	// In Acid Island, any type of liquid is no good
	if (ground.isLiquid() || space1.isLiquid() || space2.isLiquid()) {
	    return false;
	}
	if (ground.getType().equals(Material.CACTUS)) {
	    return false;
	} // Ouch - prickly
	if (ground.getType().equals(Material.BOAT)) {
	    return false;
	} // No, I don't want to end up on the boat again
	// Check that the space is not solid
	// The isSolid function is not fully accurate (yet) so we have to check
	// a few other items
	// isSolid thinks that PLATEs and SIGNS are solid, but they are not
	if (space1.getType().isSolid()) {
	    // Do a few other checks
	    if (!(space1.getType().equals(Material.SIGN_POST)) && !(space1.getType().equals(Material.WALL_SIGN))) {
		return false;
	    }
	}
	if (space2.getType().isSolid()) {
	    // Do a few other checks
	    if (!(space2.getType().equals(Material.SIGN_POST)) && !(space2.getType().equals(Material.WALL_SIGN))) {
		return false;
	    }
	}
	// Safe
	return true;
    }


    /**
     * Saves a YAML file
     * 
     * @param yamlFile
     * @param fileLocation
     */
    public static void saveYamlFile(YamlConfiguration yamlFile, String fileLocation) {
	File dataFolder = plugin.getDataFolder();
	File file = new File(dataFolder, fileLocation);

	try {
	    yamlFile.save(file);
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    /**
     * Loads a YAML file
     * 
     * @param file
     * @return
     */
    public static YamlConfiguration loadYamlFile(String file) {
	File dataFolder = plugin.getDataFolder();
	File yamlFile = new File(dataFolder, file);

	YamlConfiguration config = null;
	if (yamlFile.exists()) {
	    try {
		config = new YamlConfiguration();
		config.load(yamlFile);
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	} else {
	    // Create the missing file
	    config = new YamlConfiguration();
	    getPlugin().getLogger().info("No " + file + " found. Creating it...");
	    try {
		config.save(yamlFile);
	    } catch (Exception e) {
		getPlugin().getLogger().severe("Could not create the " + file + " file!");
	    }
	}
	return config;
    }

    /**
     * Creates the warp list if it does not exist
     */
    public void loadWarpList() {
	getLogger().info("Loading warps...");
	// warpList.clear();
	welcomeWarps = loadYamlFile("warps.yml");
	if (welcomeWarps.getConfigurationSection("warps") == null) {
	    welcomeWarps.createSection("warps"); // This is only used to create
	    // the warp.yml file so forgive
	    // this code
	}
	HashMap<String,Object> temp = (HashMap<String, Object>) welcomeWarps.getConfigurationSection("warps").getValues(true);
	for (String s : temp.keySet()) {
	    warpList.put(UUID.fromString(s), temp.get(s));
	}
    }

    /**
     * Saves the warp lists to file
     */
    public void saveWarpList() {
	getLogger().info("Saving warps...");
	final HashMap<String,Object> warps = new HashMap<String,Object>();
	for (UUID p : warpList.keySet()) {
	    warps.put(p.toString(),warpList.get(p));
	}
	welcomeWarps.set("warps", warps);
	saveYamlFile(welcomeWarps, "warps.yml");
    }

    /**
     * Stores warps in the warp array
     * 
     * @param player
     * @param loc
     */
    public boolean addWarp(UUID player, Location loc) {
	final String locS = getStringLocation(loc);
	// Remove the old warp if it existed
	// All warps are stored as lower case
	if (warpList.containsKey(player)) {
	    warpList.remove(player);
	}
	// Do not allow warps to be in the same location
	if (warpList.containsValue(locS)) {
	    return false;
	}
	warpList.put(player, locS);
	saveWarpList();
	return true;
    }

    /**
     * Removes a warp when the welcome sign is destroyed. Called by
     * WarpSigns.java.
     * 
     * @param uuid
     */
    public void removeWarp(UUID uuid) {
	if (warpList.containsKey(uuid)) {
	    warpList.remove(uuid);
	}
	saveWarpList();
    }

    /**
     * Removes a warp at a location. Called by WarpSigns.java.
     * 
     * @param loc
     */
    public void removeWarp(Location loc) {
	final String locS = getStringLocation(loc);
	getLogger().info("Asked to remove warp at " + locS);
	if (warpList.containsValue(locS)) {
	    // Step through every key (sigh)
	    List<UUID> playerList = new ArrayList<UUID>();
	    for (UUID player : warpList.keySet()) {
		if (locS.equals(warpList.get(player))) {
		    playerList.add(player);
		}
	    }
	    for (UUID rp : playerList) {
		warpList.remove(rp);
		final Player p = getServer().getPlayer(rp);
		if (p != null) {
		    // Inform the player
		    p.sendMessage(ChatColor.RED + Locale.warpssignRemoved);
		}
		getLogger().warning(rp.toString() + "'s welcome sign at " + loc.toString() + " was removed by something.");
	    }
	} else {
	    getLogger().info("Not in the list which is:");
	    for (UUID player : warpList.keySet()) {
		getLogger().info(player.toString() + "," + warpList.get(player));
	    }

	}
	saveWarpList();
    }

    /**
     * Returns true if the location supplied is a warp location
     * 
     * @param loc
     * @return true if this location has a warp sign, false if not
     */
    public boolean checkWarp(Location loc) {
	final String locS = getStringLocation(loc);
	if (warpList.containsValue(locS)) {
	    return true;
	}
	return false;
    }

    /**
     * Lists all the known warps
     * 
     * @return String set of warps
     */
    public Set<UUID> listWarps() {
	//getLogger().info("DEBUG Warp list count = " + warpList.size());
	return warpList.keySet();
    }

    /**
     * Provides the location of the warp for player
     * 
     * @param player
     *            - the warp requested
     * @return Location of warp
     */
    public Location getWarp(UUID player) {
	if (warpList.containsKey(player)) {
	    return getLocationString((String) warpList.get(player));
	} else {
	    return null;
	}
    }

    /**
     * Loads the various settings from the config.yml file into the plugin
     */
    public void loadPluginConfig() {
	try {
	    getConfig();
	} catch (final Exception e) {
	    e.printStackTrace();
	}
	// Get the challenges
	getChallengeConfig();
	// Get the localization strings
	getLocale();
	// Assign settings
	// This might be useful to change in the future
	Settings.maxTeamSize = 4;
	// Settings from config.yml
	Settings.worldName = getConfig().getString("general.worldName");

	Settings.islandDistance = getConfig().getInt("island.distance", 110);
	if (Settings.islandDistance < 50) {
	    Settings.islandDistance = 50;
	    getLogger().info("Setting minimum island distance to 50");
	}
	Settings.acidDamage = getConfig().getDouble("general.aciddamage", 5);
	if (Settings.acidDamage > 100) {
	    Settings.acidDamage = 100;
	} else if (Settings.acidDamage < 0) {
	    Settings.acidDamage = 0;
	}

	Settings.animalSpawnLimit = getConfig().getInt("general.animalspawnlimit", 15);
	if (Settings.animalSpawnLimit > 100) {
	    Settings.animalSpawnLimit = 100;
	} else if (Settings.animalSpawnLimit < -1) {
	    Settings.animalSpawnLimit = -1;
	}

	Settings.monsterSpawnLimit = getConfig().getInt("general.monsterspawnlimit", 70);
	if (Settings.monsterSpawnLimit > 100) {
	    Settings.monsterSpawnLimit = 100;
	} else if (Settings.monsterSpawnLimit < -1) {
	    Settings.monsterSpawnLimit = -1;
	}

	Settings.waterAnimalSpawnLimit = getConfig().getInt("general.wateranimalspawnlimit", 15);
	if (Settings.waterAnimalSpawnLimit > 100) {
	    Settings.waterAnimalSpawnLimit = 100;
	} else if (Settings.waterAnimalSpawnLimit < -1) {
	    Settings.waterAnimalSpawnLimit = -1;
	}

	Settings.abandonedIslandLevel = getConfig().getInt("general.abandonedislandlevel", 10);
	if (Settings.abandonedIslandLevel<0) {
	    Settings.abandonedIslandLevel = 0;
	}

	Settings.island_protectionRange = getConfig().getInt("island.protectionRange", 100);
	if (Settings.island_protectionRange > Settings.islandDistance) {
	    Settings.island_protectionRange = Settings.islandDistance;
	} else if (Settings.island_protectionRange < 0) {
	    Settings.island_protectionRange = 0;
	}
	
	Settings.startingMoney = getConfig().getDouble("general.startingmoney", 0D);

	Settings.resetWait = getConfig().getInt("general.resetwait", 300);
	if (Settings.resetWait < 0) {
	    Settings.resetWait = 0;
	}

	// The island's center is actually 5 below sea level
	Settings.sea_level = getConfig().getInt("general.sealevel", 50) - 5;
	if (Settings.sea_level < 25) {
	    Settings.sea_level = 25;
	}

	// Get chest items
	final String[] chestItemString = getConfig().getString("island.chestItems").split(" ");
	final ItemStack[] tempChest = new ItemStack[chestItemString.length];
	for (int i = 0; i < tempChest.length; i++) {
	    try {
		String[] amountdata = chestItemString[i].split(":");
		if (amountdata[0].equals("POTION")) {
		    //getLogger().info("DEBUG: Potion length " + amountdata.length);
		    if (amountdata.length == 2) {
			final String chestPotionEffect = getConfig().getString("island.chestPotion","");
			if (!chestPotionEffect.isEmpty()) {
			    // Change the water bottle stack to a potion of some kind
			    Potion chestPotion = new Potion(PotionType.valueOf(chestPotionEffect));
			    tempChest[i] = chestPotion.toItemStack(Integer.parseInt(amountdata[1]));
			}
		    }
		    else if (amountdata.length == 3) {
			//getLogger().info("DEBUG: Potion type :" + amountdata[1]);
			Potion chestPotion = new Potion(PotionType.valueOf(amountdata[1]));
			//getLogger().info("Potion in chest is :" + chestPotion.getType().toString() + " x " + amountdata[2]);
			tempChest[i] = chestPotion.toItemStack(Integer.parseInt(amountdata[2]));
		    }
		    else if (amountdata.length == 4) {
			// Extended or splash potions
			if (amountdata[2].equals("EXTENDED")) {
			    Potion chestPotion = new Potion(PotionType.valueOf(amountdata[1])).extend();
			    //getLogger().info("Potion in chest is :" + chestPotion.getType().toString() + " extended duration x " + amountdata[3]);
			    tempChest[i] = chestPotion.toItemStack(Integer.parseInt(amountdata[3]));
			} else if (amountdata[2].equals("SPLASH")) {
			    Potion chestPotion = new Potion(PotionType.valueOf(amountdata[1])).splash();
			    //getLogger().info("Potion in chest is :" + chestPotion.getType().toString() + " splash x " + amountdata[3]);
			    tempChest[i] = chestPotion.toItemStack(Integer.parseInt(amountdata[3]));
			} else if (amountdata[2].equals("EXTENDEDSPLASH")) {
			    Potion chestPotion = new Potion(PotionType.valueOf(amountdata[1])).extend().splash();
			    //getLogger().info("Potion in chest is :" + chestPotion.getType().toString() + " splash, extended duration x " + amountdata[3]);
			    tempChest[i] = chestPotion.toItemStack(Integer.parseInt(amountdata[3]));
			}
		    }
		} else {
		    if (amountdata.length == 2) {
			tempChest[i] = new ItemStack(Material.getMaterial(amountdata[0]), Integer.parseInt(amountdata[1]));
		    } else if (amountdata.length == 3) {
			tempChest[i] = new ItemStack(Material.getMaterial(amountdata[0]), Integer.parseInt(amountdata[2]), Short.parseShort(amountdata[1]));
		    }
		}
	    } catch (java.lang.IllegalArgumentException ex) {
		getLogger().severe("Problem loading chest item from config.yml so skipping it: " + chestItemString[i]);
		getLogger().severe("Error is : " + ex.getMessage());
		getLogger().info("Potential potion types are: ");
		for (PotionType c : PotionType.values())
		    getLogger().info(c.name());
	    }
	    catch (Exception e) {
		getLogger().severe("Problem loading chest item from config.yml so skipping it: " + chestItemString[i]);
		getLogger().info("Potential material types are: ");
		for (Material c : Material.values())
		    getLogger().info(c.name());
		//e.printStackTrace();
	    }
	}
	Settings.chestItems = tempChest;
	Settings.allowPvP = getConfig().getString("island.allowPvP");
	if (!Settings.allowPvP.equalsIgnoreCase("allow")) {
	    Settings.allowPvP = "deny";
	}
	Settings.allowBreakBlocks = getConfig().getBoolean("island.allowbreakblocks", false);
	Settings.allowPlaceBlocks= getConfig().getBoolean("island.allowplaceblocks", false);
	Settings.allowBedUse= getConfig().getBoolean("island.allowbeduse", false);
	Settings.allowBucketUse = getConfig().getBoolean("island.allowbucketuse", false);
	Settings.allowShearing = getConfig().getBoolean("island.allowshearing", false);
	Settings.allowEnderPearls = getConfig().getBoolean("island.allowenderpearls", false);
	Settings.allowDoorUse = getConfig().getBoolean("island.allowdooruse", false);
	Settings.allowLeverButtonUse = getConfig().getBoolean("island.allowleverbuttonuse", false);
	Settings.allowCropTrample = getConfig().getBoolean("island.allowcroptrample", false);
	Settings.allowChestAccess = getConfig().getBoolean("island.allowchestaccess", false);
	Settings.allowFurnaceUse = getConfig().getBoolean("island.allowfurnaceuse", false);
	Settings.allowRedStone = getConfig().getBoolean("island.allowredstone", false);
	Settings.allowMusic = getConfig().getBoolean("island.allowmusic", false);
	Settings.allowCrafting = getConfig().getBoolean("island.allowcrafting", false);
	Settings.allowBrewing = getConfig().getBoolean("island.allowbrewing", false);
	Settings.allowGateUse = getConfig().getBoolean("island.allowgateuse", false);

	Settings.absorbLava = getConfig().getBoolean("sponge.absorbLava", false);
	Settings.absorbFire = getConfig().getBoolean("sponge.absorbFire", false);
	Settings.restoreWater = getConfig().getBoolean("sponge.restoreWater", true);
	Settings.canPlaceWater = getConfig().getBoolean("sponge.canPlaceWater", false);
	Settings.spongeRadius = getConfig().getInt("sponge.spongeRadius", 2);
	Settings.threadedSpongeSave = getConfig().getBoolean("sponge.threadedSpongeSave", true);
	Settings.flowTimeMult = getConfig().getInt("sponge.flowTimeMult", 600);
	Settings.attackFire = getConfig().getBoolean("sponge.attackFire", false);
	Settings.pistonMove = getConfig().getBoolean("sponge.pistonMove", true);
	Settings.spongeSaturation = getConfig().getBoolean("sponge.spongeSaturation", false);
	// SPONGE DEBUG
	debug = getConfig().getBoolean("sponge.debug", false);

	// Challenges
	final Set<String> challengeList = getChallengeConfig().getConfigurationSection("challenges.challengeList").getKeys(false);
	Settings.challengeList = challengeList;
	Settings.challengeLevels = Arrays.asList(getChallengeConfig().getString("challenges.levels").split(" "));
	Settings.waiverAmount = getChallengeConfig().getInt("challenges.waiveramount", 1);
	if (Settings.waiverAmount < 0) {
	    Settings.waiverAmount = 0;
	}
	
	// Control Panel / Mini Shop
	

	// Localization
	Locale.changingObsidiantoLava = locale.getString("changingObsidiantoLava", "Changing obsidian back into lava. Be careful!");
	Locale.acidLore = locale.getString("acidLore","Poison!\nBeware!\nDo not drink!");
	Locale.acidBucket = locale.getString("acidBucket", "Acid Bucket");
	Locale.acidBottle = locale.getString("acidBottle", "Bottle O' Acid");
	Locale.drankAcidAndDied = locale.getString("drankAcidAndDied", "drank acid and died.");
	Locale.drankAcid = locale.getString("drankAcid", "drank acid.");
	Locale.errorUnknownPlayer = locale.getString("error.unknownPlayer","That player is unknown.");
	Locale.errorNoPermission = locale.getString("error.noPermission","You don't have permission to use that command!");
	Locale.errorNoIsland = locale.getString("error.noIsland","You do not have an island!");
	Locale.errorNoIslandOther = locale.getString("error.noIslandOther","That player does not have an island!");
	//"You must be on your island to use this command."
	Locale.errorCommandNotReady = locale.getString("error.commandNotReady","You can't use that command right now.");
	Locale.errorOfflinePlayer = locale.getString("error.offlinePlayer","That player is offline or doesn't exist.");
	Locale.errorUnknownCommand = locale.getString("error.unknownCommand","Unknown command.");
	Locale.errorNoTeam = locale.getString("error.noTeam","That player is not in a team.");
	Locale.islandProtected = locale.getString("islandProtected","Island protected.");
	Locale.lavaTip = locale.getString("lavaTip","Changing obsidian back into lava. Be careful!");
	Locale.warpswelcomeLine = locale.getString("warps.welcomeLine","[WELCOME]");
	Locale.warpswarpTip = locale.getString("warps.warpTip","Create a warp by placing a sign with [WELCOME] at the top.");
	Locale.warpssuccess = locale.getString("warps.success","Welcome sign placed successfully!");
	Locale.warpsremoved = locale.getString("warps.removed","Welcome sign removed!");
	Locale.warpssignRemoved = locale.getString("warps.signRemoved","Your welcome sign was removed!");
	Locale.warpsdeactivate = locale.getString("warps.deactivate","Deactivating old sign!");
	Locale.warpserrorNoRemove = locale.getString("warps.errorNoRemove","You can only remove your own Welcome Sign!");
	Locale.warpserrorNoPerm = locale.getString("warps.errorNoPerm","You do not have permission to place Welcome Signs yet!");
	Locale.warpserrorNoPlace = locale.getString("warps.errorNoPlace","You must be on your island to place a Welcome Sign!");
	Locale.warpserrorDuplicate = locale.getString("warps.errorDuplicate","Sorry! There is a sign already in that location!");
	Locale.warpserrorDoesNotExist = locale.getString("warps.errorDoesNotExist","That warp doesn't exist!");
	Locale.warpserrorNotReadyYet = locale.getString("warps.errorNotReadyYet","That warp is not ready yet. Try again later.");
	Locale.warpserrorNotSafe = locale.getString("warps.errorNotSafe","That warp is not safe right now. Try again later.");
	Locale.warpswarpToPlayersSign = locale.getString("warps.warpToPlayersSign","Warp to <player>'s welcome sign.");
	Locale.warpserrorNoWarpsYet = locale.getString("warps.errorNoWarpsYet","There are no warps available yet!");
	Locale.warpswarpsAvailable = locale.getString("warps.warpsAvailable","The following warps are available");
	Locale.topTenheader = locale.getString("topTen.header","These are the Top 10 islands:");
	Locale.topTenerrorNotReady = locale.getString("topTen.errorNotReady","Top ten list not generated yet!");
	Locale.levelislandLevel = locale.getString("level.islandLevel","Island level");
	Locale.levelerrornotYourIsland = locale.getString("level.errornotYourIsland", "Only the island owner can do that.");
	Locale.setHomehomeSet = locale.getString("sethome.homeSet","Your island home has been set to your current location.");
	Locale.setHomeerrorNotOnIsland = locale.getString("sethome.errorNotOnIsland","You must be within your island boundaries to set home!");
	Locale.setHomeerrorNoIsland = locale.getString("sethome.errorNoIsland","You are not part of an island. Returning you the spawn area!");
	Locale.challengesyouHaveCompleted = locale.getString("challenges.youHaveCompleted", "You have completed the [challenge] challenge!");
	Locale.challengesnameHasCompleted = locale.getString("challenges.nameHasCompleted", "[name] has completed the [challenge] challenge!");
	Locale.challengesyouRepeated = locale.getString("challenges.youRepeated", "You repeated the [challenge] challenge!");
	Locale.challengestoComplete = locale.getString("challenges.toComplete","Complete [challengesToDo] more [thisLevel] challenges to unlock this level!");
	Locale.challengeshelp1 = locale.getString("challenges.help1","Use /c <name> to view information about a challenge.");
	Locale.challengeshelp2 = locale.getString("challenges.help2","Use /c complete <name> to attempt to complete that challenge.");
	Locale.challengescolors = locale.getString("challenges.colors","Challenges will have different colors depending on if they are:");
	Locale.challengesincomplete = locale.getString("challenges.incomplete","Incomplete");
	Locale.challengescompleteNotRepeatable = locale.getString("challenges.completeNotRepeatable","Completed(not repeatable)");
	Locale.challengescompleteRepeatable = locale.getString("challenges.completeRepeatable","Completed(repeatable)");
	Locale.challengesname = locale.getString("challenges.name","Challenge Name");
	Locale.challengeslevel = locale.getString("challenges.level","Level");
	Locale.challengesitemTakeWarning = locale.getString("challenges.itemTakeWarning","All required items are taken when you complete this challenge!");
	Locale.challengesnotRepeatable = locale.getString("challenges.notRepeatable","This Challenge is not repeatable!");
	Locale.challengesfirstTimeRewards = locale.getString("challenges.firstTimeRewards","First time reward(s)");
	Locale.challengesrepeatRewards = locale.getString("challenges.repeatRewards","Repeat reward(s)");
	Locale.challengesexpReward = locale.getString("challenges.expReward","Exp reward");
	Locale.challengesmoneyReward = locale.getString("challenges.moneyReward","Money reward");
	Locale.challengestoCompleteUse = locale.getString("challenges.toCompleteUse","To complete this challenge, use");
	Locale.challengesinvalidChallengeName = locale.getString("challenges.invalidChallengeName","Invalid challenge name! Use /c help for more information");
	Locale.challengesrewards = locale.getString("challenges.rewards","Reward(s)");
	Locale.challengesyouHaveNotUnlocked = locale.getString("challenges.youHaveNotUnlocked","You have not unlocked this challenge yet!");
	Locale.challengesunknownChallenge = locale.getString("challenges.unknownChallenge","Unknown challenge name (check spelling)!");
	Locale.challengeserrorNotEnoughItems = locale.getString("challenges.errorNotEnoughItems","You do not have enough of the required item(s)");
	Locale.challengeserrorNotOnIsland = locale.getString("challenges.errorNotOnIsland","You must be on your island to do that!");
	Locale.challengeserrorNotCloseEnough = locale.getString("challenges.errorNotCloseEnough","You must be standing within 10 blocks of all required items.");
	Locale.challengeserrorItemsNotThere = locale.getString("challenges.errorItemsNotThere","All required items must be close to you on your island!");
	Locale.challengeserrorIslandLevel = locale.getString("challenges.errorIslandLevel","Your island must be level [level] to complete this challenge!");
	Locale.islandteleport = locale.getString("island.teleport","Teleporting you to your island. (/island help for more info)");
	Locale.islandnew = locale.getString("island.new","Creating a new island for you...");
	Locale.islanderrorCouldNotCreateIsland = locale.getString("island.errorCouldNotCreateIsland","Could not create your Island. Please contact a server moderator.");
	Locale.islanderrorYouDoNotHavePermission = locale.getString("island.errorYouDoNotHavePermission", "You do not have permission to use that command!");
	Locale.islandresetOnlyOwner = locale.getString("island.resetOnlyOwner","Only the owner may restart this island. Leave this island in order to start your own (/island leave).");
	Locale.islandresetMustRemovePlayers = locale.getString("island.resetMustRemovePlayers","You must remove all players from your island before you can restart it (/island kick <player>). See a list of players currently part of your island using /island team.");
	Locale.islandresetPleaseWait = locale.getString("island.resetPleaseWait","Please wait, generating new island");
	Locale.islandresetWait = locale.getString("island.resetWait","You have to wait [time] seconds before you can do that again.");
	Locale.islandhelpIsland = locale.getString("island.helpIsland","start an island, or teleport to your island.");
	Locale.islandhelpRestart = locale.getString("island.helpRestart","restart your island and remove the old one.");
	Locale.islandhelpSetHome = locale.getString("island.helpSetHome","set your teleport point for /island.");
	Locale.islandhelpLevel = locale.getString("island.helpLevel","calculate your island level");
	Locale.islandhelpLevelPlayer = locale.getString("island.helpLevelPlayer","see another player's island level.");
	Locale.islandhelpTop = locale.getString("island.helpTop","see the top ranked islands.");
	Locale.islandhelpWarps = locale.getString("island.helpWarps","Lists all available welcome-sign warps.");
	Locale.islandhelpWarp = locale.getString("island.helpWarp","Warp to <player>'s welcome sign.");
	Locale.islandhelpTeam = locale.getString("island.helpTeam","view your team information.");
	Locale.islandhelpInvite = locale.getString("island.helpInvite","invite a player to join your island.");
	Locale.islandhelpLeave = locale.getString("island.helpLeave","leave another player's island.");
	Locale.islandhelpKick = locale.getString("island.helpKick","leave another player's island.");
	Locale.islandhelpAcceptReject= locale.getString("island..helpAcceptReject","remove a player from your island.");
	Locale.islandhelpMakeLeader = locale.getString("island.helpMakeLeader","accept or reject an invitation.");
	Locale.islanderrorLevelNotReady = locale.getString("island.errorLevelNotReady","transfer the island to <player>.");
	Locale.islanderrorInvalidPlayer = locale.getString("island.errorInvalidPlayer","Can't use that command right now! Try again in a few seconds.");
	Locale.islandislandLevelis = locale.getString("island.islandLevelis","That player is invalid or does not have an island!");
	Locale.invitehelp = locale.getString("invite.help","Island level is");
	Locale.inviteyouCanInvite = locale.getString("invite.youCanInvite","Use [/island invite <playername>] to invite a player to your island.");
	Locale.inviteyouCannotInvite = locale.getString("invite.youCannotInvite","You can invite [number] more players.");
	Locale.inviteonlyIslandOwnerCanInvite = locale.getString("invite.onlyIslandOwnerCanInvite","You can't invite any more players.");
	Locale.inviteyouHaveJoinedAnIsland = locale.getString("invite.youHaveJoinedAnIsland","Only the island's owner can invite!");
	Locale.invitehasJoinedYourIsland = locale.getString("invite.hasJoinedYourIsland","You have joined an island! Use /island team to see the other members.");
	Locale.inviteerrorCantJoinIsland = locale.getString("invite.errorCantJoinIsland","[name] has joined your island!");
	Locale.inviteerrorYouMustHaveIslandToInvite = locale.getString("invite.errorYouMustHaveIslandToInvite","You couldn't join the island, maybe it's full.");
	Locale.inviteerrorYouCannotInviteYourself = locale.getString("invite.errorYouCannotInviteYourself","You must have an island in order to invite people to it!");
	Locale.inviteremovingInvite = locale.getString("invite.removingInvite","You can not invite yourself!");
	Locale.inviteinviteSentTo = locale.getString("invite.inviteSentTo","Removing your previous invite.");
	Locale.invitenameHasInvitedYou = locale.getString("invite.nameHasInvitedYou","Invite sent to [name]");
	Locale.invitetoAcceptOrReject = locale.getString("invite.toAcceptOrReject","[name] has invited you to join their island!");
	Locale.invitewarningYouWillLoseIsland = locale.getString("invite.warningYouWillLoseIsland","to accept or reject the invite.");
	Locale.inviteerrorYourIslandIsFull = locale.getString("invite.errorYourIslandIsFull","WARNING: You will lose your current island if you accept!");
	Locale.inviteerrorThatPlayerIsAlreadyInATeam = locale.getString("invite.errorThatPlayerIsAlreadyInATeam","Your island is full, you can't invite anyone else.");
	Locale.rejectyouHaveRejectedInvitation = locale.getString("reject.youHaveRejectedInvitation","That player is already in a team.");
	Locale.rejectnameHasRejectedInvite = locale.getString("reject.nameHasRejectedInvite","You have rejected the invitation to join an island.");
	Locale.rejectyouHaveNotBeenInvited = locale.getString("reject.youHaveNotBeenInvited","[name] has rejected your island invite!");
	Locale.leaveerrorYouAreTheLeader = locale.getString("leave.errorYouAreTheLeader","You had not been invited to join a team.");
	Locale.leaveyouHaveLeftTheIsland = locale.getString("leave.youHaveLeftTheIsland","You are the leader, use /island remove <player> instead.");
	Locale.leavenameHasLeftYourIsland = locale.getString("leave.nameHasLeftYourIsland","You have left the island and returned to the player spawn.");
	Locale.leaveerrorYouCannotLeaveIsland = locale.getString("leave.errorYouCannotLeaveIsland","[name] has left your island!");
	Locale.leaveerrorYouMustBeInWorld = locale.getString("leave.errorYouMustBeInWorld","You can't leave your island if you are the only person. Try using /island restart if you want a new one!");
	Locale.leaveerrorLeadersCannotLeave = locale.getString("leave.errorLeadersCannotLeave","You must be in the AcidIsland world to leave your team!");
	Locale.teamlistingMembers = locale.getString("team.listingMembers","Leaders cannot leave an island. Make someone else the leader fist using /island makeleader <player>");
	Locale.kickerrorPlayerNotInTeam = locale.getString("kick.errorPlayerNotInTeam","Listing your island members");
	Locale.kicknameRemovedYou = locale.getString("kick.nameRemovedYou","That player is not in your team!");
	Locale.kicknameRemoved = locale.getString("kick.nameRemoved","[name] has removed you from their island!");
	Locale.kickerrorNotPartOfTeam = locale.getString("kick.errorNotPartOfTeam","[name] has been removed from the island.");
	Locale.kickerrorOnlyLeaderCan = locale.getString("kick.errorOnlyLeaderCan","That player is not part of your island team!");
	Locale.kickerrorNoTeam = locale.getString("kick.errorNoTeam","Only the island's owner may remove people from the island!");
	Locale.makeLeadererrorPlayerMustBeOnline = locale.getString("makeleader.errorPlayerMustBeOnline","No one else is on your island, are you seeing things?");
	Locale.makeLeadererrorYouMustBeInTeam = locale.getString("makeleader.errorYouMustBeInTeam","That player must be online to transfer the island.");
	Locale.makeLeadererrorRemoveAllPlayersFirst = locale.getString("makeleader.errorRemoveAllPlayersFirst","You must be in a team to transfer your island.");
	Locale.makeLeaderyouAreNowTheOwner = locale.getString("makeleader.youAreNowTheOwner","Remove all players from your team other than the player you are transferring to.");
	Locale.makeLeadernameIsNowTheOwner = locale.getString("makeleader.nameIsNowTheOwner","You are now the owner of your island.");
	Locale.makeLeadererrorThatPlayerIsNotInTeam = locale.getString("makeleader.errorThatPlayerIsNotInTeam","[name] is now the owner of your island!");
	Locale.makeLeadererrorNotYourIsland = locale.getString("makeleader.errorNotYourIsland","That player is not part of your island team!");
	Locale.makeLeadererrorGeneralError = locale.getString("makeleader.errorGeneralError","This isn't your island, so you can't give it away!");
	Locale.adminHelpreload = locale.getString("adminHelp.reload","Could not change leaders.");
	Locale.adminHelptopTen = locale.getString("adminHelp.topTen","reload configuration from file.");
	Locale.adminHelpregister = locale.getString("adminHelp.register","manually update the top 10 list");
	Locale.adminHelpdelete = locale.getString("adminHelp.delete","set a player's island to your location");
	Locale.adminHelpcompleteChallenge = locale.getString("adminHelp.completeChallenge","delete an island (removes blocks).");
	Locale.adminHelpresetChallenge = locale.getString("adminHelp.resetChallenge","marks a challenge as complete");
	Locale.adminHelpresetAllChallenges = locale.getString("adminHelp.resetAllChallenges","marks a challenge as incomplete");
	Locale.adminHelppurge = locale.getString("adminHelp.purge","resets all of the player's challenges");
	Locale.adminHelpinfo = locale.getString("adminHelp.info","delete inactive islands older than [TimeInDays].");
	Locale.reloadconfigReloaded = locale.getString("reload.configReloaded","check the team information for the given player.");
	Locale.adminTopTengenerating = locale.getString("adminTopTen.generating","Configuration reloaded from file.");
	Locale.adminTopTenfinished = locale.getString("adminTopTen.finished","Generating the Top Ten list");
	Locale.purgealreadyRunning = locale.getString("purge.alreadyRunning","Finished generation of the Top Ten list");
	Locale.purgeusage = locale.getString("purge.usage","Purge is already running, please wait for it to finish!");
	Locale.purgecalculating = locale.getString("purge.calculating","Calculating which islands have been inactive for more than [time] days.");
	Locale.purgenoneFound = locale.getString("purge.noneFound","No inactive islands to remove.");
	Locale.purgethisWillRemove = locale.getString("purge.thisWillRemove","This will remove [number] inactive islands!");
	Locale.purgewarning = locale.getString("purge.warning","DANGER! Do not run this with players on the server! MAKE BACKUP OF WORLD!");
	Locale.purgetypeConfirm = locale.getString("purge.typeConfirm","Type /acid confirm to proceed within 10 seconds");
	Locale.purgepurgeCancelled = locale.getString("purge.purgeCancelled","Purge cancelled.");
	Locale.purgefinished = locale.getString("purge.finished","Finished purging of inactive islands.");
	Locale.purgeremovingName = locale.getString("purge.removingName","Purge: Removing [name]'s island");
	Locale.confirmerrorTimeLimitExpired = locale.getString("confirm.errorTimeLimitExpired","Time limit expired! Issue command again.");
	Locale.deleteremoving = locale.getString("delete.removing","Removing [name]'s island.");
	Locale.registersettingIsland = locale.getString("register.settingIsland","Set [name]'s island to the bedrock nearest you.");
	Locale.registererrorBedrockNotFound = locale.getString("register.errorBedrockNotFound","Error: unable to set the island!");
	Locale.adminInfoislandLocation = locale.getString("adminInfo.islandLocation","Island Location");
	Locale.adminInfoerrorNotPartOfTeam = locale.getString("adminInfo.errorNotPartOfTeam","That player is not a member of an island team.");
	Locale.adminInfoerrorNullTeamLeader = locale.getString("adminInfo.errorNullTeamLeader","Team leader should be null!");
	Locale.adminInfoerrorTeamMembersExist = locale.getString("adminInfo.errorTeamMembersExist","Player has team members, but shouldn't!");
	Locale.resetChallengessuccess = locale.getString("resetallchallenges.success","[name] has had all challenges reset.");
	Locale.checkTeamcheckingTeam = locale.getString("checkTeam.checkingTeam","Checking Team of [name]");
	Locale.completeChallengeerrorChallengeDoesNotExist = locale.getString("completechallenge.errorChallengeDoesNotExist","Challenge doesn't exist or is already completed");
	Locale.completeChallengechallangeCompleted = locale.getString("completechallenge.challangeCompleted","[challengename] has been completed for [name]");
	Locale.resetChallengeerrorChallengeDoesNotExist = locale.getString("resetchallenge.errorChallengeDoesNotExist","Challenge doesn't exist or isn't yet completed");
	Locale.resetChallengechallengeReset = locale.getString("resetchallenge.challengeReset","[challengename] has been reset for [name]");
	Locale.newsHeadline = locale.getString("news.headline","[AcidIsland News] While you were offline...");
    
    
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
     */
    @Override
    public void onDisable() {
	try {
	    // Remove players from memory
	    players.removeAllPlayers();
	    saveConfig();
	    saveWarpList();
	    saveMessages();
	} catch (final Exception e) {
	    plugin.getLogger().severe("Something went wrong saving files!");
	    e.printStackTrace();
	}
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {
	// instance of this plugin
	plugin = this;
	saveDefaultConfig();
	saveDefaultChallengeConfig();
	saveDefaultLocale();
	// Metrics
	try {
	    final Metrics metrics = new Metrics(this);
	    metrics.start();
	} catch (final IOException localIOException) {
	}
	if (!VaultHelper.setupEconomy()) {
	    getLogger().severe("Could not set up economy!");
	}
	loadPluginConfig();
	getIslandWorld();
	// SPONGE
	spongeAreas = loadSpongeData();
	// Set the limits
	spongeAreaUpLimit = Settings.spongeRadius + 1;
	spongeAreaDownLimit = Settings.spongeRadius * -1;

	// Set and make the player's directory if it does not exist and then load players into memory
	playersFolder = new File(getDataFolder() + File.separator + "players");
	if (!playersFolder.exists()) {
	    playersFolder.mkdir();
	}
	players = new PlayerCache(this);
	// Set up commands for this plugin
	getCommand("island").setExecutor(new IslandCmd(this,players));
	getCommand("challenges").setExecutor(new Challenges(this,players));
	getCommand("acid").setExecutor(new AdminCmd(this,players));
	// Register events that this plugin uses
	registerEvents();
	// Load warps
	loadWarpList();
	// Load messages
	loadMessages();

	// Kick off a few tasks on the next tick
	// By calling getIslandWorld(), if there is no island
	// world, it will be created
	getServer().getScheduler().runTask(getPlugin(), new Runnable() {
	    @Override
	    public void run() {
		final PluginManager manager = Bukkit.getServer().getPluginManager();
		if (manager.isPluginEnabled("Vault")) {
		    AcidIsland.getPlugin().getLogger().info("Trying to use Vault for permissions...");
		    if (!VaultHelper.setupPermissions()) {
			getLogger().severe("Cannot link with Vault for permissions! Disabling plugin!");
			manager.disablePlugin(AcidIsland.getPlugin());
		    } else {
			getLogger().info("Success!");
		    };
		    // update the list
		    updateTopTen();
		}
		if (manager.isPluginEnabled("Multiverse-Core")) {
		    getLogger().info("Trying to register generator with Multiverse ");
		    try {
			getServer().dispatchCommand(getServer().getConsoleSender(), "mv modify set generator AcidIsland " + Settings.worldName);
		    } catch (Exception e) {
			getLogger().info("Not successfull");
			e.printStackTrace();
		    }
		}
	    }
	});

	// This part will kill monsters if they fall into the water because it
	// is acid
	getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
	    @Override
	    public void run() {
		List<Entity> entList = acidWorld.getEntities();
		for (Entity current : entList) {
		    if (current instanceof Monster) {
			if ((current.getLocation().getBlock().getType() == Material.WATER)
				|| (current.getLocation().getBlock().getType() == Material.STATIONARY_WATER)) {
			    ((Monster) current).damage(10d);
			}
		    }
		}
	    }
	}, 0L, 20L);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, Integer> loadSpongeData() {
	if (!(new File(spongeDbLocation)).exists()) {
	    // Create the directory and database files!
	    boolean success = (new File(pluginMainDir)).mkdir();
	    if (success) {
		getLogger().info("Sponge DB created!");
	    }
	    saveSpongeData(false);
	}

	ObjectInputStream ois = null;
	try {
	    ois = new ObjectInputStream(new FileInputStream(spongeDbLocation));
	    Object result = ois.readObject();
	    if (result instanceof ConcurrentHashMap) {
		return (ConcurrentHashMap<String, Integer>) result;
	    } else if (result instanceof HashMap) {
		getLogger().info("Updated sponge database to ConcurrentHashMap.");
		return new ConcurrentHashMap<String, Integer>((Map<String, Integer>) result);
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	} finally {
	    if (ois != null) {
		try {
		    ois.close();
		} catch (IOException e) {
		    e.printStackTrace();
		}
	    }
	}
	return spongeAreas;
    }

    public void saveSpongeData(boolean threaded) {
	SpongeSaveTask spongeSaver = new SpongeSaveTask(this);
	if (threaded) {
	    workerThreads.execute(spongeSaver);
	} else {
	    spongeSaver.run();
	}
    }


    /**
     * Checks if an online player is on their island or on a team island
     * 
     * @param player
     *            - the player who is being checked
     * @return - true if they are on their island, otherwise false
     */
    public boolean playerIsOnIsland(final Player player) {
	Location islandTestLocation = null;
	if (players.hasIsland(player.getUniqueId())) {
	    islandTestLocation = players.getIslandLocation(player.getUniqueId());
	} else if (players.inTeam(player.getUniqueId())) {
	    islandTestLocation = players.get(player.getUniqueId()).getTeamIslandLocation();
	}
	if (islandTestLocation == null) {
	    return false;
	}
	if (player.getLocation().getX() > islandTestLocation.getX() - Settings.island_protectionRange / 2
		&& player.getLocation().getX() < islandTestLocation.getX() + Settings.island_protectionRange / 2
		&& player.getLocation().getZ() > islandTestLocation.getZ() - Settings.island_protectionRange / 2
		&& player.getLocation().getZ() < islandTestLocation.getZ() + Settings.island_protectionRange / 2) {
	    return true;
	}
	return false;
    }

    /**
     * Registers events
     */
    public void registerEvents() {
	final PluginManager manager = getServer().getPluginManager();
	// Nether portal events
	manager.registerEvents(new NetherPortals(this), this);
	// Island Protection events
	manager.registerEvents(new IslandGuard(this), this);
	// Events for when a player joins or leaves the server
	manager.registerEvents(new JoinLeaveEvents(this, players), this);
	// Ensures Lava flows correctly in AcidIsland world
	manager.registerEvents(new LavaCheck(this), this);
	// Ensures that water is acid
	manager.registerEvents(new AcidEffect(this), this);
	// Ensures that boats are safe in AcidIsland
	manager.registerEvents(new SafeBoat(this), this);
	// Enables warp signs in AcidIsland
	manager.registerEvents(new WarpSigns(this), this);
	// Control panel
	manager.registerEvents(new ControlPanel(), this);
	// Handle sponges
	manager.registerEvents(new SpongeBaseListener(this), this);
	if (Settings.spongeSaturation) {
	    manager.registerEvents(new SpongeSaturatedSpongeListener(this), this);
	} else {
	    manager.registerEvents(new SpongeSuperSpongeListener(this), this);
	}
	// Change names of inventory items
	manager.registerEvents(new AcidInventory(this), this);
    }

    /**
     * Removes monsters around location l if removeCreaturesByTeleport = true in
     * config.yml
     * 
     * @param l
     */
    public void removeMobs(final Location l) {
	final int px = l.getBlockX();
	final int py = l.getBlockY();
	final int pz = l.getBlockZ();
	for (int x = -1; x <= 1; x++) {
	    for (int z = -1; z <= 1; z++) {
		final Chunk c = l.getWorld().getChunkAt(new Location(l.getWorld(), px + x * 16, py, pz + z * 16));
		for (final Entity e : c.getEntities()) {
		    if (e instanceof Monster) {
			e.remove();
		    }
		}
	    }
	}
    }

    /**
     * This removes the island at the location given. Removes any entities in
     * that area too
     * 
     * @param loc
     *            - a Location
     */
    public void removeIsland(final Location loc) {
	//getLogger().info("DEBUG: removeIsland");
	if (loc != null) {
	    final Location l = loc;
	    final int px = l.getBlockX();
	    final int pz = l.getBlockZ();
	    // Place a temporary entity
	    //final World world = getIslandWorld();
	    Entity snowBall = loc.getWorld().spawnEntity(loc, EntityType.SNOWBALL);
	    // Remove any mobs if they just so happen to be around in the
	    // vicinity
	    final Iterator<Entity> ents = snowBall.getNearbyEntities((Settings.island_protectionRange / 2.0), 110.0D, (Settings.island_protectionRange / 2.0))
		    .iterator();
	    while (ents.hasNext()) {
		final Entity tempent = ents.next();
		// Remove anything except for a player
		if (!(tempent instanceof Player)) {
		    getLogger().info("Removed entity type " + tempent.getType().toString() + " when removing island at location " + loc.toString());
		    tempent.remove();
		}
	    }

	    for (int x = Settings.island_protectionRange / 2 * -1; x <= Settings.island_protectionRange / 2; x++) {
		for (int y = 0; y <= 255; y++) {
		    for (int z = Settings.island_protectionRange / 2 * -1; z <= Settings.island_protectionRange / 2; z++) {
			final Block b = new Location(l.getWorld(), px + x, y, pz + z).getBlock();
			final Material bt = new Location(l.getWorld(), px + x, y, pz + z).getBlock().getType();
			// Grab anything out of containers (do that it is
			// destroyed)
			if (bt.equals(Material.CHEST)) {
			    final Chest c = (Chest) b.getState();
			    final ItemStack[] items = new ItemStack[c.getInventory().getContents().length];
			    c.getInventory().setContents(items);
			} else if (bt.equals(Material.FURNACE)) {
			    final Furnace f = (Furnace) b.getState();
			    final ItemStack[] items = new ItemStack[f.getInventory().getContents().length];
			    f.getInventory().setContents(items);
			} else if (bt.equals(Material.DISPENSER)) {
			    final Dispenser d = (Dispenser) b.getState();
			    final ItemStack[] items = new ItemStack[d.getInventory().getContents().length];
			    d.getInventory().setContents(items);
			}
			// Split depending on below or above water line
			if (!bt.equals(Material.AIR) && !bt.equals(Material.STATIONARY_WATER)) {
			    if (y < Settings.sea_level + 5) {
				b.setType(Material.STATIONARY_WATER);
			    } else {
				b.setType(Material.AIR);
			    }
			}
		    }
		}
	    }
	}
    }

    /**
     * Transfers ownership of an island from one player to another
     * 
     * @param playerOne
     * @param playerTwo
     * @return
     */
    public boolean transferIsland(final UUID playerOne, final UUID playerTwo) {
	if (!plugin.getServer().getPlayer(playerOne).isOnline() || !plugin.getServer().getPlayer(playerTwo).isOnline()) {
	    return false;
	}
	if (players.hasIsland(playerOne)) {
	    players.setHasIsland(playerTwo, true);
	    players.setIslandLocation(playerTwo, players.getIslandLocation(playerOne));
	    players.setIslandLevel(playerTwo, players.getIslandLevel(playerOne));
	    players.setTeamIslandLocation(playerTwo, null);
	    players.setHasIsland(playerOne, false);
	    players.setIslandLocation(playerOne, null);
	    players.setIslandLevel(playerOne, 0);
	    players.setTeamIslandLocation(playerOne, players.get(playerTwo).getIslandLocation());
	    return true;
	}
	return false;
    }


    /**
     * Generates a sorted map of islands for the Top Ten list
     */
    public void updateTopTen() {
	Map<UUID, Integer> top = new HashMap<UUID, Integer>();
	for (final File f : playersFolder.listFiles()) {
	    // Need to remove the .yml suffix
	    String fileName = f.getName();
	    if (fileName.endsWith(".yml")) {
		try {
		    final UUID playerUUID = UUID.fromString(fileName.substring(0, fileName.length() - 4));
		    if (playerUUID == null) {
			getLogger().warning("Player file contains erroneous UUID data.");
			getLogger().info("Looking at " + fileName.substring(0, fileName.length() - 4));
		    }
		    Players player = new Players(this, playerUUID);    
		    if (player.getIslandLevel() > 0) {
			if (!player.inTeam()) {
			    top.put(player.getPlayerUUID(), player.getIslandLevel());
			} else if (player.getTeamLeader() != null) {
			    if (player.getTeamLeader().equals(player.getPlayerUUID())) {
				top.put(player.getPlayerUUID(), player.getIslandLevel());
			    }
			}
		    }
		} catch (Exception e) {
		    e.printStackTrace();
		}
	    }
	}
	// Now sort the list
	top = MapUtil.sortByValue(top);
	topTenList = top;
    }

    // SPONGE FUNCTIONS
    // Non-Static Functions
    public void enableSponge(Block spongeBlock) {
	// Check for water or Lava
	for (int x = spongeAreaDownLimit; x < spongeAreaUpLimit; x++) {
	    for (int y = spongeAreaDownLimit; y < spongeAreaUpLimit; y++) {
		for (int z = spongeAreaDownLimit; z < spongeAreaUpLimit; z++) {
		    if (debug) {
			getLogger().info("DEBUG Checking: " + x + ", " + y + ", " + z);
		    }
		    Block currentBlock = spongeBlock.getRelative(x, y, z);
		    addToSpongeAreas(getBlockCoords(currentBlock));
		    if (blockIsAffected(currentBlock)) {
			currentBlock.setType(Material.AIR);
			if (debug) {
			    getLogger().info("The sponge absorbed " + currentBlock.getType());
			}
		    }
		}
	    }
	}
    }

    /**
     * Supposed to remove the sponge from the database and allow water to flow
     * back if able
     * 
     * @param theSponge
     * @return
     */
    public LinkedList<Block> disableSponge(Block theSponge) {
	flag = true;
	// Mark blocks
	LinkedList<Block> markedBlocks = new LinkedList<Block>();
	// Loop around the sponge block
	for (int x = spongeAreaDownLimit; x < spongeAreaUpLimit; x++) {
	    for (int y = spongeAreaDownLimit; y < spongeAreaUpLimit; y++) {
		for (int z = spongeAreaDownLimit; z < spongeAreaUpLimit; z++) {
		    final Block currentBlock = theSponge.getRelative(x, y, z);
		    removeFromSpongeAreas(getBlockCoords(currentBlock));

		    if (Settings.restoreWater) {
			if (debug) {
			    getLogger().info("restore water = true");
			}
			// If the database does not have a record of this block
			// location then mark it as removed
			if (!spongeAreas.containsKey(getBlockCoords(currentBlock))) {
			    markAsRemoved(getBlockCoords(currentBlock));
			    markedBlocks.add(currentBlock);
			}
		    }

		    if (debug) {
			getLogger().info("AirSearching: " + x + ", " + y + ", " + z);
		    }
		    if (isAir(currentBlock)) {
			// currentBlock.setType(Material.PISTON_MOVING_PIECE);
			// // Technical clear block
			currentBlock.setType(Material.PISTON_MOVING_PIECE);
			currentBlock.setType(Material.AIR); // Turn air into
			// air.
		    }
		}
	    }
	}
	flag = false;
	return markedBlocks;
    }

    public boolean blockIsAffected(Block theBlock) {
	if (isWater(theBlock)) {
	    return true;
	} else if (isLava(theBlock)) {
	    if (Settings.absorbLava) {
		return true;
	    }
	} else if (isFire(theBlock)) {
	    if (Settings.absorbFire) {
		return true;
	    }
	}
	return false;
    }

    public void addToSpongeAreas(String coords) {
	if (spongeAreas.containsKey(coords)) {
	    spongeAreas.put(coords, spongeAreas.get(coords) + 1);
	} else {
	    spongeAreas.put(coords, 1);
	}
    }

    /**
     * Decrements the coordinate if it is in the database and removes it if it
     * is zero
     * 
     * @param coords
     */
    public void removeFromSpongeAreas(String coords) {
	if (spongeAreas.containsKey(coords)) {
	    spongeAreas.put(coords, spongeAreas.get(coords) - 1);
	    if (spongeAreas.get(coords) == 0) {
		spongeAreas.remove(coords);
	    }
	}
    }

    public int completeRemoveBlocksFromAreas(LinkedList<Block> blawks) {
	int output = 0;
	for (Block blawk : blawks) {
	    String coords = getBlockCoords(blawk);
	    if (spongeAreas.containsKey(coords)) {
		spongeAreas.remove(getBlockCoords(blawk));
		output++;
	    }
	}
	return output;
    }

    /**
     * Marks a location as removed
     * 
     * @param coords
     */
    public void markAsRemoved(String coords) {
	String removedCoord = coords + ".removed";
	// If it has been removed before, then the database is incremented by
	// one
	if (spongeAreas.containsKey(removedCoord)) {
	    spongeAreas.put(removedCoord, spongeAreas.get(removedCoord) + 1);
	} else {
	    spongeAreas.put(removedCoord, 1);
	}
    }

    public Boolean isNextToSpongeArea(Block theBlock) {
	if (spongeAreas.containsKey(getBlockCoords(theBlock.getRelative(BlockFace.NORTH)))) {
	    if (debug) {
		getLogger().info("Fire wont spread north!");
	    }
	    return true;
	}
	if (spongeAreas.containsKey(getBlockCoords(theBlock.getRelative(BlockFace.EAST)))) {
	    if (debug) {
		getLogger().info("Fire wont spread east!");
	    }
	    return true;
	}
	if (spongeAreas.containsKey(getBlockCoords(theBlock.getRelative(BlockFace.SOUTH)))) {
	    if (debug) {
		getLogger().info("Fire wont spread south!");
	    }
	    return true;
	}
	if (spongeAreas.containsKey(getBlockCoords(theBlock.getRelative(BlockFace.WEST)))) {
	    if (debug) {
		getLogger().info("Fire wont spread west!");
	    }
	    return true;
	}
	if (spongeAreas.containsKey(getBlockCoords(theBlock.getRelative(BlockFace.UP)))) {
	    if (debug) {
		getLogger().info("Fire wont spread up!");
	    }
	    return true;
	}
	if (spongeAreas.containsKey(getBlockCoords(theBlock.getRelative(BlockFace.DOWN)))) {
	    if (debug) {
		getLogger().info("Fire wont spread down!");
	    }
	    return true;
	}
	return false;
    }

    public void killSurroundingFire(Block fireMan) {
	if (isFire(fireMan.getRelative(BlockFace.NORTH))) {
	    fireMan.getRelative(BlockFace.NORTH).setType(Material.AIR);
	}
	if (isFire(fireMan.getRelative(BlockFace.EAST))) {
	    fireMan.getRelative(BlockFace.EAST).setType(Material.AIR);
	}
	if (isFire(fireMan.getRelative(BlockFace.SOUTH))) {
	    fireMan.getRelative(BlockFace.SOUTH).setType(Material.AIR);
	}
	if (isFire(fireMan.getRelative(BlockFace.WEST))) {
	    fireMan.getRelative(BlockFace.WEST).setType(Material.AIR);
	}
	if (isFire(fireMan.getRelative(BlockFace.UP))) {
	    fireMan.getRelative(BlockFace.UP).setType(Material.AIR);
	}
	if (isFire(fireMan.getRelative(BlockFace.DOWN))) {
	    fireMan.getRelative(BlockFace.DOWN).setType(Material.AIR);
	}
    }

    public int convertAreaSponges(Player thePlayer, int radius, boolean enable) {
	Block theOrigin = thePlayer.getLocation().getBlock();
	int checkAreaUpLimit = radius + 1;
	int checkAreaDownLimit = radius * -1;
	int spongesConverted = 0;
	for (int x = checkAreaDownLimit; x < checkAreaUpLimit; x++) {
	    for (int y = checkAreaDownLimit; y < checkAreaUpLimit; y++) {
		for (int z = checkAreaDownLimit; z < checkAreaUpLimit; z++) {
		    Block currentBlock = theOrigin.getRelative(x, y, z);
		    if (isSponge(currentBlock)) {
			if (debug) {
			    getLogger().info("Sponge found at: " + getBlockCoords(currentBlock));
			}
			if (enable) {
			    enableSponge(currentBlock);
			} else {
			    disableSponge(currentBlock);
			}
			spongesConverted++;
		    }
		}
	    }
	}
	return spongesConverted;
    }

    // Universal Functions
    public String getBlockCoords(Block theBlock) {
	return theBlock.getWorld().getName() + "." + theBlock.getX() + "." + theBlock.getY() + "." + theBlock.getZ();
    }

    public String getDeletedBlockCoords(Block theBlock) {
	return theBlock.getWorld().getName() + "." + theBlock.getX() + "." + theBlock.getY() + "." + theBlock.getZ() + ".removed";
    }

    public boolean isSponge(Block theBlock) {
	return (theBlock.getType() == Material.SPONGE);
    }

    public boolean isWater(Block theBlock) {
	return (theBlock.getType() == Material.WATER || theBlock.getType() == Material.STATIONARY_WATER);
    }

    public boolean isLava(Block theBlock) {
	return (theBlock.getType() == Material.LAVA || theBlock.getType() == Material.STATIONARY_LAVA);
    }

    public boolean isFire(Block theBlock) {
	return (theBlock.getType() == Material.FIRE);
    }

    public boolean isAir(Block theBlock) {
	return (theBlock.getType() == Material.AIR);
    }

    public boolean isIce(Block theBlock) {
	return (theBlock.getType() == Material.ICE);
    }

    public boolean hasSponges(List<Block> blocks) {
	for (Block blawk : blocks) {
	    if (isSponge(blawk)) {
		return true;
	    }
	}
	return false;
    }

    public LinkedList<Block> getSponges(List<Block> blocks) {
	LinkedList<Block> output = new LinkedList<Block>();
	for (Block blawk : blocks) {
	    if (isSponge(blawk)) {
		output.add(blawk);
	    }
	}
	return output;
    }
    // End Sponges
    /**
     * Saves the challenge.yml file if it does not exist
     */
    public void saveDefaultChallengeConfig() {
	if (challengeConfigFile == null) {
	    challengeConfigFile = new File(getDataFolder(), "challenges.yml");
	}
	if (!challengeConfigFile.exists()) {            
	    plugin.saveResource("challenges.yml", false);
	}
    }

    /**
     * Reloads the challenge config file
     */
    public void reloadChallengeConfig() {
	if (challengeConfigFile == null) {
	    challengeConfigFile = new File(getDataFolder(), "challenges.yml");
	}
	challenges = YamlConfiguration.loadConfiguration(challengeConfigFile);

	// Look for defaults in the jar

	InputStream defConfigStream = this.getResource("challenges.yml");
	if (defConfigStream != null) {
	    @SuppressWarnings("deprecation")
	    YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
	    challenges.setDefaults(defConfig);
	}
    }

    /**
     * @return challenges FileConfiguration object
     */
    public FileConfiguration getChallengeConfig() {
	if (challenges == null) {
	    reloadChallengeConfig();
	}
	return challenges;
    }

    /**
     * Saves challenges.yml
     */
    public void saveChallengeConfig() {
	if (challenges == null || challengeConfigFile == null) {
	    return;
	}
	try {
	    getChallengeConfig().save(challengeConfigFile);
	} catch (IOException ex) {
	    getLogger().severe("Could not save config to " + challengeConfigFile);
	}
    }

    // Localization
    /**
     * Saves the locale.yml file if it does not exist
     */
    public void saveDefaultLocale() {
	if (localeFile == null) {
	    localeFile = new File(getDataFolder(), "locale.yml");
	}
	if (!localeFile.exists()) {            
	    plugin.saveResource("locale.yml", false);
	}
    }

    /**
     * Reloads the locale file
     */
    public void reloadLocale() {
	if (localeFile == null) {
	    localeFile = new File(getDataFolder(), "locale.yml");
	}
	locale = YamlConfiguration.loadConfiguration(localeFile);

	// Look for defaults in the jar
	InputStream defLocaleStream = this.getResource("locale.yml");
	if (defLocaleStream != null) {
	    YamlConfiguration defLocale = YamlConfiguration.loadConfiguration(defLocaleStream);
	    locale.setDefaults(defLocale);
	}
    }

    /**
     * @return locale FileConfiguration object
     */
    public FileConfiguration getLocale() {
	if (locale == null) {
	    reloadLocale();
	}
	return locale;
    }

    /**
     * Saves challenges.yml
     */
    public void saveLocale() {
	if (locale == null || localeFile == null) {
	    return;
	}
	try {
	    getLocale().save(localeFile);
	} catch (IOException ex) {
	    getLogger().severe("Could not save config to " + localeFile);
	}
    }

    public void tellOfflineTeam(UUID playerUUID, String message) {
	//getLogger().info("DEBUG: tell offline team called");
	if (!players.inTeam(playerUUID)) {
	    //getLogger().info("DEBUG: player is not in a team");
	    return;
	}
	UUID teamLeader = players.getTeamLeader(playerUUID);
	List<UUID> teamMembers = players.getMembers(teamLeader);
	for (UUID member : teamMembers) {
	    //getLogger().info("DEBUG: trying UUID " + member.toString());
	    if (getServer().getPlayer(member) == null) {
		// Offline player
		setMessage(member, message);
	    }
	}
    }
    /**
     * Sets a message for the player to receive next time they login
     * @param player
     * @param message
     * @return true if player is offline, false if online
     */
    public boolean setMessage(UUID playerUUID, String message) {
	//getLogger().info("DEBUG: received message - " + message);
	Player player = getServer().getPlayer(playerUUID);
	// Check if player is online
	if (player != null) {
	    if (player.isOnline()) {
		//player.sendMessage(message);
		return false;
	    }
	}
	// Player is offline so store the message
	
	List<String> playerMessages = messages.get(playerUUID);
	if (playerMessages != null) {
	    playerMessages.add(message);
	} else {
	    playerMessages = new ArrayList<String>(Arrays.asList(message));
	}
	messages.put(playerUUID, playerMessages);
	return true;
    }

    public List<String> getMessages(UUID playerUUID) {
	List<String> playerMessages = messages.get(playerUUID);
	if (playerMessages != null) {
	    // Remove the messages
	    messages.remove(playerUUID);
	} else {
	    // No messages
	    playerMessages = new ArrayList<String>();
	}
	return playerMessages;
    }
    
    public boolean saveMessages() {
	plugin.getLogger().info("Saving offline messages...");
	try {
	    // Convert to a serialized string
	    final HashMap<String,Object> offlineMessages = new HashMap<String,Object>();
	    for (UUID p : messages.keySet()) {
		offlineMessages.put(p.toString(),messages.get(p));
	    }
	    // Convert to YAML
	    messageStore.set("messages", offlineMessages);
	    saveYamlFile(messageStore, "messages.yml");
	    return true;
	} catch (Exception e) {
	    e.printStackTrace();
	    return false;
	}
    }

    public boolean loadMessages() {
	getLogger().info("Loading offline messages...");
	try {
	    messageStore = loadYamlFile("messages.yml");
	    if (messageStore.getConfigurationSection("messages") == null) {
		messageStore.createSection("messages"); // This is only used to create
	    }
	    HashMap<String,Object> temp = (HashMap<String, Object>) messageStore.getConfigurationSection("messages").getValues(true);
	    for (String s : temp.keySet()) {
		List<String> messageList = messageStore.getStringList("messages." + s);
		if (!messageList.isEmpty()) {
		    messages.put(UUID.fromString(s), messageList);
		}
	    }
	    return true;
	} catch (Exception e) {
	    e.printStackTrace();
	    return false;
	}
    }

}