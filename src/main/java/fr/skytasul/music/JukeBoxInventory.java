package fr.skytasul.music;

import com.xxmicloxx.NoteBlockAPI.model.Song;
import com.xxmicloxx.NoteBlockAPI.model.Playlist;
import fr.skytasul.music.utils.Lang;
import fr.skytasul.music.utils.Playlists;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Thanks to <i>xigsag</i> and <I>SBPrime</I> for the custom skull utility
 * @author SkytAsul
 */
public class JukeBoxInventory implements Listener{

	private static final String RADIO_TEXTURE_URL =
			"http://textures.minecraft.net/texture/148a8c55891dec76764449f57ba677be3ee88a06921ca93b6cc7c9611a7af";

	private static final Pattern NEWLINE_REGEX = Pattern.compile("\\\\n|\\n");

	private static ItemStack stopItem = item(Material.BARRIER, Lang.STOP);
	private static ItemStack menuItem = item(Material.TRAPPED_CHEST, Lang.MENU_ITEM);
	private static ItemStack toggleItem = item(JukeBox.version < 9 ? Material.STONE_BUTTON : Material.valueOf("END_CRYSTAL"), Lang.TOGGLE_PLAYING);
	private static ItemStack randomItem = item(Material.valueOf(JukeBox.version > 12 ? "FIRE_CHARGE" : "FIREBALL"), Lang.RANDOM_MUSIC);
	private static ItemStack playlistMenuItem = item(Material.CHEST, Lang.PLAYLIST_ITEM);
	private static ItemStack optionsMenuItem = item(Material.valueOf(JukeBox.version > 12 ? "COMPARATOR" : "REDSTONE_COMPARATOR"), Lang.OPTIONS_ITEM);
	private static ItemStack nextSongItem = item(Material.FEATHER, Lang.NEXT_ITEM);
	private static ItemStack clearItem = item(Material.LAVA_BUCKET, Lang.CLEAR_PLAYLIST);
	private static Material particles = JukeBox.version < 13 ? Material.valueOf("FIREWORK") : Material.valueOf("FIREWORK_ROCKET");
	private static Material sign = JukeBox.version < 14 ? Material.valueOf("SIGN") : Material.valueOf("OAK_SIGN");
	private static Material lead = JukeBox.version < 13 ? Material.valueOf("LEASH") : Material.valueOf("LEAD");
	private static List<String> playlistLore = Arrays.asList("", Lang.IN_PLAYLIST);

	private Material[] discs;
	private UUID id;
	public PlayerData pdata;

	private int mainListPage = 0;
	private ItemsMenu currentView = ItemsMenu.PLAYLIST_LIST;
	private String viewingPlaylistName = null;
	private int songListPage = 0;

	private static final Material PLAYLIST_ITEM_MATERIAL = Material.CHEST;
	private static final Material SONG_ITEM_MATERIAL_DEFAULT = Material.MUSIC_DISC_CAT;
	private static final ItemStack BACK_BUTTON_ITEM = item(Material.ARROW, Lang.GUI_BACK_BUTTON);
	private static final ItemStack RANDOM_THIS_PLAYLIST_ITEM = item(Material.ENDER_EYE, Lang.GUI_RANDOM_THIS_PLAYLIST_BUTTON);

	private Inventory inv;

	public JukeBoxInventory(Player p, PlayerData pdata) {
		Bukkit.getPluginManager().registerEvents(this, JukeBox.getInstance());
		this.id = p.getUniqueId();
		this.pdata = pdata;
		this.pdata.linked = this;

		Random ran = new Random();
		discs = new Material[JukeBox.getSongs().size()];
		if (!JukeBox.songItems.isEmpty()) {
		for (int i = 0; i < discs.length; i++){
			discs[i] = JukeBox.songItems.get(ran.nextInt(JukeBox.songItems.size()));
			}
		}

		this.inv = Bukkit.createInventory(null, 54, Lang.INV_NAME);

		setPlaylistListPage(p);

		openInventory(p);
	}

