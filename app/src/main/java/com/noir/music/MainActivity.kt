package com.noir.music

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF9B7BFF)
private val Dim = Color(0xFF9A9A9A)
private val Panel = Color(0xFF111111)

class MainActivity : ComponentActivity() {
    private var future: ListenableFuture<MediaController>? = null
    private var player by mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Noir(player) }
    }

    override fun onStart() {
        super.onStart()
        val f = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        // Leaving the app before the connection finishes cancels f; get() would then throw and crash the app
        f.addListener({ if (future === f) player = runCatching { f.get() }.getOrNull() }, ContextCompat.getMainExecutor(this))
        future = f
    }

    override fun onStop() {
        player = null
        future?.let(MediaController::releaseFuture) // music keeps playing in PlaybackService
        future = null
        super.onStop()
    }
}

// Screen state that survives tab switches
private object S {
    var forYou by mutableStateOf<List<Song>?>(null)
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Song>?>(emptyList())
    var open by mutableStateOf<String?>(null) // playlist shown in Library; LIKED for Liked songs
    var menu by mutableStateOf<List<Opt>?>(null)
    var ask by mutableStateOf<Ask?>(null) // text prompt dialog
    var status by mutableStateOf<String?>(null) // short message in the top bar
    var autoMood by mutableStateOf(autoMood())
    var moodMix by mutableStateOf<List<Song>?>(null)
}

private class Ask(val title: String, val hint: String, val done: (String) -> Unit)

private val appScope = MainScope() // outlives screens, so a mix keeps building if you switch tabs

private fun hour() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
private fun autoMood() = Mood.auto(hour(), Taste::moodPicks)

/** Builds a [mood] mix and plays it. With a [prompt] the mix is also saved to the library, named after it. */
private fun startMood(mood: Mood, play: (List<Song>, Int) -> Unit, prompt: String? = null, extra: String = "") {
    appScope.launch {
        S.status = "Making your ${mood.label.lowercase()} mix…"
        val songs = moodMix(mood, extra)
        if (songs.isEmpty()) { S.status = "Couldn't load songs. Check the internet and try again."; return@launch }
        Taste.pickMood(Mood.slot(hour()), mood)
        S.status = if (prompt == null) "Playing your ${mood.label.lowercase()} mix" else {
            val name = Taste.freeName("${mood.label} · ${prompt.trim().take(24)}")
            Taste.setPlaylist(name, songs)
            "Saved \"$name\" to your library"
        }
        play(songs, 0)
    }
}

private const val LIKED = "" // playlist names are never blank
private typealias Opt = Pair<String, () -> Unit>
private fun opt(label: String, action: () -> Unit): Opt = label to action
private val LocalPlayer = staticCompositionLocalOf<Player?> { null }

private class NowPlaying {
    var song by mutableStateOf<Song?>(null)
    var playing by mutableStateOf(false)
    var queue by mutableStateOf(emptyList<Song>())
    var index by mutableIntStateOf(0)
}

@Composable
private fun nowPlaying(p: Player?): NowPlaying {
    val np = remember { NowPlaying() }
    DisposableEffect(p) {
        if (p == null) return@DisposableEffect onDispose {}
        fun sync() {
            np.song = p.currentMediaItem?.toSong()
            np.playing = p.isPlaying
            np.queue = List(p.mediaItemCount) { p.getMediaItemAt(it).toSong() }
            np.index = p.currentMediaItemIndex
        }
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        sync()
        p.addListener(l)
        onDispose { p.removeListener(l) }
    }
    return np
}

