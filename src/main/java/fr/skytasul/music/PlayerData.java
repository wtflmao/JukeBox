package fr.skytasul.music;

import com.xxmicloxx.NoteBlockAPI.NoteBlockAPI;
import com.xxmicloxx.NoteBlockAPI.event.SongDestroyingEvent;
import com.xxmicloxx.NoteBlockAPI.event.SongLoopEvent;
import com.xxmicloxx.NoteBlockAPI.event.SongNextEvent;
import com.xxmicloxx.NoteBlockAPI.event.SongEndEvent;
import com.xxmicloxx.NoteBlockAPI.model.FadeType;
import com.xxmicloxx.NoteBlockAPI.model.Playlist;
import com.xxmicloxx.NoteBlockAPI.model.RepeatMode;
import com.xxmicloxx.NoteBlockAPI.model.Song;
import fr.skytasul.music.utils.CustomSongPlayer;
import fr.skytasul.music.utils.Lang;
import fr.skytasul.music.utils.Playlists;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;

public class PlayerData implements Listener{

	boolean created = false;

	private UUID id;
	private boolean join = false;
	private boolean shuffle = false;
	private int volume = 100;
	private boolean particles = true;
	private boolean repeat = false;

	private boolean favoritesRemoved = false;
	private Playlist favorites;
	private Playlists listening = Playlists.PLAYLIST;

	public CustomSongPlayer songPlayer;
	private Player p;

	private List<Integer> randomPlaylist = new ArrayList<>();
	JukeBoxInventory linked = null;

	// Added: Fields and constants for smart random play feature
	private static final long RECENTLY_PLAYED_EXPIRY_MS = 30 * 60 * 1000; // 30 minutes
	private static final double RECENTLY_PLAYED_FULL_THRESHOLD = 0.85; // 85%
	private Map<String, Map<Song, Long>> recentlyPlayedTracker = new HashMap<>();

	// Added: Fields for smart random continuous playback session management
	private List<Song> currentSmartRandomSongList = null;
	private String currentSmartRandomPlaylistName = null;
	private boolean isSmartRandomSessionActive = false;
	private boolean currentUserWantsRepeatAllForSmartRandom = false;

	PlayerData(UUID id) {
		this.id = id;
		Bukkit.getPluginManager().registerEvents(this, JukeBox.getInstance());
	}

	private PlayerData(UUID id, PlayerData defaults){
		this(id);
		this.created = true;
		setJoinMusic(defaults.hasJoinMusic());
		setShuffle(defaults.isShuffle());
		setVolume(defaults.getVolume());
		setParticles(defaults.hasParticles());
		setRepeat(defaults.isRepeatEnabled());

		// Added: Set default favorites for new players
		Playlist globalDefaultPlaylist = JukeBox.getDefaultPlaylist();
		if (this.created && globalDefaultPlaylist != null && !globalDefaultPlaylist.getSongList().isEmpty()) {
			List<Song> defaultSongsCopy = new ArrayList<>(globalDefaultPlaylist.getSongList());
			if (!defaultSongsCopy.isEmpty()) {
				this.favorites = new Playlist(defaultSongsCopy.toArray(new Song[0]));
			}
		}
	}

	@EventHandler
	public void onSongDestroy(SongDestroyingEvent e) {
		if (e.getSongPlayer() == songPlayer) {
			if (linked != null) linked.playingStopped();
			
			// If a smart random session was active and hasn't been properly terminated 
			// (e.g. onSongEnd didn't start a new song, or stopPlaying wasn't called prior to destruction),
			// ensure flags are cleared.
			if (this.isSmartRandomSessionActive) {
				JukeBox.getInstance().getLogger().info("SongDestroyingEvent: Smart random session was still marked active for player " + getID() + ". Clearing flags.");
				clearSmartRandomSessionFlags();
			}
			this.songPlayer = null; // Now set to null

			if (favoritesRemoved){
				favoritesRemoved = false;
				if (listening == Playlists.FAVORITES && favorites != null){
					playList(favorites);
				}
			}
		}
	}

	@EventHandler
	public void onLoop(SongLoopEvent e){
		if (e.getSongPlayer() == songPlayer){
			if ((listening == Playlists.FAVORITES && favorites == null) || (listening == Playlists.PLAYLIST && !(shuffle || repeat))) {
				songPlayer.destroy();
				return;
			}
			playSong(true);
		}
	}