	public void openInventory(Player p) {
		this.currentView = ItemsMenu.PLAYLIST_LIST;
		this.viewingPlaylistName = null;
		this.mainListPage = 0;
		this.songListPage = 0;
		this.inv = p.openInventory(inv).getTopInventory();
		setPlaylistListPage(p);
		setItemsMenu(p);
	}

	public void setPlaylistListPage(Player p) {
		List<String> orderedPlaylists = JukeBox.getPlaylistOrder();
		int totalPlaylists = orderedPlaylists.size();
		int totalPages = (int) Math.ceil(totalPlaylists * 1.0 / 45);
		if (totalPages == 0) totalPages = 1;
		String pageInfo = String.format(Lang.CURRENT_PAGE, mainListPage + 1, totalPages);

		for (int i = 0; i < 54; i++) inv.setItem(i, null);
		inv.setItem(52, item(Material.ARROW, Lang.LATER_PAGE, pageInfo));
		inv.setItem(53, item(Material.ARROW, Lang.NEXT_PAGE, pageInfo));

		if (pdata.getPlaylistType() == Playlists.RADIO) return;
		if (totalPlaylists == 0) return;
		int startIndex = mainListPage * 45;
		for (int i = 0; i < 45 && (startIndex + i) < totalPlaylists; i++) {
			int currentItemIndex = startIndex + i;
			String playlistName = orderedPlaylists.get(currentItemIndex);
			List<Song> songsInPlaylist = JukeBox.getDirectoryPlaylists().get(playlistName);
			if (songsInPlaylist != null && !songsInPlaylist.isEmpty()) {
				ItemStack playlistItem = new ItemStack(PLAYLIST_ITEM_MATERIAL);
				ItemMeta meta = playlistItem.getItemMeta();
				String displayName = Lang.GUI_PLAYLIST_ITEM_NAME
						.replace("{ID}", String.valueOf(currentItemIndex + 1))
						.replace("{NAME}", playlistName);
				meta.setDisplayName(displayName);
				List<String> lore = new ArrayList<>();
				lore.add(Lang.PLAYLIST_ITEM_LORE_INFO.replace("{COUNT}", String.valueOf(songsInPlaylist.size())));
				lore.add(Lang.GUI_PLAYLIST_ITEM_LORE_ACTION);
				meta.setLore(lore);
				playlistItem.setItemMeta(meta);
				inv.setItem(i, playlistItem);
			}
		}
		setItemsMenu(p);
	}

	public void setSongListPage(Player p) {
		if (viewingPlaylistName == null) return;
		List<Song> songsInPlaylist = JukeBox.getDirectoryPlaylists().get(viewingPlaylistName);
		if (songsInPlaylist == null) songsInPlaylist = Collections.emptyList();
		int totalSongs = songsInPlaylist.size();
		int totalPages = (int) Math.ceil(totalSongs * 1.0 / 45);
		if (totalPages == 0) totalPages = 1;
		String pageInfo = String.format(Lang.CURRENT_PAGE, songListPage + 1, totalPages);

		for (int i = 0; i < 54; i++) inv.setItem(i, null);
		inv.setItem(45, BACK_BUTTON_ITEM);
		inv.setItem(52, item(Material.ARROW, Lang.LATER_PAGE, pageInfo));
		inv.setItem(53, item(Material.ARROW, Lang.NEXT_PAGE, pageInfo));

		if (totalSongs == 0) {
			setItemsMenu(p);
			return;
		}
		int startIndex = songListPage * 45;
		for (int i = 0; i < 45 && (startIndex + i) < totalSongs; i++) {
			int currentSongIndex = startIndex + i;
			Song song = songsInPlaylist.get(currentSongIndex);
			ItemStack songItem = new ItemStack(SONG_ITEM_MATERIAL_DEFAULT);
			ItemMeta meta = songItem.getItemMeta();
			String displayName = Lang.GUI_SONG_ITEM_NAME
					.replace("{SUB_ID}", String.valueOf(currentSongIndex + 1))
					.replace("{NAME}", JukeBox.getInternal(song));
			meta.setDisplayName(displayName);
			List<String> lore = new ArrayList<>();
			String author = song.getAuthor();
			if (author != null && !author.isEmpty()) {
				lore.add("§7Author: §f" + author);
		}
			String description = song.getDescription();
			if (description != null && !description.isEmpty()) {
				lore.addAll(splitOnSpace("§7Desc: §f" + description, 30));
			}
			lore.add("§eLeft-click to play");
			meta.setLore(lore);
			meta.addItemFlags(ItemFlag.values());
			songItem.setItemMeta(meta);
			inv.setItem(i, songItem);
		}
		setItemsMenu(p);
	}