@Composable
private fun Noir(player: Player?) {
    val np = nowPlaying(player)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var full by rememberSaveable { mutableStateOf(false) }
    val play: (List<Song>, Int) -> Unit = { list, i ->
        player?.run { setMediaItems(list.map { it.toItem() }, i, 0); prepare(); play() }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Color.Black, surface = Color.Black)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            CompositionLocalProvider(LocalPlayer provides player) { Box {
                Column {
                    // TV overscan-safe padding
                    TopBar()
                    Row(Modifier.weight(1f).padding(start = 32.dp, end = 48.dp)) {
                        NavRail(tab) { tab = it }
                        Box(Modifier.weight(1f).padding(start = 24.dp)) {
                            when (tab) {
                                0 -> Home(play)
                                1 -> Search(play)
                                else -> Library(play)
                            }
                        }
                    }
                    val s = np.song
                    if (s != null && player != null) MiniBar(s, np.playing, player) { full = true }
                }
                AnimatedVisibility(
                    full && np.song != null && player != null,
                    enter = slideInVertically { it / 3 } + fadeIn(),
                    exit = slideOutVertically { it / 3 } + fadeOut(),
                ) {
                    val s = np.song
                    if (s != null && player != null) FullPlayer(s, np, player)
                }
                S.menu?.let { Options(it) { S.menu = null } }
                S.ask?.let { TextDialog(it) { S.ask = null } }
            } }
            // Conditions are exclusive: the last registered enabled handler would otherwise win
            BackHandler(full) { full = false }
            BackHandler(!full && tab == 2 && S.open != null) { S.open = null }
            BackHandler(!full && tab != 0 && !(tab == 2 && S.open != null)) { tab = 0 }
        }
    }
}

@Composable
private fun TopBar() {
    Row(Modifier.fillMaxWidth().padding(start = 44.dp, end = 48.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
        Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(44.dp))
        Text("NOIR", fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, modifier = Modifier.padding(start = 14.dp, top = 6.dp))
        Spacer(Modifier.weight(1f))
        S.status?.let { msg ->
            Text(msg, color = Dim, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(top = 12.dp, end = 24.dp))
            // Progress messages end with "…" and stay until replaced; the rest fade out on their own
            LaunchedEffect(msg) { if (!msg.endsWith("…")) { delay(6000); if (S.status == msg) S.status = null } }
        }
        Column(horizontalAlignment = Alignment.End) {
            Clock()
            val activity = LocalContext.current as Activity
            Row(
                Modifier.tv(RoundedCornerShape(50), grow = 1.08f) { restart(activity) }.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.refresh, null, Modifier.size(15.dp), tint = Dim)
                Text("Restart", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** Digits only, e.g. 21:45 (or 9:45 when the TV uses 12-hour time). Ticks on the minute. */
@Composable
private fun Clock() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000 - System.currentTimeMillis() % 60_000); now = System.currentTimeMillis() } }
    val pattern = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
    Text(SimpleDateFormat(pattern, Locale.US).format(Date(now)), fontSize = 32.sp, fontWeight = FontWeight.Light, lineHeight = 34.sp)
}

/** Kills the app (music included) and opens it fresh: the way out if anything gets stuck. */
private fun restart(a: Activity) {
    a.stopService(Intent(a, PlaybackService::class.java))
    a.startActivity(Intent.makeRestartActivityTask(ComponentName(a, MainActivity::class.java)))
    Runtime.getRuntime().exit(0)
}