	@EventHandler
	public void onSongEnd(SongEndEvent e) {
		if (e.getSongPlayer() != this.songPlayer || !this.isSmartRandomSessionActive) {
			// JukeBox.getInstance().getLogger().info("[DEBUG] onSongEnd: Not for current smart session or not active.");
			return;
		}

		// This song has ended, and it was part of an active smart random session.
		// SongPlayer is likely auto-destroying due to setAutoDestroy(true) and RepeatMode.NO on a single-song playlist.
		// We need to decide the next song and start a new player for it.
		// Note: The old songPlayer instance referenced by 'this.songPlayer' will be invalid after this event if autoDestroy was true.

		Song nextSong = getSmartRandomSongFromDirectory(this.currentSmartRandomSongList, this.currentSmartRandomPlaylistName);

		if (nextSong != null) {
			// JukeBox.getInstance().getLogger().info("[DEBUG] onSongEnd: Found next smart song: " + JukeBox.getInternal(nextSong));
			playNextSmartRandomSong(nextSong, false); // false because this is not an admin play
		} else {
			// No next song available from smart random logic
			// JukeBox.getInstance().getLogger().info("[DEBUG] onSongEnd: No next smart song initially. Checking repeat...");
			if (this.currentUserWantsRepeatAllForSmartRandom) {
				JukeBox.getInstance().getLogger().info("Smart random playlist '" + this.currentSmartRandomPlaylistName + "' for player " + getID() + " finished. Attempting to repeat (clearing recently played).");
				Map<Song, Long> specificTracker = recentlyPlayedTracker.get(this.currentSmartRandomPlaylistName);
				if (specificTracker != null) {
					specificTracker.clear();
				}
				nextSong = getSmartRandomSongFromDirectory(this.currentSmartRandomSongList, this.currentSmartRandomPlaylistName);
				if (nextSong != null) {
					// JukeBox.getInstance().getLogger().info("[DEBUG] onSongEnd: Found next smart song after repeat: " + JukeBox.getInternal(nextSong));
					playNextSmartRandomSong(nextSong, false); // false because this is not an admin play
				} else {
					JukeBox.getInstance().getLogger().info("Smart random playlist '" + this.currentSmartRandomPlaylistName + "' for player " + getID() + " still has no songs after attempting repeat. Session ending.");
					// Session will be marked inactive by onSongDestroy or if stopPlaying was called before.
					// No new player started, so session effectively ends.
					// Explicitly clear flags here if onSongDestroy might not catch it due to player already being null by then.
					clearSmartRandomSessionFlags();
				}
			} else {
				JukeBox.getInstance().getLogger().info("Smart random playlist '" + this.currentSmartRandomPlaylistName + "' for player " + getID() + " finished and repeat is off. Session ending.");
				// Session will be marked inactive by onSongDestroy or if stopPlaying was called before.
				// No new player started, so session effectively ends.
				clearSmartRandomSessionFlags();
			}
		}
	}

	@EventHandler
	public void onSongNext(SongNextEvent e){
		// This event is for when a SongPlayer with a multi-song playlist goes to the next song internally.
		// Since our smart random plays single-song playlists and restarts player via onSongEnd,
		// this handler should primarily deal with other playback modes (e.g. favorites if it were a list, radio).
		if (e.getSongPlayer() != this.songPlayer) return;

		if (this.isSmartRandomSessionActive) {
			// This should ideally not be hit if smart random is active because we use single song playlists
			// and onSongEnd handles the transition. If it is hit, something is unexpected.
			JukeBox.getInstance().getLogger().warning("[JukeBox PlayerData] onSongNext triggered for player " + getID() + " while isSmartRandomSessionActive=true. This is unexpected. Stopping session.");
			stopPlaying(false);
			clearSmartRandomSessionFlags();
			return;
		}

		// Default behavior for non-smart-random sessions (e.g. favorites, radio, or direct playSong calls)
		// This was the old logic from onSongNext, ensure it's still valid for other modes.
		if (listening == Playlists.PLAYLIST && !shuffle){ 
			stopPlaying(false); // If it's a non-shuffled, non-smart "PLAYLIST" type, it stops after one song.
		} else playSong(true); // playSong(true) is the old logic for next song in other modes (e.g. shuffled favorites)
	}

	@EventHandler
	public void onLeave(PlayerQuitEvent e){
		Player p = e.getPlayer();
		if (!p.getUniqueId().equals(id)) return;
		if (songPlayer != null) songPlayer.setPlaying(false);
		p = null;
	}