	public void setItemsMenu(Player p) {
		for (int i = 45; i < 52; i++) inv.setItem(i, null);

		switch (currentView) {
		case PLAYLIST_LIST:
			inv.setItem(45, stopItem);
			if (pdata.isListening()) inv.setItem(46, toggleItem);
			inv.setItem(49, playlistMenuItem);
			inv.setItem(50, optionsMenuItem);
			break;
		case SONG_LIST:
			inv.setItem(45, BACK_BUTTON_ITEM);
			inv.setItem(46, stopItem);
			if (pdata.isListening()) inv.setItem(46, toggleItem);
			if (viewingPlaylistName != null) {
				List<Song> songsInList = JukeBox.getDirectoryPlaylists().get(viewingPlaylistName);
				if (songsInList != null && !songsInList.isEmpty()) {
					inv.setItem(48, RANDOM_THIS_PLAYLIST_ITEM);
				}
			}
			break;
		case OPTIONS:
			inv.setItem(45, menuItem);
			inv.setItem(47, item(Material.BEACON, "§cerror", Lang.RIGHT_CLICK, Lang.LEFT_CLICK));
			volumeItem();
			if (pdata.getPlaylistType() != Playlists.RADIO) {
				if (JukeBox.particles && pdata.getPlayer().hasPermission("music.particles")) inv.setItem(48, item(particles, "§cerror"));
				particlesItem();
				if (pdata.getPlayer().hasPermission("music.play-on-join")) inv.setItem(49, item(sign, "§cerror"));
				joinItem();
				if (pdata.getPlayer().hasPermission("music.shuffle")) inv.setItem(50, item(Material.BLAZE_POWDER, "§cerror"));
				shuffleItem();
				if (pdata.getPlayer().hasPermission("music.loop")) inv.setItem(51, item(lead, "§cerror"));
				repeatItem();
			}
			break;
		case PLAYLIST:
			inv.setItem(45, menuItem);
			inv.setItem(47, nextSongItem);
			inv.setItem(48, clearItem);
			inv.setItem(50, pdata.getPlaylistType().item);
			break;
		}
	}

	public UUID getID(){
		return id;
	}