@Composable
private fun NavRail(tab: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.width(150.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Home" to Ic.home, "Search" to Ic.search, "Library" to Ic.library).forEachIndexed { i, (label, icon) ->
            val c = if (tab == i) Color.White else Dim
            Row(Modifier.fillMaxWidth().tv { onSelect(i) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(22.dp), tint = if (tab == i) Accent else Dim)
                Spacer(Modifier.width(12.dp))
                Text(label, color = c, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun Home(play: (List<Song>, Int) -> Unit) {
    LaunchedEffect(Unit) {
        launch { if (S.forYou == null) S.forYou = withContext(Dispatchers.IO) { runCatching { recommend() }.getOrDefault(emptyList()) } }
        // Re-guess the mood each visit: the app may stay open from evening into night
        val m = autoMood()
        if (m != S.autoMood || S.moodMix == null) { S.autoMood = m; S.moodMix = null; S.moodMix = runCatching { moodMix(m) }.getOrDefault(emptyList()) }
    }
    val playlists = Taste.playlists
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { MoodPrompt(play) }
        item { Shelf("Picked for your ${Mood.slotNames[Mood.slot(hour())]} · ${S.autoMood.label}", S.moodMix, play) }
        item {
            ShelfTitle("Moods")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                items(Mood.entries) { m ->
                    Box(
                        Modifier.size(170.dp, 96.dp).tv { startMood(m, play) }
                            .background(Brush.linearGradient(listOf(Color(m.from), Color(m.to))), RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) { Text(m.label, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (playlists.isNotEmpty()) item {
            ShelfTitle("Your playlists")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                items(playlists.toList(), key = { it.first }) { (name, songs) ->
                    Column(Modifier.width(150.dp).tv { if (songs.isNotEmpty()) play(songs, 0) }.padding(6.dp)) {
                        Art(songs.firstOrNull()?.thumb.orEmpty(), 138.dp)
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text(count(songs.size), color = Dim, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Shelf("Made for you", S.forYou, play) }
        item { Shelf("Recently played", Taste.history.take(20), play) }
        item { Shelf("Liked songs", Taste.liked.take(20), play) }
    }
}

/** "Tell Noir how you feel": the Fire TV keyboard's mic turns speech into text, the Mood DJ turns that into a saved, playing mix. */
@Composable
private fun MoodPrompt(play: (List<Song>, Int) -> Unit) {
    val ask = Ask("How are you feeling?", "Say it with the keyboard's mic, or type") { text ->
        val (mood, extra) = Mood.detect(text)
        startMood(mood ?: S.autoMood, play, prompt = text, extra = extra)
    }
    Row(
        Modifier.fillMaxWidth().padding(8.dp).tv(grow = 1.02f) { S.ask = ask }
            .background(Brush.horizontalGradient(listOf(Color(0xFF3A1C80), Color(0xFF1A0D33))), RoundedCornerShape(14.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Ic.mic, null, Modifier.size(26.dp), tint = Color.White)
        }
        Column(Modifier.padding(start = 18.dp)) {
            Text("How are you feeling?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Tell Noir your mood and it builds a playlist, saves it to your library and plays it", color = Color(0xFFCFC3F5), fontSize = 13.sp)
        }
    }
}

@Composable
private fun ShelfTitle(title: String) =
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp, 16.dp, 8.dp, 4.dp))

/** Radios of a few seed songs merged and ranked by taste. New users start from current hits. */
private suspend fun recommend(): List<Song> = coroutineScope {
    val seeds = Taste.seeds().ifEmpty { Yt.search("top hits ${Calendar.getInstance().get(Calendar.YEAR)}").take(3).map { it.id } }
    val all = seeds.map { async { runCatching { Yt.radio(it) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
    Taste.rank(all).take(30)
}

@Composable
private fun Search(play: (List<Song>, Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val go = {
        val q = S.query.trim()
        if (q.isNotEmpty()) scope.launch {
            S.results = null
            S.results = withContext(Dispatchers.IO) { runCatching { Yt.search(q) }.getOrDefault(emptyList()) }
        }
    }
    Column {
        OutlinedTextField(
            S.query, { S.query = it },
            placeholder = { Text("Songs or artists") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { go() }, onDone = { go() }, onGo = { go() }),
            // Text fields swallow D-pad keys for the cursor; let Down/Left leave the box like on any TV app
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).onPreviewKeyEvent {
                val dir = when (it.key) { Key.DirectionDown -> FocusDirection.Down; Key.DirectionLeft -> FocusDirection.Left; else -> null }
                if (dir != null && it.type == KeyEventType.KeyDown) focus.moveFocus(dir) else false
            },
        )
        val r = S.results
        when {
            r == null -> Text("Searching…", color = Dim, modifier = Modifier.padding(12.dp))
            r.isEmpty() && S.query.isNotBlank() -> Text("Press Search on the keyboard", color = Dim, modifier = Modifier.padding(12.dp))
            // A search result starts that song; autoplay then continues with similar songs
            else -> SongList(r, play = { list, i -> play(listOf(list[i]), 0) })
        }
    }
}

@Composable
private fun Library(play: (List<Song>, Int) -> Unit) {
    val open = S.open
    // The row you clicked disappears when opening/closing a list; move focus into the new view (not on first show)
    val top = remember { FocusRequester() }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (entered) { withFrameNanos {}; runCatching { top.requestFocus() } }
        entered = true
    }
    if (open != null) {
        val songs = if (open == LIKED) Taste.liked else Taste.playlists[open].orEmpty()
        Column {
            Text(if (open == LIKED) "Liked songs" else open, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 12.dp))
            when {
                songs.isNotEmpty() -> SongList(songs, play, top) { s, _ ->
                    if (open == LIKED) emptyList() else listOf(opt("Remove from this playlist") { Taste.setPlaylist(open, songs - s) })
                }
                open == LIKED -> Text("Like songs from the player and they show up here.", color = Dim, modifier = Modifier.padding(8.dp))
                else -> Text("Hold OK (or press ☰) on any song and choose Add to playlist.", color = Dim, modifier = Modifier.padding(8.dp))
            }
        }
        return
    }
    val liked = Taste.liked
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(4.dp)) {
        item {
            Text("Your library", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp, 4.dp, 4.dp, 2.dp))
            Text("Tip: hold OK or press ☰ on a song for more options", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(4.dp, 0.dp, 4.dp, 8.dp))
        }
        item { LibRow("Liked songs", count(liked.size), null, Ic.heart, Modifier.focusRequester(top)) { S.open = LIKED } }
        items(Taste.playlists.toList(), key = { it.first }) { (name, songs) ->
            val menu = { S.menu = listOf(opt("Play") { play(songs, 0) }, opt("Delete playlist") { Taste.setPlaylist(name, null) }) }
            LibRow(name, count(songs.size), songs.firstOrNull()?.thumb, Ic.library, onMenu = menu) { S.open = name }
        }
        item { LibRow("New playlist", "", null, Ic.add) { S.ask = Ask("New playlist", "Playlist name") { Taste.addToPlaylist(it, null) } } }
    }
}

private fun count(n: Int) = if (n == 1) "1 song" else "$n songs"

@Composable
private fun LibRow(
    title: String, sub: String, thumb: String?, icon: ImageVector, modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null, onClick: () -> Unit,
) {
    Row(modifier.fillMaxWidth().tv(onMenu = onMenu, onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (thumb != null) Art(thumb, 56.dp)
        else Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(26.dp), tint = Accent)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, color = Dim, fontSize = 13.sp)
        }
    }
}

/** Spotify-style song menu. Queue actions only show while something is playing. */
private fun songMenu(p: Player?, s: Song): List<Opt> = buildList {
    if (p != null && p.mediaItemCount > 0) {
        add(opt("Play next") { p.addMediaItem(p.currentMediaItemIndex + 1, s.toItem()) })
        add(opt("Add to queue") { p.addMediaItem(s.toItem()) })
    }
    add(opt("Add to playlist") { S.menu = playlistMenu(s) })
    add(opt(if (Taste.isLiked(s.id)) "Remove from Liked songs" else "Save to Liked songs") { Taste.toggleLike(s) })
}

private fun playlistMenu(s: Song): List<Opt> =
    listOf(opt("+ New playlist") { S.ask = Ask("New playlist", "Playlist name") { Taste.addToPlaylist(it, s) } }) +
        Taste.playlists.keys.map { name -> opt(name) { Taste.addToPlaylist(name, s) } }

@Composable
private fun Options(opts: List<Opt>, dismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(dismiss) {
        // Still holding OK from the long press that opened this? Ignore its repeats so they don't pick the first option.
        // Scrolls, because "Add to playlist" lists every playlist.
        Column(
            Modifier.width(380.dp).background(Panel, RoundedCornerShape(14.dp)).verticalScroll(rememberScrollState()).padding(10.dp)
                .onPreviewKeyEvent { it.nativeKeyEvent.repeatCount > 0 && (it.key == Key.DirectionCenter || it.key == Key.Enter) },
        ) {
            opts.forEachIndexed { i, (label, action) ->
                Text(
                    label,
                    Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .tv(grow = 1f) { dismiss(); action() }.padding(14.dp),
                )
            }
        }
        LaunchedEffect(opts) { runCatching { first.requestFocus() } }
    }
}

@Composable
private fun TextDialog(ask: Ask, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val ok = { val n = name.trim(); if (n.isNotEmpty()) { dismiss(); ask.done(n) } }
    Dialog(dismiss) {
        Column(Modifier.width(520.dp).background(Panel, RoundedCornerShape(14.dp)).padding(20.dp)) {
            Text(ask.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                name, { name = it },
                placeholder = { Text(ask.hint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { ok() }, onGo = { ok() }, onSearch = { ok() }),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).focusRequester(field),
            )
        }
        LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    }
}

@Composable
private fun Shelf(title: String, songs: List<Song>?, play: (List<Song>, Int) -> Unit) {
    ShelfTitle(title)
    val player = LocalPlayer.current
    when {
        songs == null -> Text("Loading…", color = Dim, modifier = Modifier.padding(8.dp))
        songs.isEmpty() -> Text("Nothing here yet", color = Dim, modifier = Modifier.padding(8.dp))
        else -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        ) {
            itemsIndexed(songs, key = { i, s -> "$i${s.id}" }) { i, s ->
                Column(Modifier.width(150.dp).prefetch(s.id).tv(onMenu = { S.menu = songMenu(player, s) }) { play(songs, i) }.padding(6.dp)) {
                    Art(s.thumb, 138.dp)
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    Text(s.artist, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    play: (List<Song>, Int) -> Unit,
    firstFocus: FocusRequester? = null,
    extra: (Song, Int) -> List<Opt> = { _, _ -> emptyList() }, // menu items shown above the usual song menu
) {
    val player = LocalPlayer.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(4.dp)) {
        // Keyed by position so focus stays put when the focused song is removed or moved
        itemsIndexed(songs) { i, s ->
            val focus = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier
            Row(
                focus.fillMaxWidth().prefetch(s.id).tv(onMenu = { S.menu = extra(s, i) + songMenu(player, s) }) { play(songs, i) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Art(s.thumb, 56.dp)
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artist, color = Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (s.duration > 0) Text(time(s.duration * 1000), color = Dim, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MiniBar(s: Song, playing: Boolean, player: Player, onOpen: () -> Unit) {
    Column(Modifier.background(Panel)) {
        Progress(player, Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).tv(grow = 1f, onClick = onOpen).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(s.thumb, 48.dp)
                Column(Modifier.padding(horizontal = 14.dp)) {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(s.artist, color = Dim, fontSize = 13.sp, maxLines = 1)
                }
            }
            Controls(player, playing, 28.dp)
        }
    }
}

@Composable
private fun FullPlayer(s: Song, np: NowPlaying, player: Player) {
    val liked = Taste.isLiked(s.id)
    var queue by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    val queueFocus = remember { FocusRequester() }
    // The focused button disappears when switching views, so put focus somewhere sensible
    LaunchedEffect(queue) { runCatching { (if (queue) queueFocus else playFocus).requestFocus() } }
    BackHandler(queue) { queue = false }
    Row(
        Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 80.dp, vertical = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(s.thumb, label = "art") { Art(it, 340.dp, 18.dp) }
        Spacer(Modifier.width(64.dp))
        if (queue) Queue(np, player, queueFocus, Modifier.weight(1f).fillMaxHeight())
        else Column(Modifier.weight(1f)) {
            Crossfade(s, label = "meta") {
                Column {
                    Text(it.title, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(it.artist, fontSize = 20.sp, color = Dim, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(36.dp))
            Progress(player, Modifier.height(4.dp).clip(RoundedCornerShape(2.dp)), showTime = true)
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn(if (liked) Ic.heart else Ic.heartOutline, 30.dp, tint = if (liked) Accent else Color.White) {
                    Taste.toggleLike(s)
                }
                Spacer(Modifier.width(24.dp))
                Controls(player, np.playing, 40.dp, playFocus)
                Spacer(Modifier.width(24.dp))
                IconBtn(Ic.add, 30.dp) { S.menu = playlistMenu(s) }
                IconBtn(Ic.queue, 30.dp) { queue = true }
            }
        }
    }
}

/** Spotify's "Up next": pick a song to jump to it; hold OK / ☰ to move or remove it. */
@Composable
private fun Queue(np: NowPlaying, player: Player, focus: FocusRequester, modifier: Modifier) {
    val start = np.index + 1
    val next = np.queue.drop(start)
    Column(modifier) {
        Text("Up next", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
        Text("Hold OK or press ☰ on a song to move or remove it", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(4.dp, 0.dp, 4.dp, 8.dp))
        if (next.isEmpty()) Text("Nothing queued. Autoplay adds similar songs soon.", color = Dim, modifier = Modifier.padding(4.dp))
        // shortcut: indexes are taken when the menu opens; if a song ends while it is open they are one off. Re-resolve by id if that bites.
        else SongList(next, { _, i -> player.seekTo(start + i, 0) }, focus) { _, i ->
            val at = start + i
            buildList {
                if (i > 0) add(opt("Move up") { player.moveMediaItem(at, at - 1) })
                if (i < next.size - 1) add(opt("Move down") { player.moveMediaItem(at, at + 1) })
                add(opt("Remove from queue") { player.removeMediaItem(at) })
            }
        }
    }
}

@Composable
private fun Controls(player: Player, playing: Boolean, size: Dp, playFocus: FocusRequester? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconBtn(Ic.prev, size) { player.seekToPrevious() }
        IconBtn(if (playing) Ic.pause else Ic.play, size * 1.3f, playFocus?.let { Modifier.focusRequester(it) } ?: Modifier) {
            if (player.isPlaying) player.pause() else player.play()
        }
        IconBtn(Ic.next, size) { player.seekToNext() }
    }
}

/** Smooth progress: polls 4x a second and animates linearly in between. */
@Composable
private fun Progress(player: Player, modifier: Modifier, showTime: Boolean = false) {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0)
            delay(250)
        }
    }
    val frac by animateFloatAsState(if (dur > 0) pos.toFloat() / dur else 0f, tween(250, easing = LinearEasing), label = "progress")
    Box(modifier.fillMaxWidth().background(Color(0xFF2A2A2A))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).background(Accent))
    }
    if (showTime) Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(time(pos), color = Dim, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(time(dur), color = Dim, fontSize = 13.sp)
    }
}

@Composable
private fun IconBtn(icon: ImageVector, size: Dp, modifier: Modifier = Modifier, tint: Color = Color.White, onClick: () -> Unit) {
    Box(modifier.size(size + 20.dp).tv(CircleShape, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size), tint = tint)
    }
}

/** Cover art; the Noir logo stands in when a song has none or it fails to load. */
@Composable
private fun Art(url: String, size: Dp, corner: Dp = 8.dp) {
    val logo = painterResource(R.drawable.ic_launcher)
    AsyncImage(
        url, null, Modifier.size(size).clip(RoundedCornerShape(corner)).background(Panel),
        error = logo, fallback = logo, contentScale = ContentScale.Crop,
    )
}

/** Resolve the song's stream once focus rests on it for a moment, so pressing OK plays almost at once. */
private fun Modifier.prefetch(id: String) = composed {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (focused) { delay(250); Yt.prefetch(id) } }
    onFocusChanged { focused = it.isFocused }
}

/** D-pad focus: the focused item grows a little and gets an accent outline. [onMenu] runs on a long OK press or the remote's ☰ key. */
private fun Modifier.tv(
    shape: Shape = RoundedCornerShape(10.dp), grow: Float = 1.06f, onMenu: (() -> Unit)? = null, onClick: () -> Unit,
) = composed {
    var focused by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) grow else 1f, label = "focus")
    graphicsLayer { scaleX = scale; scaleY = scale }
        .border(2.dp, if (focused) Accent else Color.Transparent, shape)
        .background(if (focused) Color(0xFF1C1C1C) else Color.Transparent, shape)
        .onFocusChanged { focused = it.isFocused }
        .onPreviewKeyEvent {
            val ok = it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter
            val down = it.type == KeyEventType.KeyDown
            when {
                onMenu == null -> false
                it.key == Key.Menu -> { if (!down) onMenu(); true }
                // Android flags a held key as a long press; swallow the release so it doesn't also click
                ok && down && it.nativeKeyEvent.isLongPress -> { longPressed = true; onMenu(); true }
                ok && down && it.nativeKeyEvent.repeatCount == 0 -> { longPressed = false; false }
                ok && !down && longPressed -> { longPressed = false; true }
                else -> false
            }
        }
        .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
}

private fun time(ms: Long) = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

// Material icon paths, inlined so we don't ship the 30 MB extended icon library
private object Ic {
    private fun v(d: String) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(addPathNodes(d), fill = SolidColor(Color.White)).build()

    val play = v("M8 5v14l11-7z")
    val pause = v("M6 19h4V5H6v14zm8-14v14h4V5h-4z")
    val next = v("M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z")
    val prev = v("M6 6h2v12H6z M9.5 12l8.5 6V6z")
    val home = v("M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z")
    val library = v("M20 2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 5h-3v5.5c0 1.38-1.12 2.5-2.5 2.5S10 13.88 10 12.5s1.12-2.5 2.5-2.5c.57 0 1.08.19 1.5.51V5h4v2zM4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6z")
    val queue = v("M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18c-.31-.11-.65-.18-1-.18-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3V8h3V6h-5z")
    val mic = v("M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z")
    val refresh = v("M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z")
    val add = v("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val search = v("M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z")
    val heart = v("M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z")
    val heartOutline = v("M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z")
}