	public void playList(Playlist list){
		if (listening == Playlists.RADIO){
			JukeBox.sendMessage(getPlayer(), Lang.UNAVAILABLE_RADIO);
			return;
		}
		if (songPlayer != null) stopPlaying(false);
		if (list == null) return;

		songPlayer = new CustomSongPlayer(list);
		songPlayer.setParticlesEnabled(particles);
		songPlayer.getFadeIn().setFadeDuration(JukeBox.fadeInDuration);
		if (JukeBox.fadeInDuration != 0) songPlayer.getFadeIn().setType(FadeType.LINEAR);
		songPlayer.getFadeOut().setFadeDuration(JukeBox.fadeOutDuration);
		if (JukeBox.fadeOutDuration != 0) songPlayer.getFadeOut().setType(FadeType.LINEAR);
		songPlayer.setAutoDestroy(true);
		songPlayer.addPlayer(getPlayer());
		songPlayer.setPlaying(true);
		songPlayer.setRandom(shuffle);
		songPlayer.setRepeatMode(repeat ? RepeatMode.ONE : RepeatMode.ALL);

		playSong(false);

		if (JukeBox.getInstance().stopVanillaMusic != null) JukeBox.getInstance().stopVanillaMusic.accept(p);
		if (linked != null) linked.playingStarted();
	}

	public boolean playSong(Song song){
		if (listening == Playlists.RADIO){
			JukeBox.sendMessage(getPlayer(), Lang.UNAVAILABLE_RADIO);
			return false;
		}
		if (songPlayer != null) stopPlaying(false);
		if (song == null) return false;
		
		// 创建单曲播放列表直接播放选择的歌曲，而不是通过addSong添加到现有播放列表
		Playlist singleSongPlaylist = new Playlist(song);
		playList(singleSongPlaylist);
		return true;
	}

	public boolean addSong(Song song, boolean playIndex) {
		Playlist toPlay = null;
		switch (listening){
		case FAVORITES:
			if (favorites == null){
				favorites = new Playlist(song);
			}else {
				if (favorites.contains(song)) break;
				if (playIndex && songPlayer != null){
					favorites.insert(songPlayer.getPlayedSongIndex() + 1, song);
					finishPlaying();
				}else favorites.add(song);
			}
			toPlay = favorites;
			break;
		case PLAYLIST:
			randomPlaylist.add(JukeBox.getPlaylist().getIndex(song));
			if (playIndex) finishPlaying();
			toPlay = JukeBox.getPlaylist();
			break;
		case RADIO:
			return false;
		}
		if (songPlayer == null && getPlayer() != null){
			playList(toPlay);
			return listening == Playlists.FAVORITES;
		}
		return true;
	}

	public void removeSong(Song song){
		switch (listening){
		case FAVORITES:
			if (favorites.getCount() == 1){
				favorites = null;
				favoritesRemoved = true;
				songPlayer.setRepeatMode(repeat ? RepeatMode.ONE : RepeatMode.NO);
			}else favorites.remove(song);
			break;
		case PLAYLIST:
			randomPlaylist.remove((Integer) JukeBox.getPlaylist().getIndex(song));
			break;
		case RADIO:
			break;
		}
	}

	public boolean isInPlaylist(Song song){
		switch (listening){
		case FAVORITES:
			if (favorites != null) return favorites.contains(song);
			break;
		case PLAYLIST:
			return randomPlaylist.contains(JukeBox.getPlaylist().getIndex(song));
		case RADIO:
			return false;
		}
		return false;
	}

	public void clearPlaylist(){
		switch (listening){
		case FAVORITES:
			if (favorites == null) break;
			for (int i = 0; i < favorites.getCount() - 1; i++){
				favorites.remove(favorites.get(0));
			}
			removeSong(favorites.get(0));
			break;
		case PLAYLIST:
			randomPlaylist.clear();
			break;
		case RADIO:
			break;
		}
	}

	public Song playRandom() {
		if (JukeBox.getSongs().isEmpty()) return null;
		setPlaylist(Playlists.PLAYLIST, false);
		Song song = JukeBox.randomSong();

		Player playerForLog = getPlayer(); 
		if (playerForLog != null && song != null) { 
		    String songIdentifier = JukeBox.getInternal(song); 
		    if (songIdentifier == null || songIdentifier.isEmpty()) songIdentifier = song.getPath().getName(); 
		    JukeBox.getInstance().getLogger().info("RandomPlay: Playing " + songIdentifier + " for " + playerForLog.getName()); 
		}
		// For playRandom(), ensure any smart session is terminated.
		clearSmartRandomSessionFlags(); 
		playSong(song);
		return song;
	}