	@EventHandler
	public void onClick(InventoryClickEvent e){
		Player p = (Player) e.getWhoClicked();
		if (e.getClickedInventory() == null || !e.getClickedInventory().equals(inv)) return;
		if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
		if (!p.getUniqueId().equals(id)) return;
		e.setCancelled(true);
		int slot = e.getSlot();
		ItemStack clickedItem = e.getCurrentItem();

		switch (currentView) {
		case PLAYLIST_LIST:
			if (slot < 45 && clickedItem.getType() == PLAYLIST_ITEM_MATERIAL) {
				String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
				String playlistName = displayName.substring(displayName.indexOf(']') + 2);
				viewingPlaylistName = playlistName;
				songListPage = 0;
				currentView = ItemsMenu.SONG_LIST;
				setSongListPage(p);
			} else if (slot == 52 || slot == 53) {
				int totalPlaylists = JukeBox.getPlaylistOrder().size();
				int totalPages = (int) Math.ceil(totalPlaylists * 1.0 / 45);
				if (totalPages == 0) totalPages = 1;
				if (slot == 53 && mainListPage < totalPages - 1) {
					mainListPage++;
				} else if (slot == 52 && mainListPage > 0) {
					mainListPage--;
				}
				setPlaylistListPage(p);
			} else if (slot >= 45) {
				handleBottomMenuClick(p, slot, clickedItem, e.getClick());
			}
			break;
		case SONG_LIST:
			if (slot < 45 && clickedItem.getType().name().contains("DISC")) {
				List<Song> songsInList = JukeBox.getDirectoryPlaylists().get(viewingPlaylistName);
				if (songsInList != null) {
					int songIndex = slot + songListPage * 45;
					if (songIndex >= 0 && songIndex < songsInList.size()) {
						Song songToPlay = songsInList.get(songIndex);
						pdata.playSong(songToPlay);
						//p.closeInventory();
					} else {
						JukeBox.sendMessage(p, "§cError selecting song.");
					}
				} else {
					JukeBox.sendMessage(p, "§cError accessing playlist songs.");
				}
			} else if (slot == 45 && clickedItem.equals(BACK_BUTTON_ITEM)) {
				currentView = ItemsMenu.PLAYLIST_LIST;
				viewingPlaylistName = null;
				setPlaylistListPage(p);
			} else if (slot == 52 || slot == 53) {
				List<Song> songsInList = JukeBox.getDirectoryPlaylists().get(viewingPlaylistName);
				int totalSongs = (songsInList != null) ? songsInList.size() : 0;
				int totalPages = (int) Math.ceil(totalSongs * 1.0 / 45);
				if (totalPages == 0) totalPages = 1;
				if (slot == 53 && songListPage < totalPages - 1) {
					songListPage++;
				} else if (slot == 52 && songListPage > 0) {
					songListPage--;
			}
				setSongListPage(p);
			} else if (slot >= 45) {
				handleBottomMenuClick(p, slot, clickedItem, e.getClick());
			}
			break;
		case OPTIONS:
		case PLAYLIST:
			if (slot >= 45) {
				handleBottomMenuClick(p, slot, clickedItem, e.getClick());
			}
			break;
		}
	}

	private void handleBottomMenuClick(Player p, int slot, ItemStack clickedItem, ClickType click) {
			if (slot == 45) {
			if (currentView == ItemsMenu.PLAYLIST_LIST) {
					pdata.stopPlaying(true);
				setItemsMenu(p);
			} else if (currentView == ItemsMenu.OPTIONS || currentView == ItemsMenu.PLAYLIST) {
				currentView = ItemsMenu.PLAYLIST_LIST;
				setPlaylistListPage(p);
				}
				return;
			}

				switch (slot) {
				case 46:
			if (currentView == ItemsMenu.PLAYLIST_LIST) {
					pdata.togglePlaying();
				setItemsMenu(p);
			} else if (currentView == ItemsMenu.SONG_LIST) {
				if (pdata.isListening()) {
					pdata.togglePlaying();
				} else {
					pdata.stopPlaying(true);
				}
				setItemsMenu(p);
			}
				break;
				case 47:
			if (currentView == ItemsMenu.PLAYLIST_LIST) {
				pdata.playRandom();
			} else if (currentView == ItemsMenu.OPTIONS) {
				if (click == ClickType.RIGHT) pdata.setVolume((byte) (pdata.getVolume() - 10));
				if (click == ClickType.LEFT) pdata.setVolume((byte) (pdata.getVolume() + 10));
					if (pdata.getVolume() < 0) pdata.setVolume((byte) 0);
					if (pdata.getVolume() > 100) pdata.setVolume((byte) 100);
				volumeItem();
			} else if (currentView == ItemsMenu.PLAYLIST) {
				pdata.nextSong();
			}
					break;
				case 48:
			if (currentView == ItemsMenu.SONG_LIST) {
				if (viewingPlaylistName != null) {
					List<Song> sortedSongsToPlay = JukeBox.getDirectoryPlaylists().get(viewingPlaylistName);
					if (sortedSongsToPlay != null && !sortedSongsToPlay.isEmpty()) {
						pdata.playDirectoryPlaylist(sortedSongsToPlay, viewingPlaylistName);
						if (pdata.songPlayer != null) {
							Song firstSong = pdata.songPlayer.getSong();
							if (firstSong != null) {
								int subId = sortedSongsToPlay.indexOf(firstSong) + 1;
								JukeBox.sendMessage(p, Lang.GUI_RANDOM_STARTED_WITH
										.replace("{SUB_ID}", String.valueOf(subId))
										.replace("{SONG}", JukeBox.getInternal(firstSong)));
							}
						}
						p.closeInventory();
					} else {
						JukeBox.sendMessage(p, "§cPlaylist is empty or invalid.");
					}
				} else {
					JukeBox.sendMessage(p, "§cError: No playlist selected.");
				}
			} else if (currentView == ItemsMenu.OPTIONS) {
					pdata.setParticles(!pdata.hasParticles());
				particlesItem();
			} else if (currentView == ItemsMenu.PLAYLIST) {
				pdata.clearPlaylist();
			}
					break;
				case 49:
			if (currentView == ItemsMenu.PLAYLIST_LIST) {
				currentView = ItemsMenu.PLAYLIST;
				setItemsMenu(p);
			} else if (currentView == ItemsMenu.OPTIONS) {
					if (!JukeBox.autoJoin) pdata.setJoinMusic(!pdata.hasJoinMusic());
				joinItem();
				}
				break;
				case 50:
			if (currentView == ItemsMenu.PLAYLIST_LIST) {
				currentView = ItemsMenu.OPTIONS;
				setItemsMenu(p);
			} else if (currentView == ItemsMenu.OPTIONS) {
				pdata.setShuffle(!pdata.isShuffle());
				shuffleItem();
			} else if (currentView == ItemsMenu.PLAYLIST) {
					pdata.nextPlaylist();
				playlistItem();
				}
				break;
		case 51:
			if (currentView == ItemsMenu.OPTIONS) {
				pdata.setRepeat(!pdata.isRepeatEnabled());
				repeatItem();
			}
			break;
		}
	}

