package fr.skytasul.music.utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import fr.skytasul.music.JukeBox;

public class Lang{

	public static String NEXT_PAGE = ChatColor.AQUA + "Next page";
	public static String LATER_PAGE = ChatColor.AQUA + "Previous page";
	public static String CURRENT_PAGE = ChatColor.DARK_AQUA + "§oPage %d of %d";
	public static String PLAYER = ChatColor.RED + "You must be a player to do this command.";
	public static String RELOAD_MUSIC = ChatColor.GREEN + "Music reloaded.";
	public static String INV_NAME = ChatColor.LIGHT_PURPLE + "§lJukebox !";
	public static String TOGGLE_PLAYING = ChatColor.GOLD + "Pause/play";
	public static String VOLUME = ChatColor.BLUE + "Music volume : §b";
	public static String RIGHT_CLICK = "§eRight click: decrease by 10%";
	public static String LEFT_CLICK = "§eLeft click: increase by 10%";
	public static String RANDOM_MUSIC = ChatColor.DARK_AQUA + "Random music";
	public static String STOP = ChatColor.RED + "Stop the music";
	public static String MUSIC_STOPPED = ChatColor.GREEN + "Music stopped.";
	public static String ENABLE;
	public static String DISABLE;
	public static String ENABLED = "Enabled";
	public static String DISABLED = "Disabled";
	public static String TOGGLE_SHUFFLE_MODE;
	public static String TOGGLE_LOOP_MODE;
	public static String TOGGLE_CONNEXION_MUSIC;
	public static String TOGGLE_PARTICLES;
	public static String MUSIC_PLAYING = ChatColor.GREEN + "Music while playing:";
	public static String INCORRECT_SYNTAX = ChatColor.RED + "Incorrect syntax.";
	public static String RELOAD_LAUNCH = ChatColor.GREEN + "Trying to reload.";
	public static String RELOAD_FINISH = ChatColor.GREEN + "Reload finished.";
	public static String AVAILABLE_COMMANDS = ChatColor.GREEN + "Available commands:";
	public static String INVALID_NUMBER = ChatColor.RED + "Invalid number.";
	public static String PLAYER_MUSIC_STOPPED = ChatColor.GREEN + "Music stopped for player: §b";
	public static String IN_PLAYLIST = ChatColor.BLUE + "§oIn Playlist";
	public static String PLAYLIST_ITEM = ChatColor.LIGHT_PURPLE + "Playlists";
	public static String OPTIONS_ITEM = ChatColor.AQUA + "Options";
	public static String MENU_ITEM = ChatColor.GOLD + "Return to menu";
	public static String CLEAR_PLAYLIST = ChatColor.RED + "Clear the current playlist";
	public static String NEXT_ITEM = ChatColor.YELLOW + "Next song";
	public static String CHANGE_PLAYLIST = ChatColor.GOLD + "§lSwitch playlist: §r";
	public static String CHANGE_PLAYLIST_LORE = ChatColor.YELLOW + "Middle-click on a music disc\n§e to add/remove the song";
	public static String PLAYLIST = ChatColor.DARK_PURPLE + "Playlist";
	public static String FAVORITES = ChatColor.DARK_RED + "Favorites";
	public static String RADIO = ChatColor.DARK_AQUA + "Radio";
	public static String UNAVAILABLE_RADIO = ChatColor.RED + "This action is unavailable while listening to the radio.";
	public static String NONE = ChatColor.RED + "none";
	public static String TOGGLE_SHUFFLE_ON = "§eShuffle enabled.";
	public static String TOGGLE_SHUFFLE_OFF = "§eShuffle disabled.";
	public static String TOGGLE_PARTICLES_ON = "§eParticles enabled.";
	public static String TOGGLE_PARTICLES_OFF = "§eParticles disabled.";
	public static String TOGGLE_REPEAT_ON = "§eRepeat enabled.";
	public static String TOGGLE_REPEAT_OFF = "§eRepeat disabled.";
	public static String PLAYER_VOLUME = "§bYour player volume is currently set to {VOLUME}%";
	public static String PLAYER_VOLUME_CHANGED = "§bPlayer volume set to {VOLUME}%";
	public static String DEFAULT_PLAYLIST_EMPTY = "§cThe default server playlist is currently empty.";
	public static String PLAYLIST_RESET_SUCCESS = "§aYour playlist has been reset to the server default.";
	public static String DEFAULT_PLAYLIST_LIST_HEADER = "§6--- Default Playlist Songs ---";
	public static String DEFAULT_PLAYLIST_LIST_EMPTY = "§eThe default playlist is empty.";
	public static String DEFAULT_PLAYLIST_LIST_ENTRY = "§e- §7{SONG_NAME}";
	public static String DEFAULT_PLAYLIST_ADD_SUCCESS = "§aSong '§e{SONG_NAME}§a' added to the default playlist.";
	public static String DEFAULT_PLAYLIST_ADD_FAIL_EXISTS = "§cSong '§e{SONG_NAME}§c' is already in the default playlist.";
	public static String DEFAULT_PLAYLIST_ADD_FAIL_INVALID = "§cSong '§e{SONG_NAME}§c' is not a valid song.";
	public static String DEFAULT_PLAYLIST_REMOVE_SUCCESS = "§aSong '§e{SONG_NAME}§a' removed from the default playlist.";
	public static String DEFAULT_PLAYLIST_REMOVE_FAIL_NOT_FOUND = "§cSong '§e{SONG_NAME}§c' is not in the default playlist.";
	public static String DEFAULT_PLAYLIST_SET_SUCCESS = "§aDefault playlist set to contain {COUNT} songs.";
	public static String DEFAULT_PLAYLIST_SET_FAIL_INVALID = "§cSome songs were invalid and not added: {INVALID_SONGS}";
	public static String DEFAULT_PLAYLIST_CLEAR_SUCCESS = "§aDefault playlist cleared.";
	public static String DEFAULT_PLAYLIST_SAVE_ERROR = "§cAn error occurred while saving the default playlist file.";
	public static String RESET_ALL_SUCCESS = "§aReset playlists for {COUNT} online players to the server default.";
	public static String RESET_ALL_FAIL_EMPTY = "§cCannot reset all playlists because the default playlist is empty.";
	public static String PLAYLIST_RESET_NOTIFICATION = "§eYour playlist has been reset to the server default by an administrator.";
	public static String NOPERM = ChatColor.RED + "You do not have permission to do that.";