	public void stopPlaying(boolean msg) {
		// Clear smart random session flags BEFORE destroying the song player
		clearSmartRandomSessionFlags();

		if (songPlayer != null){
			songPlayer.setPlaying(false);
			songPlayer.destroy();
			songPlayer = null;
			if (msg) JukeBox.sendMessage(getPlayer(), Lang.MUSIC_STOPPED);
		}else if (msg) JukeBox.sendMessage(getPlayer(), "§c§oNo music playing.");
		if (linked != null) linked.playingStopped();
	}

	public Playlists getPlaylistType(){
		return listening;
	}

	public void nextPlaylist(){
		Playlists toPlay = null;

		switch (listening) {
		case PLAYLIST:
			if (Playlists.FAVORITES.hasPermission(p)) {
				toPlay = Playlists.FAVORITES;
			}else if (Playlists.RADIO.hasPermission(p)) toPlay = Playlists.RADIO;
			break;
		case FAVORITES:
			if (JukeBox.radioEnabled && Playlists.RADIO.hasPermission(p)) {
				toPlay = Playlists.RADIO;
			}else toPlay = Playlists.PLAYLIST;
			break;
		case RADIO:
			toPlay = Playlists.PLAYLIST;
			break;
		}
		if (toPlay == null) return;
		setPlaylist(toPlay, true);
	}

	public void setPlaylist(Playlists list, boolean play){
		stopPlaying(false);
		this.listening = list;
		if (linked != null) {
			linked.playlistItem();
			Player player = getPlayer();
			if (player != null) {
				linked.setPlaylistListPage(player);
			}
		}
		if (!play || getPlayer() == null) return;
		switch (listening){
		case PLAYLIST:
			playList(JukeBox.getPlaylist());
			break;
		case FAVORITES:
			playList(favorites);
			break;
		case RADIO:
			JukeBox.radio.join(getPlayer());
			if (linked != null) linked.playingStarted();
			break;
		}
	}

	public boolean isListening() {
		return songPlayer != null || listening == Playlists.RADIO;
	}

	public boolean isPlaying() {
		return p != null && (songPlayer == null ? listening == Playlists.RADIO : songPlayer.isPlaying());
	}

	private void finishPlaying(){
		if (songPlayer == null) return;
		songPlayer.setTick((short) (songPlayer.getSong().getLength() + 1));
		if (!songPlayer.isPlaying()) songPlayer.setPlaying(true);
	}

	public void nextSong() {
		if (listening == Playlists.RADIO){
			JukeBox.sendMessage(getPlayer(), Lang.UNAVAILABLE_RADIO);
			return;
		}
		if (songPlayer == null) {
			playList(listening == Playlists.PLAYLIST ? JukeBox.getPlaylist() : favorites);
		}else {
			finishPlaying();
		}
	}

	public Song getListeningTo() {
		if (songPlayer != null) return songPlayer.getSong();
		if (getPlaylistType() == Playlists.RADIO) return JukeBox.radio.getSong();
		return null;
	}

	public String getListeningSongName() {
		Song song = getListeningTo();
		return song == null ? null : JukeBox.getSongName(song);
	}

	public void playerJoin(Player player, boolean replay){
		this.p = player;
		setVolume(volume); // to refresh the volume in NoteBlockAPI
		if (!replay) return;
		if (JukeBox.radioOnJoin){
			setPlaylist(Playlists.RADIO, true);
			return;
		}
		if (listening == Playlists.RADIO) return;
		if (songPlayer == null){
			if (hasJoinMusic()) {
				if (JukeBox.songOnJoin != null) {
					playSong(JukeBox.songOnJoin);
				}else playRandom();
			}
		}else if (!songPlayer.adminPlayed && JukeBox.autoReload) {
			songPlayer.setPlaying(true);
			JukeBox.sendMessage(getPlayer(), Lang.RELOAD_MUSIC + " (" + JukeBox.getSongName(songPlayer.getSong()) + ")");
		}
	}

	public void togglePlaying() {
		if (songPlayer != null) {
			songPlayer.setPlaying(!songPlayer.isPlaying());
		}else {
			if (listening == Playlists.RADIO) {
				if (JukeBox.radio.isListening(getPlayer())) {
					JukeBox.radio.leave(getPlayer());
				}else JukeBox.radio.join(getPlayer());
			}
		}
		if (JukeBox.getInstance().stopVanillaMusic != null && isPlaying()) JukeBox.getInstance().stopVanillaMusic.accept(p);
	}

	public void playerLeave(){
		if (!JukeBox.autoReload) stopPlaying(false);
		p = null;
	}