	public ItemStack getSongItem(Song s, Player p) {
		ItemStack is = item(discs[JukeBox.getSongs().indexOf(s)], JukeBox.getItemName(s, p));
		if (s.getDescription() != null && !s.getDescription().isEmpty()) loreAdd(is, splitOnSpace(JukeBox.format(JukeBox.descriptionFormat, JukeBox.descriptionFormatWithoutAuthor, s), 30));
		return is;
	}

	public void volumeItem(){
		if (currentView == ItemsMenu.OPTIONS) name(inv.getItem(47), Lang.VOLUME + pdata.getVolume() + "%");
	}

	public void particlesItem(){
		if (currentView != ItemsMenu.OPTIONS) return;
		if (!JukeBox.particles) return;
		if (!JukeBox.particles) inv.setItem(48, null);
		name(inv.getItem(48), ChatColor.AQUA + replaceToggle(Lang.TOGGLE_PARTICLES, pdata.hasParticles()));
	}

	public void joinItem(){
		if (currentView == ItemsMenu.OPTIONS) name(inv.getItem(49), ChatColor.GREEN + replaceToggle(Lang.TOGGLE_CONNEXION_MUSIC, pdata.hasJoinMusic()));
	}

	public void shuffleItem(){
		if (currentView == ItemsMenu.OPTIONS) name(inv.getItem(50), ChatColor.YELLOW + replaceToggle(Lang.TOGGLE_SHUFFLE_MODE, pdata.isShuffle()));
	}

	public void repeatItem(){
		if (currentView == ItemsMenu.OPTIONS) name(inv.getItem(51), ChatColor.GOLD + replaceToggle(Lang.TOGGLE_LOOP_MODE, pdata.isRepeatEnabled()));
	}

	private String replaceToggle(String string, boolean enabled) {
		return string.replace("{TOGGLE}", enabled ? Lang.DISABLE : Lang.ENABLE);
	}

	public void playingStarted() {
		if (currentView == ItemsMenu.PLAYLIST_LIST || currentView == ItemsMenu.SONG_LIST) inv.setItem(46, toggleItem);
	}