	// Added keys for directory playlists
	public static String LOOSE_NBS_FILES_WARNING = "§eWarning: Found .nbs files directly in the 'songs' folder. These files were ignored. Please place songs inside sub-directories (playlists): {FILES}";
	public static String PLAYLIST_ITEM_LORE_INFO = "§7Contains {COUNT} songs";
	public static String PLAYLIST_ITEM_LORE_ACTION = "§eLeft-click to play randomly";
	public static String NOW_PLAYING_RANDOM_FROM = "§aNow randomly playing songs from playlist: §e{PLAYLIST}";

	// Added keys for GUI revamp and CLI changes (edit3)
	public static String GUI_BACK_BUTTON = "§cBack";
	public static String GUI_PLAYLIST_ITEM_NAME = "§b[{ID}] {NAME}";
	public static String GUI_PLAYLIST_ITEM_LORE_ACTION = "§eLeft-click to view songs"; // Replaces old PLAYLIST_ITEM_LORE_ACTION
	public static String GUI_SONG_ITEM_NAME = "§e[{SUB_ID}] {NAME}";
	public static String CLI_PLAY_USAGE = "§cUsage: /amusic play <player> <list_id> [song_id|random]";
	public static String CLI_PLAY_INVALID_LIST_ID = "§cInvalid List ID: {ID}. Must be between 1 and {MAX}";
	public static String CLI_PLAY_INVALID_SUB_ID = "§cInvalid Song ID: {ID} for playlist '{PLAYLIST}'. Must be between 1 and {MAX}";
	public static String CLI_PLAY_SUCCESS = "§aPlaying song '§e{SONG}§a' for player '§e{PLAYER}§a'.";
	public static String GUI_RANDOM_THIS_PLAYLIST_BUTTON = "§bRandom play this list";