	private void playSong(boolean next){
		if (songPlayer == null) return;
		Song s = songPlayer.getSong();
		if (!next) s = songPlayer.getPlaylist().get(songPlayer.getPlayedSongIndex());
		if (s == null) return;
		// if (p != null && p.isOnline() && JukeBox.sendMsg && songPlayer.adminPlayed) JukeBox.sendMessage(p, Lang.MUSIC_PLAYING + " " + JukeBox.getSongName(s)); // Commented out to prevent message to target player on admin play
	}

	public UUID getID(){
		return id;
	}

	public Player getPlayer() {
		return p;
	}

	public boolean hasJoinMusic(){
		return join;
	}

	public boolean setJoinMusic(boolean join){
		this.join = join;
		if (linked != null) linked.joinItem();
		return join;
	}

	public boolean isShuffle(){
		return shuffle;
	}

	public boolean setShuffle(boolean shuffle){
		this.shuffle = shuffle;
		if (songPlayer != null) songPlayer.setRandom(true);
		if (linked != null) linked.shuffleItem();
		return shuffle;
	}

	public int getVolume(){
		return volume;
	}

	public int setVolume(int volume){
		if (id != null) NoteBlockAPI.setPlayerVolume(id, (byte) volume);
		this.volume = volume;
		if (linked != null) linked.volumeItem();
		return volume;
	}

	public boolean hasParticles(){
		return particles;
	}

	public boolean setParticles(boolean particles){
		if (songPlayer != null) songPlayer.setParticlesEnabled(particles);
		this.particles = particles;
		if (linked != null) linked.particlesItem();
		return particles;
	}

	public boolean isRepeatEnabled(){
		return repeat;
	}

	public boolean setRepeat(boolean repeat){
		this.repeat = repeat;
		if (songPlayer != null) songPlayer.setRepeatMode(repeat ? RepeatMode.ONE : (listening == Playlists.FAVORITES && favorites == null ? RepeatMode.NO : RepeatMode.ALL));
		if (linked != null) linked.repeatItem();
		return repeat;
	}

	public Playlist getFavorites() {
		return favorites;
	}

	public void setFavorites(Song... songs) {
		if (songs == null || songs.length == 0) {
			this.favorites = null;
		} else {
			List<Song> validSongs = Arrays.stream(songs).filter(Objects::nonNull).collect(Collectors.toList());
			if (!validSongs.isEmpty()) {
				this.favorites = new Playlist(validSongs.toArray(new Song[0]));
			} else {
				this.favorites = null;
			}
		}
	}

	public boolean isDefault(PlayerData base){
		if (base.hasJoinMusic() != hasJoinMusic()) if (!JukeBox.autoJoin) return false;
		if (base.isShuffle() != isShuffle()) return false;
		if (base.getVolume() != getVolume()) return false;
		if (base.hasParticles() != hasParticles()) return false;
		if (base.isRepeatEnabled() != isRepeatEnabled()) return false;
		return true;
	}

	public Map<String, Object> serialize(){
		Map<String, Object> map = new HashMap<>();
		map.put("id", id.toString());

		map.put("join", hasJoinMusic());
		map.put("shuffle", isShuffle());
		map.put("volume", getVolume());
		map.put("particles", hasParticles());
		map.put("repeat", isRepeatEnabled());
		map.put("playlist", listening.name());

		if (favorites != null) {
			List<String> list = new ArrayList<>();
			for (Song song : favorites.getSongList()) list.add(JukeBox.getInternal(song));
			if (!list.isEmpty()) map.put("favorites", list);
		}

		return map;
	}

	static PlayerData create(UUID id){
		PlayerData pdata = new PlayerData(id, JukeBox.defaultPlayer);
		if (JukeBox.autoJoin) pdata.setJoinMusic(true);
		return pdata;
	}

	public static PlayerData deserialize(Map<String, Object> map, Map<String, Song> songsName) {
		PlayerData pdata = new PlayerData(map.containsKey("id") ? UUID.fromString((String) map.get("id")) : null);

		pdata.setJoinMusic((boolean) map.get("join"));
		pdata.setShuffle((boolean) map.get("shuffle"));
		if (map.containsKey("volume")) pdata.setVolume((int) map.get("volume"));
		if (map.containsKey("particles")) pdata.setParticles((boolean) map.get("particles"));
		if (map.containsKey("repeat")) pdata.setRepeat((boolean) map.get("repeat"));

		if (map.containsKey("favorites")) {
			pdata.setPlaylist(Playlists.FAVORITES, false);
			for (String s : (List<String>) map.get("favorites")) {
				Song song = songsName.get(s);
				if (song == null) {
					JukeBox.getInstance().getLogger().warning("Unknown song \"" + s + "\" for favorite playlist of " + pdata.getID().toString());
				}else pdata.addSong(song, false);
			}
			pdata.setPlaylist(Playlists.PLAYLIST, false);
		}
		if (map.containsKey("playlist")) {
			pdata.setPlaylist(Playlists.valueOf((String) map.get("playlist")), false);
		}

		if (JukeBox.autoJoin) pdata.setJoinMusic(true);

		return pdata;
	}

