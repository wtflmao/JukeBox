package fr.skytasul.music;

import com.tchristofferson.configupdater.ConfigUpdater;
import com.xxmicloxx.NoteBlockAPI.NoteBlockAPI;
import com.xxmicloxx.NoteBlockAPI.model.Playlist;
import com.xxmicloxx.NoteBlockAPI.model.Song;
import com.xxmicloxx.NoteBlockAPI.utils.NBSDecoder;
import fr.skytasul.music.utils.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.Collator;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class JukeBox extends JavaPlugin implements Listener{

	public static int version = Integer.parseInt(Bukkit.getBukkitVersion().split("-R")[0].split("\\.")[1]);
	private static JukeBox instance;
	private boolean disable = false;

	private static File playersFile;
	public static FileConfiguration players;
	public static File songsFolder;
	private static File defaultPlaylistFile;

	public static JukeBoxRadio radio = null;

	private static LinkedList<Song> songs;
	private static Map<String, Song> fileNames;
	private static Map<String, Song> internalNames;
	private static Playlist playlist;
	private static Playlist defaultPlaylist = null;
	private static List<String> defaultFavoritesList = new ArrayList<>();

	public static int maxPage;
	public static boolean jukeboxClick = false;
	public static boolean sendMessages = true;
	public static boolean async = false;
	public static boolean autoJoin = false;
	public static boolean radioEnabled = true;
	public static boolean radioOnJoin = false;
	public static boolean autoReload = true;
	public static boolean preventVanillaMusic = false;
	public static String songOnJoinName;
	public static Song songOnJoin;
	public static PlayerData defaultPlayer = null;
	public static List<String> worldsEnabled;
	public static boolean worlds;
	public static boolean particles;
	public static boolean actionBar;
	public static List<Material> songItems;
	public static String itemFormat;
	public static String itemFormatWithoutAuthor;
	public static String itemFormatAdmin;
	public static String itemFormatAdminWithoutAuthor;
	public static String songFormat;
	public static String songFormatWithoutAuthor;
	public static String descriptionFormat;
	public static String descriptionFormatWithoutAuthor;
	public static boolean savePlayerDatas = true;
	public static int fadeInDuration, fadeOutDuration;
	public static boolean useExtendedOctaveRange = true;
	public static boolean forceMono = false;
	public static boolean adminCommandFeedback = true;

	public ItemStack jukeboxItem;

	private Database db;
	public JukeBoxDatas datas;

	private BukkitTask vanillaMusicTask = null;
	public Consumer<Player> stopVanillaMusic = null;

	// Added for directory playlists
	private static Map<String, List<Song>> directoryPlaylists = new LinkedHashMap<>();
	private static List<String> playlistOrder = new ArrayList<>();

	@Override
	public void onEnable(){
		getLogger().info("Rewritten version maintained by hhzm (JK trigger)");
		
		instance = this;

		if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) Placeholders.registerPlaceholders();
		getLogger().info("This JukeBox version requires NoteBlockAPI version 1.5.0 or more. Please ensure you have the right version before using JukeBox (you are using NBAPI ver. " + getPlugin(NoteBlockAPI.class).getDescription().getVersion() + ")");

		saveDefaultConfig();
		try {
			ConfigUpdater.update(this, "config.yml", new File(getDataFolder(), "config.yml"));
		}catch (IOException e) {
			getLogger().log(Level.WARNING, "Failed to update the configuration file.", e);
		}

		initAll();

		Metrics metrics = new Metrics(this, 9533);
		metrics.addCustomChart(new SimplePie("noteblockapi_version", () -> NoteBlockAPI.getAPI().getDescription().getVersion()));
		metrics.addCustomChart(new SingleLineChart("songs", () -> songs.size()));
	}

	@Override
	public void onDisable(){
		if (!disable) disableAll();
	}

	public void disableAll(){
		if (radio != null){
			radio.stop();
			radio = null;
		}
		if (datas != null) {
			if (savePlayerDatas && db == null) players.set("players", datas.getSerializedList());
			players.set("item", (jukeboxItem == null) ? null : jukeboxItem.serialize());
			try {
				players.save(playersFile);
			}catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (vanillaMusicTask != null) vanillaMusicTask.cancel();
		if (db != null) db.closeConnection();
		HandlerList.unregisterAll((JavaPlugin) this);
	}

	public void initAll(){
		reloadConfig();

		loadLang();
		if (disable) return;

		FileConfiguration config = getConfig();
		jukeboxClick = config.getBoolean("jukeboxClick");
		sendMessages = config.getBoolean("sendMessages");
		async = config.getBoolean("asyncLoading");
		autoJoin = config.getBoolean("forceJoinMusic");
		songOnJoinName = autoJoin ? config.getString("songOnJoin") : null;
		defaultPlayer = PlayerData.deserialize(config.getConfigurationSection("defaultPlayerOptions").getValues(false), null);
		particles = config.getBoolean("noteParticles") && version >= 9;
		actionBar = config.getBoolean("actionBar") && version >= 9;
		radioEnabled = config.getBoolean("radio");
		radioOnJoin = radioEnabled && config.getBoolean("radioOnJoin");
		autoReload = config.getBoolean("reloadOnJoin");
		preventVanillaMusic = config.getBoolean("preventVanillaMusic");
		if (preventVanillaMusic && (version < 13 || version >= 18)) {
			preventVanillaMusic = false;
			getLogger().warning("preventVanillaMusic is enabled in config.yml but is not support in your server version.");
		}
		songItems = config.getStringList("songItems").stream().map(Material::matchMaterial).collect(Collectors.toList());
		songItems.removeIf(x -> x == null);
		if (songItems.isEmpty()) {
			String[] materials;
			if (version > 12) {
				materials = new String[] { /*"MUSIC_DISC_11", */"MUSIC_DISC_13", "MUSIC_DISC_BLOCKS", "MUSIC_DISC_CAT", "MUSIC_DISC_CHIRP", "MUSIC_DISC_FAR", "MUSIC_DISC_MALL", "MUSIC_DISC_MELLOHI", "MUSIC_DISC_STAL", "MUSIC_DISC_STRAD", "MUSIC_DISC_WAIT", "MUSIC_DISC_WARD" };
			}else materials = new String[] { "RECORD_10", /*"RECORD_11", */"RECORD_12", "RECORD_3", "RECORD_4", "RECORD_5", "RECORD_6", "RECORD_7", "RECORD_8", "RECORD_9", "GOLD_RECORD", "GREEN_RECORD" };
			songItems = Arrays.stream(materials).map(Material::valueOf).collect(Collectors.toList());
		}
		itemFormat = config.getString("itemFormat");
		itemFormatWithoutAuthor = config.getString("itemFormatWithoutAuthor");
		itemFormatAdmin = config.getString("itemFormatAdmin");
		itemFormatAdminWithoutAuthor = config.getString("itemFormatAdminWithoutAuthor");
		songFormat = config.getString("songFormat");
		songFormatWithoutAuthor = config.getString("songFormatWithoutAuthor");
		descriptionFormat = config.getString("descriptionFormat");
		descriptionFormatWithoutAuthor = config.getString("descriptionFormatWithoutAuthor");
		savePlayerDatas = config.getBoolean("savePlayerDatas");
		fadeInDuration = config.getInt("fadeInDuration");
		fadeOutDuration = config.getInt("fadeOutDuration");
		useExtendedOctaveRange = config.getBoolean("useExtendedOctaveRange");
		forceMono = config.getBoolean("forceMono");
		adminCommandFeedback = config.getBoolean("admin-command-feedback", true);

		worldsEnabled = config.getStringList("enabledWorlds");
		worlds = !worldsEnabled.isEmpty();

		ConfigurationSection dbConfig = config.getConfigurationSection("database");
		if (dbConfig.getBoolean("enabled")) {
			db = new Database(dbConfig);
			if (db.openConnection()) {
				getLogger().info("Connected to database.");
			}else {
				getLogger().info("Failed to connect to database. Now using YAML system.");
				db = null;
			}
		}

		if (async){
			new BukkitRunnable() {
				@Override
				public void run() {
					loadDatas();
					finishEnabling();
				}
			}.runTaskAsynchronously(this);
		}else{
			loadDatas();
			finishEnabling();
		}

		if (preventVanillaMusic) {
			try {
				String nms = "net.minecraft.server";
				String cb = "org.bukkit.craftbukkit";
				Method getHandle = getVersionedClass(cb, "entity.CraftPlayer").getDeclaredMethod("getHandle");
				Field playerConnection = getVersionedClass(nms, "EntityPlayer").getDeclaredField("playerConnection");
				Method sendPacket = getVersionedClass(nms, "PlayerConnection").getDeclaredMethod(version < 18 ? "sendPacket" : "a", getVersionedClass(nms, "Packet"));
				Class<?> soundCategory = getVersionedClass(nms, "SoundCategory");
				Object packet = getVersionedClass(nms, "PacketPlayOutStopSound").getDeclaredConstructor(getVersionedClass(nms, "MinecraftKey"), soundCategory).newInstance(null, soundCategory.getDeclaredField("MUSIC").get(null));

				stopVanillaMusic = player -> {
					try {
						sendPacket.invoke(playerConnection.get(getHandle.invoke(player)), packet);
					}catch (ReflectiveOperationException e1) {
						e1.printStackTrace();
					}
				};
			}catch (ReflectiveOperationException ex) {
				ex.printStackTrace();
			}
		}

		loadDefaultPlaylist();
	}

	private Class<?> getVersionedClass(String packageName, String className) throws ClassNotFoundException {
		return Class.forName(packageName + "." + Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3] + "." + className);
	}

	private void finishEnabling(){
		getCommand("music").setExecutor(new CommandMusic());
		getCommand("adminmusic").setExecutor(new CommandAdmin());

		getServer().getPluginManager().registerEvents(this, this);

		radioEnabled = radioEnabled && !songs.isEmpty();
		if (radioEnabled){
			radio = new JukeBoxRadio(playlist);
		}else radioOnJoin = false;

		for (Player p : Bukkit.getOnlinePlayers()) {
			datas.joins(p);
		}

		if (stopVanillaMusic != null) {
			vanillaMusicTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
				for (PlayerData pdata : datas.getDatas()) {
					if (pdata.isPlaying() && pdata.getPlayer() != null) stopVanillaMusic.accept(pdata.getPlayer());
				}
			}, 20L, 100l); // every 5 seconds
		}
	}

	private void loadDatas(){
		/* --------------------------------------------- SONGS ------- */
		songs = new LinkedList<>();
		fileNames = new HashMap<>();
		internalNames = new HashMap<>();
		directoryPlaylists.clear();
		playlistOrder.clear();

		songsFolder = new File(getDataFolder(), "songs");
		if (!songsFolder.exists()) songsFolder.mkdirs();

		int loadedCount = 0;
		List<String> looseFiles = new ArrayList<>();

		for (File item : songsFolder.listFiles()){
			if (item.isDirectory()) {
				String playlistName = item.getName();
				List<Song> songsInDir = new ArrayList<>();
				getLogger().info("Loading songs from directory: " + playlistName);
				for (File songFile : item.listFiles()) {
					if (songFile.isFile() && songFile.getName().toLowerCase().endsWith(".nbs")) {
						Song song = NBSDecoder.parse(songFile);
						if (song == null) {
							getLogger().warning("Could not parse NBS file: " + songFile.getName() + " in directory " + playlistName);
							continue;
						}
						String internalName = getInternal(song);
						if (internalNames.containsKey(internalName)) {
							getLogger().warning("Song internal name '" + internalName + "' from file " + songFile.getName() + " in directory " + playlistName + " is duplicated (already loaded from another file/directory). Skipping.");
					continue;
				}
						songsInDir.add(song);
						internalNames.put(internalName, song);
						fileNames.put(songFile.getName(), song); // Keep file name mapping if needed
						loadedCount++;
					}
				}
				if (!songsInDir.isEmpty()) {
					// Sort songs within the directory playlist by internal name
					songsInDir.sort(Comparator.comparing(JukeBox::getInternal, Collator.getInstance()));
					directoryPlaylists.put(playlistName, songsInDir);
					playlistOrder.add(playlistName); // Add directory name to the order list
					getLogger().info("Loaded " + songsInDir.size() + " songs for playlist '" + playlistName + "'.");
				} else {
					getLogger().info("Directory '" + playlistName + "' is empty or contains no valid NBS files.");
				}
			} else if (item.isFile() && item.getName().toLowerCase().endsWith(".nbs")) {
				// Handle loose files - add to a special playlist or warn user
				looseFiles.add(item.getName());
			}
		}

		// Sort the directory playlist order list by name
		Collections.sort(playlistOrder, Collator.getInstance());

		if (!looseFiles.isEmpty()) {
			getLogger().warning(Lang.LOOSE_NBS_FILES_WARNING.replace("{FILES}", String.join(", ", looseFiles))); // Need Lang key
		}

		// Populate the global 'songs' list from directory playlists for Radio/Admin list
		// The order in the global list might differ now due to directory sorting
		songs.clear(); // Ensure it's empty before repopulating
		for (String orderedPlaylistName : playlistOrder) { // Iterate in sorted order
			if (directoryPlaylists.containsKey(orderedPlaylistName)) {
				songs.addAll(directoryPlaylists.get(orderedPlaylistName));
		}
		}
		// Collections.sort(songs, Comparator.comparing(JukeBox::getInternal, Collator.getInstance())); // Global sort might not be needed/desired anymore

		getLogger().info(internalNames.size() + " total unique songs loaded from " + directoryPlaylists.size() + " directories. Total playlists: " + playlistOrder.size());

		setMaxPage(); // Update max page based on number of directory playlists
		getLogger().info("Directory playlists sorted! Number of playlist pages: " + maxPage);

		// Create global playlist object if needed (for Radio)
		if (!songs.isEmpty()) { 
			playlist = new Playlist(songs.toArray(new Song[0]));
		} else { 
			playlist = null;
		}

		/* --------------------------------------------- PLAYERS ------- */
		try {
			playersFile = new File(getDataFolder(), "datas.yml");
			playersFile.createNewFile();
			players = YamlConfiguration.loadConfiguration(playersFile);
			if (players.get("item") != null) jukeboxItem = ItemStack.deserialize(players.getConfigurationSection("item").getValues(false));
		}catch (IOException e) {
			e.printStackTrace();
		}
		if (db == null) {
			datas = new JukeBoxDatas(players.getMapList("players"), internalNames);
		}else {
			try {
				datas = new JukeBoxDatas(db);
			}catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	void setMaxPage(){
		maxPage = (int) StrictMath.ceil(playlistOrder.size() * 1.0 / 45); // New logic based on number of playlists
		if (maxPage == 0) maxPage = 1; // Ensure at least one page even if empty
	}


	private YamlConfiguration loadLang() {
		String s = "en.yml";
		if (getConfig().getString("lang") != null) s = getConfig().getString("lang") + ".yml";
		File lang = new File(getDataFolder(), s);
		if (!lang.exists()) {
			try {
				getDataFolder().mkdir();
				lang.createNewFile();
				InputStream defConfigStream = this.getResource(s);
				if (defConfigStream != null) {
					YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
					defConfig.save(lang);
					Lang.loadFromConfig(lang, defConfig);
					getLogger().info("Created language file " + s);
					return defConfig;
				}
			} catch(IOException e) {
				e.printStackTrace();
				getLogger().severe("Couldn't create language file.");
				getLogger().severe("This is a fatal error. Now disabling.");
				disable = true;
				this.setEnabled(false);
				return null;
			}
		}
		YamlConfiguration conf = YamlConfiguration.loadConfiguration(lang);
		try {
			Lang.saveFile(conf, lang);
		}catch (IOException | ReflectiveOperationException e) {
			getLogger().warning("Failed to save lang.yml.");
			getLogger().warning("Report this stack trace to SkytAsul on SpigotMC.");
			e.printStackTrace();
		}
		Lang.loadFromConfig(lang, conf);
		getLogger().info("Loaded language file " + s);
		return conf;
	}


	@EventHandler
	public void onJoin(PlayerJoinEvent e){
		datas.joins(e.getPlayer());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e){
		datas.quits(e.getPlayer());
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent e){
		if (e.getItem() == null) return;
		Player p = e.getPlayer();
		PlayerData pdata = datas.getDatas(p);
		if (pdata == null) return;

		if (jukeboxItem != null && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)){
			if (e.getItem().equals(jukeboxItem)){
				CommandMusic.open(p, pdata);
				e.setCancelled(true);
				return;
			}
		}
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK && jukeboxClick){
			if (e.getClickedBlock().getType() == Material.JUKEBOX){
				String disc = e.getItem().getType().name();
				if (disc.contains("RECORD") || disc.contains("MUSIC_DISC_")) {
					CommandMusic.open(p, pdata);
					e.setCancelled(true);
					return;
				}
			}
		}
	}

	@EventHandler
	public void onTeleport(PlayerTeleportEvent e){
		if (!worlds) return;
		if (e.getFrom().getWorld() == e.getTo().getWorld()) return;
		if (worldsEnabled.contains(e.getTo().getWorld().getName())) return;
		PlayerData pdata = datas.getDatas(e.getPlayer());
		if (pdata == null) return;
		if (pdata.songPlayer != null) pdata.stopPlaying(true);
		if (pdata.getPlaylistType() == Playlists.RADIO) pdata.setPlaylist(Playlists.PLAYLIST, false);
	}


	public static JukeBox getInstance(){
		return instance;
	}

	public static boolean canSaveDatas(Player p) {
		return savePlayerDatas && p.hasPermission("music.save-datas");
	}

	private static Random random = new Random();
	public static Song randomSong() {
		if (songs.isEmpty()) return null;
		if (songs.size() == 1) return songs.get(0);
		return songs.get(random.nextInt(songs.size() - 1));
	}

	public static Playlist getPlaylist(){
		return playlist;
	}

	public static List<Song> getSongs(){
		return songs;
	}

	public static Song getSongByFile(String fileName){
		return fileNames.get(fileName);
	}

	public static Song getSongByInternalName(String internalName) {
		return internalNames.get(internalName);
	}

	public static String getInternal(Song s) {
		if (s == null) return "";
		if (s.getTitle() == null || s.getTitle().isEmpty()) return s.getPath().getName();
		return s.getTitle();
	}

	public static String getItemName(Song s, Player p) {
		boolean admin = p.hasPermission("music.adminItem");
		return format(admin ? itemFormatAdmin : itemFormat, admin ? itemFormatAdminWithoutAuthor : itemFormatWithoutAuthor, s);
	}

	public static String getSongName(Song song) {
		return format(songFormat, songFormatWithoutAuthor, song);
	}

	private static String removeFileExtension(String path) {
		int dot = path.lastIndexOf('.');
		if(dot == -1) return path;
		return path.substring(0, dot);
	}

	public static String format(String base, String noAuthorBase, Song song) {
		String author = song.getAuthor();
		String format = (author == null || author.isEmpty()) ? noAuthorBase : base.replace("{AUTHOR}", author);
		return format
				.replace("{NAME}", song.getTitle().isEmpty() ? removeFileExtension(song.getPath().getName()) : song.getTitle())
				.replace("{ID}", String.valueOf(songs.indexOf(song)))
				.replace("{DESCRIPTION}", song.getDescription());
	}

	public static boolean sendMessage(Player p, String msg){
		if (JukeBox.sendMessages){
			if (JukeBox.actionBar){
				p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
			}else {
				p.spigot().sendMessage(TextComponent.fromLegacyText(msg));
			}
			return true;
		}
		return false;
	}

	private void loadDefaultPlaylist() {
		defaultPlaylistFile = new File(getDataFolder(), "default_playlist.yml");
		try {
			if (!defaultPlaylistFile.exists()) {
				getLogger().info("Creating default_playlist.yml...");
				getDataFolder().mkdir();
				defaultPlaylistFile.createNewFile();
				YamlConfiguration cfg = new YamlConfiguration();
				cfg.options().header("List of internal song names for the default playlist given to new players.\nUse /jukeboxadmin songs list to see available internal names.");
				cfg.set("defaultFavorites", new ArrayList<String>());
				cfg.save(defaultPlaylistFile);
			}
			YamlConfiguration cfg = YamlConfiguration.loadConfiguration(defaultPlaylistFile);
			List<String> rawList = cfg.getStringList("defaultFavorites");
			defaultFavoritesList.clear();
			List<Song> validSongs = new ArrayList<>();
			for (String songName : rawList) {
				Song song = getSongByInternalName(songName);
				if (song != null) {
					validSongs.add(song);
					defaultFavoritesList.add(songName);
				} else {
					getLogger().warning("Song '" + songName + "' listed in default_playlist.yml not found. Skipping.");
				}
			}

			if (!validSongs.isEmpty()) {
				defaultPlaylist = new Playlist(validSongs.toArray(new Song[0]));
				getLogger().info("Loaded " + validSongs.size() + " songs into the default playlist.");
			} else {
				defaultPlaylist = null;
				getLogger().info("Default playlist is empty or contains only invalid songs.");
			}
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "Failed to load or create default_playlist.yml", e);
			defaultPlaylist = null;
		}
	}

	public boolean saveAndUpdateDefaultPlaylist() {
		if (defaultPlaylistFile == null) {
			getLogger().severe("Default playlist file reference is null. Cannot save.");
			return false;
		}
		try {
			YamlConfiguration cfg = YamlConfiguration.loadConfiguration(defaultPlaylistFile);
			cfg.set("defaultFavorites", defaultFavoritesList);
			cfg.save(defaultPlaylistFile);

			List<Song> validSongs = new ArrayList<>();
			for (String songName : defaultFavoritesList) {
				Song song = getSongByInternalName(songName);
				if (song != null) {
					validSongs.add(song);
				}
			}
			if (!validSongs.isEmpty()) {
				defaultPlaylist = new Playlist(validSongs.toArray(new Song[0]));
			} else {
				defaultPlaylist = null;
			}
			return true;
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "Failed to save default_playlist.yml", e);
			return false;
		}
	}

	public static Playlist getDefaultPlaylist() {
		return defaultPlaylist;
	}

	public static List<String> getDefaultFavoritesList() {
		return Collections.unmodifiableList(defaultFavoritesList);
	}

	public static void updateDefaultFavoritesList(List<String> newList) {
		defaultFavoritesList.clear();
		defaultFavoritesList.addAll(newList);
	}

	// Added Getters for new structures
	public static Map<String, List<Song>> getDirectoryPlaylists() {
		return directoryPlaylists;
	}

	public static List<String> getPlaylistOrder() {
		return playlistOrder;
	}

}