	// Added key for CLI random play success (edit 4)
	public static String CLI_PLAY_RANDOM_SUCCESS = "§aPlaying playlist '§e{PLAYLIST}§a' randomly for player '§e{PLAYER}§a'.";

	// Added keys for random start feedback (edit 5)
	public static String GUI_RANDOM_STARTED_WITH;
	public static String CLI_RANDOM_STARTED_WITH;
	
	// Added keys for selector support
	public static String SELECTOR_S_NOT_PLAYER = "§cError: @s selector can only be used by a player.";
	public static String SELECTOR_P_NO_PLAYER_NEARBY = "§cError: No player found nearby for @p selector.";
	public static String SELECTOR_P_CONSOLE_UNSUPPORTED = "§cError: @p selector cannot be used from console.";
	public static String SELECTOR_A_NO_PLAYERS_ONLINE = "§eNote: No players are currently online.";
	public static String UNKNOWN_PLAYER_SELECTOR = "§cError: No player found matching selector '{SELECTOR}' or invalid selector.";
	public static String CLI_PLAY_MULTIPLE_SUCCESS = "§aPlaying song '§e{SONG}§a' for §e{COUNT}§a players.";
	public static String CLI_PLAY_RANDOM_MULTIPLE_SUCCESS = "§aPlaying playlist '§e{PLAYLIST}§a' randomly for §e{COUNT}§a players.";
	public static String TOGGLE_MULTIPLE_SUCCESS = "§aToggled playback for §e{COUNT}§a/§e{TOTAL}§a players.";

	// Added: New language keys for smart random play feature
	public static String NO_SONG_AVAILABLE_IN_PLAYLIST;
	public static String NOW_PLAYING_SONG_FROM;
	public static String ADMIN_PLAYING_SONG_FROM;
	public static String ADMIN_FAILED_TO_PLAY_RANDOM;
	public static String ADMIN_SUCCESS_PLAY_RANDOM;

	public static void saveFile(YamlConfiguration cfg, File file) throws ReflectiveOperationException, IOException {
		for (Field f : Lang.class.getDeclaredFields()){
			if (f.getType() != String.class) continue;
			if (!cfg.contains(f.getName())) cfg.set(f.getName(), f.get(null));
		}
		cfg.save(file);
	}
	
	public static void loadFromConfig(File file, YamlConfiguration cfg) {
		List<String> inexistant = new ArrayList<>();
		for (String key : cfg.getValues(false).keySet()){
			try {
				String str = cfg.getString(key);
				str = ChatColor.translateAlternateColorCodes('&', str);
				if (JukeBox.version >= 16) str = translateHexColorCodes("(&|§)#", "", str);
				try {
					Lang.class.getDeclaredField(key).set(key, str);
				}catch (NoSuchFieldException ex) {
					inexistant.add(key);
				}
			}catch (Exception e) {
				JukeBox.getInstance().getLogger().warning("Error when loading language value \"" + key + "\".");
				e.printStackTrace();
				continue;
			}
		}
		if (!inexistant.isEmpty())
			JukeBox.getInstance().getLogger().warning("Found " + inexistant.size() + " inexistant string(s) in " + file.getName() + ": " + String.join(" ", inexistant));
	}
	
	private static final char COLOR_CHAR = '\u00A7';
	
	private static String translateHexColorCodes(String startTag, String endTag, String message) {
		final Pattern hexPattern = Pattern.compile(startTag + "([A-Fa-f0-9]{6})" + endTag);
		Matcher matcher = hexPattern.matcher(message);
		StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);
		while (matcher.find()) {
			String group = matcher.group(2);
			matcher.appendReplacement(buffer, COLOR_CHAR + "x"
					+ COLOR_CHAR + group.charAt(0) + COLOR_CHAR + group.charAt(1)
					+ COLOR_CHAR + group.charAt(2) + COLOR_CHAR + group.charAt(3)
					+ COLOR_CHAR + group.charAt(4) + COLOR_CHAR + group.charAt(5));
		}
		return matcher.appendTail(buffer).toString();
	}
	
}