	// Modified: Updated to use smart random selection and continuous play via SongNextEvent
	public void playDirectoryPlaylist(List<Song> songsToPlay, String directoryName) {
		stopPlaying(false); // Stop any current music

		if (songsToPlay == null || songsToPlay.isEmpty()) {
			JukeBox.sendMessage(getPlayer(), Lang.NO_SONG_AVAILABLE_IN_PLAYLIST.replace("{PLAYLIST}", directoryName));
			return;
		}

		Song firstSong = getSmartRandomSongFromDirectory(new ArrayList<>(songsToPlay), directoryName); // Pass a copy

		if (firstSong == null) {
			JukeBox.sendMessage(getPlayer(), Lang.NO_SONG_AVAILABLE_IN_PLAYLIST.replace("{PLAYLIST}", directoryName));
			return;
		}

		Playlist initialPlaylist = new Playlist(firstSong);
		this.listening = Playlists.PLAYLIST; // Keep this to signify it's not favorites/radio for other logic if any

		songPlayer = new CustomSongPlayer(initialPlaylist);
		songPlayer.setParticlesEnabled(particles);
		songPlayer.getFadeIn().setFadeDuration(JukeBox.fadeInDuration);
		if (JukeBox.fadeInDuration != 0) songPlayer.getFadeIn().setType(FadeType.LINEAR);
		songPlayer.getFadeOut().setFadeDuration(JukeBox.fadeOutDuration);
		if (JukeBox.fadeOutDuration != 0) songPlayer.getFadeOut().setType(FadeType.LINEAR);
		songPlayer.setAutoDestroy(true);
		songPlayer.addPlayer(getPlayer());
		songPlayer.setRepeatMode(RepeatMode.NO); // We handle next song and repeat via SongNextEvent
		// songPlayer.setRandom(false); // No longer needed, we are not passing the full list here initially

		// Set state for smart random session
		this.currentSmartRandomSongList = new ArrayList<>(songsToPlay);
		this.currentSmartRandomPlaylistName = directoryName;
		this.isSmartRandomSessionActive = true;
		this.currentUserWantsRepeatAllForSmartRandom = this.repeat; // Player's 'repeat' flag now means repeat entire smart random list

		songPlayer.setPlaying(true);

		if (JukeBox.getInstance().stopVanillaMusic != null) JukeBox.getInstance().stopVanillaMusic.accept(p);

		Player playerForLog = getPlayer(); 
		if (playerForLog != null && firstSong != null) { 
		    String songIdentifier = JukeBox.getInternal(firstSong); 
		    if (songIdentifier == null || songIdentifier.isEmpty()) songIdentifier = firstSong.getPath().getName(); 
		    JukeBox.getInstance().getLogger().info("RandomPlay: Playing " + songIdentifier + " for " + playerForLog.getName() + " (Smart Random Start)");
		}

		String messageFormat = Lang.NOW_PLAYING_SONG_FROM;
		Player player = getPlayer(); 
		if (messageFormat == null) {
		    JukeBox.getInstance().getLogger().severe("CRITICAL: Lang.NOW_PLAYING_SONG_FROM is null when trying to send play message for playlist '" + directoryName + "' to player " + (player != null ? player.getName() : "UNKNOWN_PLAYER") + ". This indicates a problem with language file loading or initialization. Please check plugin startup logs and language files (e.g., en.yml, zh_CN.yml). Falling back to a default message.");
		    // Attempt to log which language is configured to help debug
		    String configuredLang = "UNKNOWN (config not accessible)";
		    JukeBox mainPlugin = JukeBox.getInstance();
		    if (mainPlugin != null && mainPlugin.getConfig() != null) {
		        configuredLang = mainPlugin.getConfig().getString("lang", "en (default)");
		    }
		    JukeBox.getInstance().getLogger().severe("Configured language: " + configuredLang);

		    // Also log the state of a few other Lang keys to see if they are also null
		    // Adding null checks for Lang fields themselves before accessing them for logging, just in case.
		    String stopKeyVal = (Lang.STOP == null) ? "null" : Lang.STOP;
		    String randomMusicKeyVal = (Lang.RANDOM_MUSIC == null) ? "null" : Lang.RANDOM_MUSIC;
		    String nextPageKeyVal = (Lang.NEXT_PAGE == null) ? "null" : Lang.NEXT_PAGE;
		    JukeBox.getInstance().getLogger().severe("Debug Lang Keys: STOP = " + stopKeyVal + ", RANDOM_MUSIC = " + randomMusicKeyVal + ", NEXT_PAGE = " + nextPageKeyVal);

		    messageFormat = "&6Now playing: &e{SONG} &6from playlist &e{PLAYLIST}&6. (LANG_ERR: NOW_PLAYING_SONG_FROM_MISSING)"; // Default fallback with error indicator
		}

		String message = ChatColor.translateAlternateColorCodes('&', messageFormat)
		        .replace("{SONG}", JukeBox.getSongName(firstSong))
		        .replace("{PLAYLIST}", directoryName);
		if (player != null) { // Check if player is still valid
		    JukeBox.sendMessage(player, message);
		} else {
		    JukeBox.getInstance().getLogger().warning("Player object was null when trying to send play message for playlist '" + directoryName + "'. Message was: " + message);
		}

		if (linked != null) linked.playingStarted();
	}

