package fr.skytasul.music;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.skytasul.music.utils.Lang;
import com.xxmicloxx.NoteBlockAPI.model.Playlist;
import com.xxmicloxx.NoteBlockAPI.model.Song;
import fr.skytasul.music.utils.Playlists;
import java.util.ArrayList;
import java.util.List;

public class CommandMusic implements CommandExecutor{

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
		if (!(sender instanceof Player)){
			sender.sendMessage(Lang.PLAYER);
			return true;
		}
		
		Player p = (Player) sender;
		PlayerData pdata = JukeBox.getInstance().datas.getDatas(p);
		if (pdata == null) {
			p.sendMessage("§cLoading player data... Please try again shortly.");
			return true;
		}

		if (args.length > 0 && args[0].equalsIgnoreCase("resetplaylist")) {
			if (!p.hasPermission("music.command.resetplaylist")) {
				JukeBox.sendMessage(p, Lang.NOPERM);
				return true;
			}
			resetPlaylist(p, pdata);
		} else {
			open(p, pdata);
		}

		return true;
	}
	
	public static void open(Player p, PlayerData pdata){
		if (JukeBox.worlds && !JukeBox.worldsEnabled.contains(p.getWorld().getName())) return;
		if (pdata.linked != null){
			JukeBoxInventory inv = pdata.linked;
			inv.setPlaylistListPage(p);
			inv.openInventory(p);
		}else new JukeBoxInventory(p, pdata);
	}

	private void resetPlaylist(Player p, PlayerData pdata) {
		Playlist defaultPlaylist = JukeBox.getDefaultPlaylist();
		if (defaultPlaylist == null || defaultPlaylist.getSongList().isEmpty()) {
			JukeBox.sendMessage(p, Lang.DEFAULT_PLAYLIST_EMPTY);
			return;
		}

		List<Song> defaultSongsCopy = new ArrayList<>(defaultPlaylist.getSongList());
		if (defaultSongsCopy.isEmpty()) {
			JukeBox.sendMessage(p, Lang.DEFAULT_PLAYLIST_EMPTY);
			return;
		}

		pdata.setFavorites(defaultSongsCopy.toArray(new Song[0]));

		if (pdata.getPlaylistType() == Playlists.FAVORITES && pdata.songPlayer != null) {
			pdata.stopPlaying(false);
		}

		JukeBox.sendMessage(p, Lang.PLAYLIST_RESET_SUCCESS);
	}

}