	public void playingStopped() {
		if (currentView == ItemsMenu.PLAYLIST_LIST || currentView == ItemsMenu.SONG_LIST) inv.setItem(46, null);
	}

	public void playlistItem(){
		if (currentView == ItemsMenu.PLAYLIST)
			inv.setItem(50, pdata.getPlaylistType().item);
		else if (currentView == ItemsMenu.OPTIONS) setItemsMenu(null);
	}

	public void songItem(int id, Player p) {
		if (!(id > mainListPage*45 && id < (mainListPage+1)*45) || pdata.getPlaylistType() == Playlists.RADIO) return;
		Song song = JukeBox.getSongs().get(id);
		ItemStack is = getSongItem(song, p);
		if (pdata.isInPlaylist(song)) loreAdd(is, playlistLore);
		inv.setItem(id - mainListPage*45, is);
	}

	public static ItemStack item(Material type, String name, String... lore) {
		ItemStack is = new ItemStack(type);
		ItemMeta im = is.getItemMeta();
		im.setDisplayName(name);
		im.setLore(Arrays.stream(lore).flatMap(NEWLINE_REGEX::splitAsStream).collect(Collectors.toList()));
		im.addItemFlags(ItemFlag.values());
		is.setItemMeta(im);
		return is;
	}

	public static ItemStack loreAdd(ItemStack is, List<String> lore){
		ItemMeta im = is.getItemMeta();
		List<String> ls = im.getLore();
		if (ls == null) ls = new ArrayList<>();
		ls.addAll(lore);
		im.setLore(ls);
		is.setItemMeta(im);
		return is;
	}

	public static ItemStack name(ItemStack is, String name) {
		if (is == null) return null;
		ItemMeta im = is.getItemMeta();
		im.setDisplayName(name);
		is.setItemMeta(im);
		return is;
	}

	public static final ItemStack radioItem;
    static {
        ItemStack item = new ItemStack(Material.valueOf("PLAYER_HEAD"));
        SkullMeta headMeta = (SkullMeta) item.getItemMeta();
		UUID uuid = UUID.randomUUID();
		PlayerProfile playerProfile = Bukkit.createPlayerProfile(uuid, uuid.toString().substring(0, 16));
		PlayerTextures textures = playerProfile.getTextures();
		try {
			textures.setSkin(new URI(RADIO_TEXTURE_URL).toURL());
		} catch (MalformedURLException | URISyntaxException ex) {
			JukeBox.getInstance().getLogger()
					.severe("An error occured during initialization of Radio item. Please report it to an administrator !");
			ex.printStackTrace();
		}
		playerProfile.setTextures(textures);
		headMeta.setOwnerProfile(playerProfile);
        headMeta.setDisplayName(Lang.CHANGE_PLAYLIST + Lang.RADIO);
        item.setItemMeta(headMeta);
        radioItem = item;
    }

	public static List<String> splitOnSpace(String string, int minSize){
		if (string == null || string.isEmpty()) return null;
		List<String> ls = new ArrayList<>();
		if (string.length() <= minSize){
			ls.add(string);
			return ls;
		}

		for (String str : NEWLINE_REGEX.split(string)) {
			int lastI = 0;
			int ic = 0;
			for (int i = 0; i < str.length(); i++){
				String color = "";
				if (!ls.isEmpty()) color = ChatColor.getLastColors(ls.get(ls.size() - 1));
				if (ic >= minSize){
					if (str.charAt(i) == ' '){
						ls.add(color + str.substring(lastI, i));
						ic = 0;
						lastI = i + 1;
					}else if (i + 1 == str.length()){
						ls.add(color + str.substring(lastI, i + 1));
					}
				}else if (str.length() - lastI <= minSize){
					ls.add(color + str.substring(lastI, str.length()));
					break;
				}
				ic++;
			}
		}

		return ls;
	}

	enum ItemsMenu{
		PLAYLIST_LIST, SONG_LIST, OPTIONS, PLAYLIST;
	}

}