	// Added: Helper method for smart random song selection
	private Song getSmartRandomSongFromDirectory(List<Song> songsInDirectory, String directoryName) {
		if (songsInDirectory == null || songsInDirectory.isEmpty()) {
			return null;
		}

		// Get or initialize the tracker for this directory
		Map<Song, Long> specificPlaylistTracker = recentlyPlayedTracker.computeIfAbsent(directoryName, k -> new HashMap<>());
		long currentTime = System.currentTimeMillis();

		// Clean up expired entries
		specificPlaylistTracker.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > RECENTLY_PLAYED_EXPIRY_MS);

		// Check if tracker is too full and needs reset
		if (specificPlaylistTracker.size() / (double) songsInDirectory.size() >= RECENTLY_PLAYED_FULL_THRESHOLD) {
			specificPlaylistTracker.clear();
		}

		// Create list of available songs (not in tracker)
		List<Song> availableSongs = new ArrayList<>();
		for (Song song : songsInDirectory) {
			if (!specificPlaylistTracker.containsKey(song)) {
				availableSongs.add(song);
			}
		}

		// If no songs available, clear tracker and use all songs
		if (availableSongs.isEmpty()) {
			specificPlaylistTracker.clear();
			availableSongs.addAll(songsInDirectory);
		}

		// Select a random song
		if (!availableSongs.isEmpty()) {
			Song selectedSong = availableSongs.get(new Random().nextInt(availableSongs.size()));
			specificPlaylistTracker.put(selectedSong, currentTime);
			return selectedSong;
		}

