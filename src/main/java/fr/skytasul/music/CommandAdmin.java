package fr.skytasul.music;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Files;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.xxmicloxx.NoteBlockAPI.model.Song;
import com.xxmicloxx.NoteBlockAPI.model.Playlist;
import com.xxmicloxx.NoteBlockAPI.utils.NBSDecoder;

import fr.skytasul.music.utils.Lang;
import fr.skytasul.music.utils.Playlists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CommandAdmin implements CommandExecutor{

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
		/*if (!(sender instanceof Player)){
			sender.sendMessage(Lang.PLAYER);
			return false;
		}
		Player p = (Player) sender;*/
		
		if (args.length == 0){
			sender.sendMessage(Lang.INCORRECT_SYNTAX);
			return false;
		}
		
		switch (args[0]){
		
		case "reload":
			sender.sendMessage(Lang.RELOAD_LAUNCH);
			try{
				JukeBox.getInstance().disableAll();
				JukeBox.getInstance().initAll();
			}catch (Exception ex){
				sender.sendMessage("§cError while reloading. Please check the console and send the stacktrace to SkytAsul on SpigotMC.");
				ex.printStackTrace();
			}
			sender.sendMessage(Lang.RELOAD_FINISH);
			break;
			
		case "player":
			if (args.length < 2){
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			OfflinePlayer pp = Bukkit.getOfflinePlayer(args[1]);
			if (pp == null){
				sender.sendMessage("§cUnknown player.");
				return false;
			}
			PlayerData pdata = JukeBox.getInstance().datas.getDatas(pp.getUniqueId());
			String s = Lang.MUSIC_PLAYING + " ";
			if (pdata == null){
				s = s + "§cx";
			}else {
				if (pdata.songPlayer == null){
					s = s + "§cx";
				}else {
					Song song = pdata.songPlayer.getSong();
					s = JukeBox.getSongName(song);
				}
			}
			sender.sendMessage(s);
			break;
			
		case "play":
			if (args.length != 4) {
				sender.sendMessage(Lang.CLI_PLAY_USAGE);
				return true;
			}
			Player targetPlayer = Bukkit.getPlayer(args[1]);
			if (targetPlayer == null) {
				sender.sendMessage("§cUnknown or offline player: " + args[1]);
				return true;
			}
			try {
				int listId = Integer.parseInt(args[2]);
				int subSongId = Integer.parseInt(args[3]);
				if (listId <= 0 || listId > JukeBox.getPlaylistOrder().size()) {
					sender.sendMessage(Lang.CLI_PLAY_INVALID_LIST_ID.replace("{ID}", args[2]).replace("{MAX}", String.valueOf(JukeBox.getPlaylistOrder().size())));
					return true;
				}
				String playlistName = JukeBox.getPlaylistOrder().get(listId - 1);
				List<Song> songsInPlaylist = JukeBox.getDirectoryPlaylists().get(playlistName);
				if (songsInPlaylist == null || subSongId <= 0 || subSongId > songsInPlaylist.size()) {
					int maxSubId = (songsInPlaylist == null) ? 0 : songsInPlaylist.size();
					sender.sendMessage(Lang.CLI_PLAY_INVALID_SUB_ID.replace("{ID}", args[3]).replace("{MAX}", String.valueOf(maxSubId)).replace("{PLAYLIST}", playlistName));
					return true;
				}
				Song songToPlay = songsInPlaylist.get(subSongId - 1);
				PlayerData targetPdata = JukeBox.getInstance().datas.getDatas(targetPlayer);
				if (targetPdata == null) {
					sender.sendMessage("§cCould not get player data for " + targetPlayer.getName());
					return true;
				}
				targetPdata.playSong(songToPlay);
				if (targetPdata.songPlayer != null) targetPdata.songPlayer.adminPlayed = true;
				sender.sendMessage(Lang.CLI_PLAY_SUCCESS.replace("{SONG}", JukeBox.getInternal(songToPlay)).replace("{PLAYER}", targetPlayer.getName()));
			} catch (NumberFormatException ex) {
				sender.sendMessage(Lang.INVALID_NUMBER + " for List ID or Song ID.");
				return true;
			}
			break;
			
		case "playlist":
			if (args.length < 3) {
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			if (args[1].equals("@a")) {
				for (Player p : Bukkit.getOnlinePlayers()) {
					args[1] = p.getName();
					String msg = playlist(args);
					if (!msg.isEmpty()) sender.sendMessage(p.getName() + " : " + msg);
				}
			}else {
				String msg = playlist(args);
				if (!msg.isEmpty()) sender.sendMessage(msg);
			}
			break;
		
		case "stop":
			if (args.length < 2){
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			if (args[1].equals("@a")){
				for (Player p : Bukkit.getOnlinePlayers()){
					sender.sendMessage(stop(p));
				}
			}else {
				Player cp = Bukkit.getPlayer(args[1]);
				if (cp == null){
					sender.sendMessage("§cUnknown player.");
					return false;
				}
				sender.sendMessage(stop(cp));
			}
			break;
			
		case "toggle":
			if (args.length < 2) {
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			if (args[1].equals("@a")) {
				for (Player p : Bukkit.getOnlinePlayers()) {
					toggle(p);
				}
			}else {
				Player cp = Bukkit.getPlayer(args[1]);
				if (cp == null) {
					sender.sendMessage("§cUnknown player.");
					return false;
				}
				toggle(cp);
			}
			break;
		
		case "setitem":
			if (!(sender instanceof Player)){
				sender.sendMessage("§cYou have to be a player to do that.");
				return false;
			}
			ItemStack is = ((Player) sender).getInventory().getItemInHand();
			if (is == null || is.getType() == Material.AIR){
				JukeBox.getInstance().jukeboxItem = null;
			}else JukeBox.getInstance().jukeboxItem = is;
			sender.sendMessage("§aItem edited. Now : §2" + ((JukeBox.getInstance().jukeboxItem == null) ? "null" : JukeBox.getInstance().jukeboxItem.toString()));
			break;
			
		case "download":
			if (args.length < 3){
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			try {
				String fileName = args[2];
				if (!fileName.toLowerCase().endsWith(".nbs")) fileName += ".nbs";
				File file = new File(JukeBox.songsFolder, fileName);
				if (file.exists()) {
					sender.sendMessage("§cThe file " + fileName + " already exists.");
					break;
				}
				String url = expandUrl(args[1]);
				try (InputStream openStream = new URL(url).openStream()) {
					Files.copy(openStream, file.toPath());
					boolean valid = true;
					FileInputStream stream = new FileInputStream(file);
					try {
						Song song = NBSDecoder.parse(stream);
						if (song == null) valid = false;
					}catch (Throwable e) {
						valid = false;
					}finally {
						stream.close();
						if (!valid) sender.sendMessage("§cDownloaded file is not a nbs song file.");
					}
					if (valid) {
						sender.sendMessage("§aSong downloaded. To add it to the list, you must reload the plugin. (§o/amusic reload§r§a)");
					}else file.delete();
				}
			} catch (Throwable e) {
				sender.sendMessage("§cError when downloading file.");
				e.printStackTrace();
			}
			break;
			
		case "shuffle":
			if (args.length < 2){
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			if (args[1].equals("@a")){
				for (Player p : Bukkit.getOnlinePlayers()){
					sender.sendMessage(p.getName() + " : " + shuffle(p));
				}
			}else {
				Player cp = Bukkit.getPlayer(args[1]);
				if (cp == null){
					sender.sendMessage("§cUnknown player.");
					return false;
				}
				sender.sendMessage(shuffle(cp));
			}
			break;
			
		case "particles":
			if (args.length < 2){
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			if (args[1].equals("@a")){
				for (Player p : Bukkit.getOnlinePlayers()){
					sender.sendMessage(p.getName() + " : " + particles(p));
				}
			}else {
				Player cp = Bukkit.getPlayer(args[1]);
				if (cp == null){
					sender.sendMessage("§cUnknown player.");
					return false;
				}
				sender.sendMessage(particles(cp));
			}
			break;
			
		case "join":
			if (args.length < 2){
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			if (args[1].equals("@a")){
				for (Player p : Bukkit.getOnlinePlayers()){
					sender.sendMessage(p.getName() + " : " + join(p));
				}
			}else {
				Player cp = Bukkit.getPlayer(args[1]);
				if (cp == null){
					sender.sendMessage("§cUnknown player.");
					return false;
				}
				sender.sendMessage(join(cp));
			}
			break;
			
		case "random":
			if (args.length < 2){
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			if (args[1].equals("@a")){
				for (Player p : Bukkit.getOnlinePlayers()){
					sender.sendMessage(p.getName() + " : " + random(p));
				}
			}else {
				Player cp = Bukkit.getPlayer(args[1]);
				if (cp == null){
					sender.sendMessage("§cUnknown player.");
					return false;
				}
				sender.sendMessage(random(cp));
			}
			break;
			
		case "volume":
			if (args.length < 3){
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			Player cp = Bukkit.getPlayer(args[1]);
			if (cp == null){
				sender.sendMessage("§cUnknown player.");
				return false;
			}
			PlayerData volumePdata = JukeBox.getInstance().datas.getDatas(cp);
			try{
				int volume;
				if (args[2].equals("+")) {
					volume = volumePdata.getVolume() + 10;
				}else if (args[2].equals("-")) {
					volume = volumePdata.getVolume() - 10;
				}else volume = Integer.parseInt(args[2]);
				volumePdata.setVolume(volume);
				sender.sendMessage("§aVolume : " + volumePdata.getVolume());
			}catch (NumberFormatException ex){
				sender.sendMessage(Lang.INVALID_NUMBER);
			}
			break;
			
		case "loop":
			if (args.length < 2){
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			if (args[1].equals("@a")){
				for (Player p : Bukkit.getOnlinePlayers()){
					sender.sendMessage(p.getName() + " : " + loop(p));
				}
			}else {
				cp = Bukkit.getPlayer(args[1]);
				if (cp == null){
					sender.sendMessage("§cUnknown player.");
					return false;
				}
				sender.sendMessage(loop(cp));
			}
			break;
			
		case "next":
			if (args.length < 2) {
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			if (args[1].equals("@a")) {
				int i = 0;
				for (Player p : Bukkit.getOnlinePlayers()) {
					JukeBox.getInstance().datas.getDatas(p).nextSong();
					i++;
				}
				sender.sendMessage("§aNext song for " + i + "players.");
			}else {
				cp = Bukkit.getPlayer(args[1]);
				if (cp == null) {
					sender.sendMessage("§cUnknown player.");
					return false;
				}
				JukeBox.getInstance().datas.getDatas(cp).nextSong();
				sender.sendMessage("§aNext song for " + cp.getName());
			}
			break;
			
		case "setdefaultplaylist":
			if (!sender.hasPermission("music.admin.setdefaultplaylist")) {
				sender.sendMessage(Lang.NOPERM);
				return true;
			}
			handleSetDefaultPlaylist(sender, args);
			break;
			
		case "resetallplaylists":
			if (!sender.hasPermission("music.admin.resetallplaylists")) {
				sender.sendMessage(Lang.NOPERM);
				return true;
			}
			handleResetAllPlaylists(sender);
			break;
			
		case "songs":
			if (args.length < 2){
				sender.sendMessage(Lang.INCORRECT_SYNTAX);
				return false;
			}
			// ... existing code ...
			break;
		
		default:
			sender.sendMessage(Lang.AVAILABLE_COMMANDS + " <reload|player|play|playlist|stop|toggle|setitem|download|shuffle|particles|join|random|volume|loop|next|setdefaultplaylist|resetallplaylists|songs> ...");
			break;
		
		}
		
		return false;
	}
	
	private String play(String[] args){
		Player cp = Bukkit.getPlayer(args[1]);
		if (cp == null) return "§cUnknown player.";
		if (JukeBox.worlds && !JukeBox.worldsEnabled.contains(cp.getWorld().getName())) return "§cMusic isn't enabled in the world the player is into.";
		Song song;
		try{
			int id = Integer.parseInt(args[2]);
			try{
				song = JukeBox.getSongs().get(id);
			}catch (IndexOutOfBoundsException ex){
				return "§cError on §l" + id + " §r§c(inexistant)";
			}
		}catch (NumberFormatException ex){
			String fileName = args[2];
			for (int i = 3; i < args.length; i++){
				fileName = fileName + args[i] + (i == args.length-1 ? "" : " ");
			}
			song = JukeBox.getSongByFile(fileName);
			if (song == null) return Lang.INVALID_NUMBER;
		}
		PlayerData pdata = JukeBox.getInstance().datas.getDatas(cp);
		pdata.setPlaylist(Playlists.PLAYLIST, false);
		pdata.playSong(song);
		pdata.songPlayer.adminPlayed = true;
		return "";
	}
	
	private String playlist(String[] args) {
		Player cp = Bukkit.getPlayer(args[1]);
		if (cp == null) return "§cUnknown player.";
		if (JukeBox.worlds && !JukeBox.worldsEnabled.contains(cp.getWorld().getName())) return "§cMusic isn't enabled in the world the player is into.";
		try {
			Playlists list = Playlists.valueOf(args[2].toUpperCase());
			PlayerData pdata = JukeBox.getInstance().datas.getDatas(cp);
			pdata.setPlaylist(list, true);
			return "";
		}catch (IllegalArgumentException ex) {
			return "§cUnknown playlist: " + args[2];
		}
	}
	
	private String stop(Player cp){
		PlayerData pdata = JukeBox.getInstance().datas.getDatas(cp);
		pdata.stopPlaying(true);
		return Lang.PLAYER_MUSIC_STOPPED + cp.getName();
	}
	
	private void toggle(Player cp){
		PlayerData pdata = JukeBox.getInstance().datas.getDatas(cp);
		pdata.togglePlaying();
	}
	
	private String shuffle(Player cp){
		PlayerData pdata = JukeBox.getInstance().datas.getDatas(cp);
		return "§aShuffle: " + pdata.setShuffle(!pdata.isShuffle());
	}
	
	private String join(Player cp){
		PlayerData pdata = JukeBox.getInstance().datas.getDatas(cp);
		return "§aJoin: " + pdata.setJoinMusic(!pdata.hasJoinMusic());
	}
	
	private String particles(Player cp){
		PlayerData pdata = JukeBox.getInstance().datas.getDatas(cp);
		return "§aParticles: " + pdata.setParticles(!pdata.hasParticles());
	}
	
	private String loop(Player cp){
		PlayerData pdata = JukeBox.getInstance().datas.getDatas(cp);
		return "§aLoop: " + pdata.setRepeat(!pdata.isRepeatEnabled());
	}
	
	private String random(Player cp){
		PlayerData pdata = JukeBox.getInstance().datas.getDatas(cp);
		Song song = pdata.playRandom();
		if (song == null) return "§aShuffle: §cnothing to play";
		return "§aShuffle: " + song.getTitle();
	}
	
	private static String expandUrl(String shortenedUrl) throws IOException {
		URL url = new URL(shortenedUrl);
		// open connection
		HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
		
		// stop following browser redirect
		httpURLConnection.setInstanceFollowRedirects(false);
		
		// extract location header containing the actual destination URL
		String expandedURL = httpURLConnection.getHeaderField("Location");
		httpURLConnection.disconnect();
		
		return expandedURL == null ? shortenedUrl : expandedURL.replace(" ", "%20");
	}

	private void handleSetDefaultPlaylist(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(Lang.INCORRECT_SYNTAX + " Usage: /amusic setdefaultplaylist <list|add|remove|set|clear> [song(s)]");
			return;
		}
		String subCommand = args[1].toLowerCase();
		List<String> currentDefaultNames = new ArrayList<>(JukeBox.getDefaultFavoritesList()); // Modifiable copy
		boolean changed = false;
		switch (subCommand) {
			case "list":
				sender.sendMessage(Lang.DEFAULT_PLAYLIST_LIST_HEADER);
				if (currentDefaultNames.isEmpty()) {
					sender.sendMessage(Lang.DEFAULT_PLAYLIST_LIST_EMPTY);
				} else {
					for (String songName : currentDefaultNames) {
						sender.sendMessage(Lang.DEFAULT_PLAYLIST_LIST_ENTRY.replace("{SONG_NAME}", songName));
					}
				}
				return; // No save needed
			case "add":
				if (args.length < 3) {
					sender.sendMessage(Lang.INCORRECT_SYNTAX + " Usage: /amusic setdefaultplaylist add <InternalSongName>");
					return;
				}
				String songToAdd = args[2];
				if (JukeBox.getSongByInternalName(songToAdd) == null) {
					sender.sendMessage(Lang.DEFAULT_PLAYLIST_ADD_FAIL_INVALID.replace("{SONG_NAME}", songToAdd));
					return;
				}
				if (currentDefaultNames.contains(songToAdd)) {
					sender.sendMessage(Lang.DEFAULT_PLAYLIST_ADD_FAIL_EXISTS.replace("{SONG_NAME}", songToAdd));
					return;
				}
				currentDefaultNames.add(songToAdd);
				sender.sendMessage(Lang.DEFAULT_PLAYLIST_ADD_SUCCESS.replace("{SONG_NAME}", songToAdd));
				changed = true;
				break;
			case "remove":
				if (args.length < 3) {
					sender.sendMessage(Lang.INCORRECT_SYNTAX + " Usage: /amusic setdefaultplaylist remove <InternalSongName>");
					return;
				}
				String songToRemove = args[2];
				if (!currentDefaultNames.contains(songToRemove)) {
					sender.sendMessage(Lang.DEFAULT_PLAYLIST_REMOVE_FAIL_NOT_FOUND.replace("{SONG_NAME}", songToRemove));
					return;
				}
				currentDefaultNames.remove(songToRemove);
				sender.sendMessage(Lang.DEFAULT_PLAYLIST_REMOVE_SUCCESS.replace("{SONG_NAME}", songToRemove));
				changed = true;
				break;
			case "set":
				List<String> songsToSet = new ArrayList<>();
				List<String> invalidSongs = new ArrayList<>();
				if (args.length > 2) {
					for (int i = 2; i < args.length; i++) {
						String songName = args[i];
						if (JukeBox.getSongByInternalName(songName) != null) {
							songsToSet.add(songName);
						} else {
							invalidSongs.add(songName);
						}
					}
				}
				currentDefaultNames = songsToSet; // Replace the list
				sender.sendMessage(Lang.DEFAULT_PLAYLIST_SET_SUCCESS.replace("{COUNT}", String.valueOf(currentDefaultNames.size())));
				if (!invalidSongs.isEmpty()) {
					sender.sendMessage(Lang.DEFAULT_PLAYLIST_SET_FAIL_INVALID.replace("{INVALID_SONGS}", String.join(", ", invalidSongs)));
				}
				changed = true;
				break;
			case "clear":
				currentDefaultNames.clear();
				sender.sendMessage(Lang.DEFAULT_PLAYLIST_CLEAR_SUCCESS);
				changed = true;
				break;
			default:
				sender.sendMessage(Lang.INCORRECT_SYNTAX + " Unknown sub-command: " + subCommand);
				return;
		}
		if (changed) {
			JukeBox.updateDefaultFavoritesList(currentDefaultNames); // Update the list in JukeBox
			if (!JukeBox.getInstance().saveAndUpdateDefaultPlaylist()) {
				sender.sendMessage(Lang.DEFAULT_PLAYLIST_SAVE_ERROR);
			}
		}
	}

	private void handleResetAllPlaylists(CommandSender sender) {
		Playlist defaultPlaylist = JukeBox.getDefaultPlaylist();
		if (defaultPlaylist == null || defaultPlaylist.getSongList().isEmpty()) {
			sender.sendMessage(Lang.RESET_ALL_FAIL_EMPTY);
			return;
		}
		List<Song> defaultSongsCopy = new ArrayList<>(defaultPlaylist.getSongList());
		if (defaultSongsCopy.isEmpty()) { // Extra check
			sender.sendMessage(Lang.RESET_ALL_FAIL_EMPTY);
			return;
		}
		int resetCount = 0;
		for (Player player : Bukkit.getOnlinePlayers()) {
			PlayerData pdata = JukeBox.getInstance().datas.getDatas(player);
			if (pdata != null) {
				// Create a new copy for each player
				Playlist newFavorites = new Playlist(new ArrayList<>(defaultSongsCopy).toArray(new Song[0]));
				pdata.setFavorites(newFavorites.getSongList().toArray(new Song[0])); // Use setFavorites method

				// Stop current playback if it was favorites
				if (pdata.getPlaylistType() == Playlists.FAVORITES && pdata.songPlayer != null) {
					pdata.stopPlaying(false);
				}
				JukeBox.sendMessage(player, Lang.PLAYLIST_RESET_NOTIFICATION);
				resetCount++;
			}
		}
		sender.sendMessage(Lang.RESET_ALL_SUCCESS.replace("{COUNT}", String.valueOf(resetCount)));
	}

}