		return null;
	}

	// Added: Method for admin-triggered random play, now supports smart continuous play
	// Modified: Now plays only a single smart random song and does not start a continuous session.
	public boolean playRandomSongFromDirectoryForAdmin(List<Song> songsInDirectory, String directoryName) {
		stopPlaying(false); // Stop any current music

		if (songsInDirectory == null || songsInDirectory.isEmpty()) {
			return false; // Admin command typically gives feedback via CommandAdmin
		}

		// Ensure any previous smart session is cleared before starting a new admin play, 
		// even if it's a single song, to prevent unexpected interactions if player state was somehow corrupted.
		clearSmartRandomSessionFlags();

		Song firstSong = getSmartRandomSongFromDirectory(new ArrayList<>(songsInDirectory), directoryName); // Pass a copy

		if (firstSong == null) {
			return false; // No song available
		}

		Playlist initialPlaylist = new Playlist(firstSong);
		this.listening = Playlists.PLAYLIST;

		songPlayer = new CustomSongPlayer(initialPlaylist);
		songPlayer.setParticlesEnabled(particles); 
		songPlayer.getFadeIn().setFadeDuration(JukeBox.fadeInDuration);
		if (JukeBox.fadeInDuration != 0) songPlayer.getFadeIn().setType(FadeType.LINEAR);
		songPlayer.getFadeOut().setFadeDuration(JukeBox.fadeOutDuration);
		if (JukeBox.fadeOutDuration != 0) songPlayer.getFadeOut().setType(FadeType.LINEAR);
		songPlayer.setAutoDestroy(true); // Ensures player stops and cleans up after one song
		songPlayer.addPlayer(getPlayer());
		songPlayer.setRepeatMode(RepeatMode.NO); // Single play, no repeat
		songPlayer.adminPlayed = true;

		// DO NOT set session flags for admin single play:
		// this.currentSmartRandomSongList = new ArrayList<>(songsInDirectory);
		// this.currentSmartRandomPlaylistName = directoryName;
		// this.isSmartRandomSessionActive = true;
		// this.currentUserWantsRepeatAllForSmartRandom = false;

		songPlayer.setPlaying(true);

		if (JukeBox.getInstance().stopVanillaMusic != null) JukeBox.getInstance().stopVanillaMusic.accept(p);

		Player playerForLog = getPlayer();
		if (playerForLog != null && firstSong != null) {
		    String songIdentifier = JukeBox.getInternal(firstSong);
		    if (songIdentifier == null || songIdentifier.isEmpty()) songIdentifier = firstSong.getPath().getName();
		    JukeBox.getInstance().getLogger().info("RandomPlay: Admin playing " + songIdentifier + " for " + playerForLog.getName() + " (Smart Random Single from playlist " + directoryName + ")");
		}

		String adminMessageFormat = Lang.ADMIN_PLAYING_SONG_FROM;
		if (adminMessageFormat != null) {
		    JukeBox.sendMessage(getPlayer(), adminMessageFormat.replace("{PLAYLIST}", directoryName));
		} else {
		    JukeBox.getInstance().getLogger().warning("Lang.ADMIN_PLAYING_SONG_FROM is null. Cannot send message to player.");
		}

		if (linked != null) linked.playingStarted();
		return true;
	}

	private void playNextSmartRandomSong(Song songToPlay, boolean isAdminPlayed) {
		Playlist nextPlaylist = new Playlist(songToPlay);
		// this.listening should still be Playlists.PLAYLIST from the initial call

		// Important: a new SongPlayer instance is created.
		// The old one is expected to have been (or about to be) destroyed due to autoDestroy=true.
		CustomSongPlayer nextSongPlayer = new CustomSongPlayer(nextPlaylist);
		nextSongPlayer.setParticlesEnabled(particles);
		nextSongPlayer.getFadeIn().setFadeDuration(JukeBox.fadeInDuration);
		if (JukeBox.fadeInDuration != 0) nextSongPlayer.getFadeIn().setType(FadeType.LINEAR);
		nextSongPlayer.getFadeOut().setFadeDuration(JukeBox.fadeOutDuration);
		if (JukeBox.fadeOutDuration != 0) nextSongPlayer.getFadeOut().setType(FadeType.LINEAR);
		nextSongPlayer.setAutoDestroy(true);
		Player currentPlayer = getPlayer();
		if (currentPlayer != null) {
			nextSongPlayer.addPlayer(currentPlayer);
		} else {
			JukeBox.getInstance().getLogger().warning("Player " + getID() + " was null when trying to play next smart random song. Aborting next song.");
			// If player is null, we can't play. Session should naturally end or be cleaned up.
			clearSmartRandomSessionFlags();
			return;
		}
		nextSongPlayer.setRepeatMode(RepeatMode.NO); // Each song in smart random is a new event, no individual repeat.
		nextSongPlayer.adminPlayed = isAdminPlayed; // Carry over admin status if applicable
		
		this.songPlayer = nextSongPlayer; // Update the main songPlayer reference
		this.songPlayer.setPlaying(true);

		if (JukeBox.getInstance().stopVanillaMusic != null && currentPlayer != null) JukeBox.getInstance().stopVanillaMusic.accept(currentPlayer);

		Player playerForLog = getPlayer();
		if (playerForLog != null) {
			String songIdentifier = JukeBox.getInternal(songToPlay);
			if (songIdentifier == null || songIdentifier.isEmpty()) songIdentifier = songToPlay.getPath().getName();
			JukeBox.getInstance().getLogger().info("RandomPlay: Playing " + (isAdminPlayed ? "(admin) " : "") + "next (smart random) " + songIdentifier + " for " + playerForLog.getName() + " from playlist " + this.currentSmartRandomPlaylistName);
		}
		// No player message for subsequent songs in smart random by default, only for the first one.
		// Or if admin played, the initial admin message was sent.

		if (linked != null) linked.playingStarted(); // Update GUI if linked
	}

	private void clearSmartRandomSessionFlags() {
		this.isSmartRandomSessionActive = false;
		this.currentSmartRandomSongList = null;
		this.currentSmartRandomPlaylistName = null;
		this.currentUserWantsRepeatAllForSmartRandom = false;
		// JukeBox.getInstance().getLogger().info("[DEBUG] Smart random session flags cleared for player " + getID());
	}

}