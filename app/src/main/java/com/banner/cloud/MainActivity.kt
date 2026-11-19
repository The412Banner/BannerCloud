package com.banner.cloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.banner.cloud.ui.Accent
import com.banner.cloud.ui.CloudTheme
import com.banner.cloud.ui.PadCommand
import com.banner.cloud.ui.XboxGreen
import com.banner.cloud.ui.focusHighlight
import com.banner.cloud.ui.hasGamepad
import com.banner.cloud.ui.mapFaceButtons
import com.banner.cloud.xbox.XboxAccountStore
import com.banner.cloud.xbox.XboxCatalog
import com.banner.cloud.xbox.XboxCloudActivity
import com.banner.cloud.xbox.XboxCloudPlayerActivity
import com.banner.cloud.xbox.XboxCloudSession
import com.banner.cloud.xbox.XboxCloudSettingsActivity
import com.banner.cloud.xbox.XboxCredentials
import com.banner.cloud.xbox.XboxDiag
import com.banner.cloud.xbox.XboxGame
import com.banner.cloud.xbox.XboxLibraryCache
import com.banner.cloud.xbox.XboxLoginActivity
import com.banner.cloud.xbox.Xodus
import com.banner.cloud.xbox.mergeEditions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sign-in, then the account's Xbox library (Cloud / Owned / Game Pass / All). Touch and controller:
 * A selects, B goes back, LB/RB switch the filter, X refreshes, Y searches, Start opens settings.
 */
class MainActivity : ComponentActivity() {
    private var account by mutableStateOf<String?>(null)
    private var shownName by mutableStateOf<String?>(null)
    private var message by mutableStateOf<String?>(null)
    /** The screen on top registers here for the shortcut buttons. */
    internal var padHandler: ((PadCommand) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        readAccount()
        setContent {
            CloudTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        readAccount()
                        if (result.resultCode != Activity.RESULT_OK && account == null) message = "Sign-in did not finish."
                    }
                    AnimatedContent(targetState = account, label = "screen",
                        transitionSpec = { (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith fadeOut(tween(160)) }
                    ) { signedIn ->
                        if (signedIn == null) {
                            SignInScreen(Xodus.isAvailable(this@MainActivity), message) {
                                message = null
                                login.launch(Intent(this@MainActivity, XboxLoginActivity::class.java))
                            }
                        } else {
                            LibraryScreen(this@MainActivity, signedIn, shownName.orEmpty(), message,
                                onMessageShown = { message = null }, onSignOut = { signOut() })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        readAccount()
    }

    /** Controller buttons: A and B act as select and back everywhere; the shoulder and face shortcuts go to the screen. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        mapFaceButtons(event)?.let { return super.dispatchKeyEvent(it) }
        val command = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1 -> PadCommand.PREV_TAB
            KeyEvent.KEYCODE_BUTTON_R1 -> PadCommand.NEXT_TAB
            KeyEvent.KEYCODE_BUTTON_X -> PadCommand.REFRESH
            KeyEvent.KEYCODE_BUTTON_Y -> PadCommand.SEARCH
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> PadCommand.SETTINGS
            else -> null
        }
        if (command != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) padHandler?.invoke(command)
            if (padHandler != null) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun readAccount() {
        account = runCatching { XboxAccountStore.signedInAs(applicationContext) }.getOrNull()
        shownName = runCatching { XboxAccountStore.displayName(applicationContext) }.getOrNull()
    }

    private fun signOut() {
        val app = applicationContext
        Thread {
            XboxCredentials.invalidate(app) {
                XboxLibraryCache.clear(app)
                XboxCloudActivity.clearWebSignIn(app)
                XboxCloudSession.clear()
            }
            XboxDiag.log(app, "signed out")
            runOnUiThread { account = null; shownName = null; message = "Signed out." }
        }.start()
    }
}

// ---------------------------------------------------------------------------------------------------
// Sign-in

@Composable
private fun SignInScreen(backendReady: Boolean, message: String?, onSignIn: () -> Unit) {
    val button = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { button.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F2A13), Color(0xFF0A0C0F), Color(0xFF0A0C0F)))),
        contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 440.dp).padding(28.dp).windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(XboxGreen), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Even Better Xcloud", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Xbox Cloud Gaming with frame generation", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(36.dp))
            if (backendReady) {
                PillButton("Sign in with Microsoft", Icons.Filled.PlayArrow, primary = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(button), onClick = onSignIn)
            } else {
                Text("The Xbox backend is missing from this build.", color = MaterialTheme.colorScheme.error)
            }
            AnimatedVisibility(message != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text(message.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// Library

private enum class Filter(val label: String) { CLOUD("Cloud"), OWNED("Owned"), GAME_PASS("Game Pass"), ALL("All"), REMOTE("Remote Play") }

private fun Filter.matches(game: XboxGame) = when (this) {
    Filter.CLOUD -> game.cloud
    Filter.OWNED -> game.owned
    Filter.GAME_PASS -> game.gamePass
    Filter.ALL -> true
    Filter.REMOTE -> false
}

private sealed interface Page {
    data object Grid : Page
    data class Detail(val game: XboxGame) : Page
    data object SignOut : Page
}

@Composable
private fun LibraryScreen(activity: MainActivity, account: String, shownName: String, externalMessage: String?,
                          onMessageShown: () -> Unit, onSignOut: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val scope = rememberCoroutineScope()
    var games by remember { mutableStateOf<List<XboxGame>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(Filter.CLOUD) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var page by remember { mutableStateOf<Page>(Page.Grid) }
    var lastGameId by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val searchFocus = remember { FocusRequester() }
    var consoles by remember { mutableStateOf<List<XboxCloudSession.Console>?>(null) }
    var consolesLoading by remember { mutableStateOf(false) }
    var consolesError by remember { mutableStateOf<String?>(null) }
    fun loadConsoles() {
        if (consolesLoading) return
        consolesLoading = true; consolesError = null
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { XboxCloudSession.consoles(XboxCloudSession.getHome(app, account)).second } }
                .onSuccess { consoles = it }
                .onFailure { consolesError = it.message ?: "Remote Play is not available for this account."; XboxDiag.log(app, "remote play list failed: ${it.javaClass.simpleName}") }
            consolesLoading = false
        }
    }
    LaunchedEffect(filter) { if (filter == Filter.REMOTE && consoles == null) loadConsoles() }
    val gamepad = remember { hasGamepad() }

    LaunchedEffect(externalMessage) { if (externalMessage != null) { message = externalMessage; onMessageShown() } }
    LaunchedEffect(message) { if (message != null) { delay(6000); message = null } }

    fun sync(force: Boolean) {
        if (syncing) return
        syncing = true
        scope.launch {
            val market = Xodus.market()
            val cached = withContext(Dispatchers.IO) { XboxLibraryCache.read(app, account, market) }
            if (cached != null) { games = cached.games; loaded = true }
            if (cached != null && !cached.needsRefresh(force)) { syncing = false; return@launch }
            withContext(Dispatchers.IO) { loadLibrary(app, account, market) }
                .onSuccess { games = it }
                .onFailure { message = it.message ?: "The Xbox library could not be loaded." }
            loaded = true
            syncing = false
        }
    }
    LaunchedEffect(account) { sync(false) }

    DisposableEffect(page, searching) {
        activity.padHandler = { command ->
            if (page != Page.Grid) false else {
                when (command) {
                    PadCommand.PREV_TAB -> filter = Filter.entries[(filter.ordinal + Filter.entries.size - 1) % Filter.entries.size]
                    PadCommand.NEXT_TAB -> filter = Filter.entries[(filter.ordinal + 1) % Filter.entries.size]
                    PadCommand.REFRESH -> if (filter == Filter.REMOTE) loadConsoles() else sync(true)
                    PadCommand.SEARCH -> { searching = !searching; if (!searching) query = "" }
                    PadCommand.SETTINGS -> ctx.startActivity(Intent(ctx, XboxCloudSettingsActivity::class.java))
                }
                true
            }
        }
        onDispose { activity.padHandler = null }
    }
    LaunchedEffect(searching) { if (searching) { delay(120); runCatching { searchFocus.requestFocus() } } }

    BackHandler(enabled = page != Page.Grid) { page = Page.Grid }
    BackHandler(enabled = page == Page.Grid && searching) { searching = false; query = "" }

    val shown = remember(games, filter, query) {
        val q = query.trim().lowercase()
        games.filter { filter.matches(it) && (q.isEmpty() || q in it.title.lowercase()) }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(targetState = page, label = "library-page",
            transitionSpec = {
                if (targetState is Page.Grid) fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                else (fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.96f)) togetherWith fadeOut(tween(140))
            }
        ) { current ->
            when (current) {
                Page.Grid -> GridPage(
                    games = games, shown = shown, loaded = loaded, syncing = syncing, shownName = shownName, filter = filter,
                    searching = searching, query = query, gridState = gridState, searchFocus = searchFocus, gamepad = gamepad,
                    restoreId = lastGameId,
                    onFilter = { filter = it }, onQuery = { query = it.take(80) },
                    onSearch = { searching = !searching; if (!searching) query = "" },
                    onRefresh = { if (filter == Filter.REMOTE) loadConsoles() else sync(true) },
                    consoles = consoles, consolesLoading = consolesLoading, consolesError = consolesError,
                    onConsole = { c ->
                        ctx.startActivity(Intent(ctx, com.banner.cloud.xbox.XboxNativePlayerActivity::class.java)
                            .putExtra("home", true).putExtra("serverId", c.serverId).putExtra("title", c.name))
                    },
                    onSettings = { ctx.startActivity(Intent(ctx, XboxCloudSettingsActivity::class.java)) },
                    onSignOut = { page = Page.SignOut },
                    onOpen = { lastGameId = it.id; page = Page.Detail(it) },
                )
                is Page.Detail -> DetailPage(current.game, onBack = { page = Page.Grid },
                    onPlay = { ctx.startActivity(XboxCloudPlayerActivity.intent(ctx, current.game)) },
                    onWebPlayer = { ctx.startActivity(XboxCloudActivity.intent(ctx, current.game.cloudUrl)) })
                Page.SignOut -> SignOutPage(onCancel = { page = Page.Grid }, onConfirm = { page = Page.Grid; onSignOut() })
            }
        }
        AnimatedVisibility(message != null, modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 }) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).widthIn(max = 560.dp)
                    .clickable { message = null }) {
                Text(message.orEmpty(), Modifier.padding(horizontal = 18.dp, vertical = 14.dp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun GridPage(
    games: List<XboxGame>, shown: List<XboxGame>, loaded: Boolean, syncing: Boolean, shownName: String, filter: Filter,
    searching: Boolean, query: String, gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    searchFocus: FocusRequester, gamepad: Boolean, restoreId: String?,
    onFilter: (Filter) -> Unit, onQuery: (String) -> Unit, onSearch: () -> Unit, onRefresh: () -> Unit,
    onSettings: () -> Unit, onSignOut: () -> Unit, onOpen: (XboxGame) -> Unit,
    consoles: List<XboxCloudSession.Console>?, consolesLoading: Boolean, consolesError: String?,
    onConsole: (XboxCloudSession.Console) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth > maxHeight
        val compact = maxWidth < 420.dp
        Column(Modifier.fillMaxSize()) {
            // Header: wordmark + account, actions
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(XboxGreen), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Even Better Xcloud", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (shownName.isNotEmpty()) Text(shownName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AnimatedVisibility(syncing, enter = fadeIn(), exit = fadeOut()) { SyncDot() }
                HeaderButton(Icons.Filled.Search, "Search", onSearch)
                HeaderButton(Icons.Filled.Refresh, "Refresh", onRefresh)
                HeaderButton(Icons.Filled.Settings, "Settings", onSettings)
                HeaderButton(Icons.Filled.Logout, "Sign out", onSignOut)
            }
            AnimatedVisibility(searching, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                OutlinedTextField(value = query, onValueChange = onQuery, singleLine = true,
                    placeholder = { Text("Search your library") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Clear", Modifier.clickable { onQuery("") }) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(searchFocus))
            }
            // Filters
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (gamepad) PadHint("LB")
                Filter.entries.forEach { f ->
                    FilterPill(f.label, if (f == Filter.REMOTE) consoles?.size ?: -1 else games.count { f.matches(it) }, selected = filter == f) { onFilter(f) }
                }
                if (gamepad) PadHint("RB")
            }
            // Grid
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (filter == Filter.REMOTE) ConsolesPane(consoles, consolesLoading, consolesError, gamepad, onConsole) else when {
                    !loaded && shown.isEmpty() -> SkeletonGrid(if (compact) 110.dp else if (wide) 150.dp else 128.dp)
                    shown.isEmpty() -> Text(if (games.isEmpty()) "No games yet. Refresh to load your library." else "Nothing here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                    else -> {
                        val first = remember { FocusRequester() }
                        // A new filter fades the grid in from the top; a new search jumps to the top. (No per-card
                        // placement animation: with hundreds of cards changing at once it left cards overlapping.)
                        val fade = remember { Animatable(1f) }
                        var lastFilter by remember { mutableStateOf(filter) }
                        var lastQuery by remember { mutableStateOf(query) }
                        LaunchedEffect(filter, query) {
                            if (filter != lastFilter) {
                                lastFilter = filter; lastQuery = query
                                fade.snapTo(0f); runCatching { gridState.scrollToItem(0) }; fade.animateTo(1f, tween(220))
                            } else if (query != lastQuery) {
                                lastQuery = query
                                runCatching { gridState.scrollToItem(0) }
                            }
                        }
                        // Coming back from a game's page with a controller: focus that game's card once.
                        var restorePending by remember { mutableStateOf(gamepad && restoreId != null) }
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(if (compact) 110.dp else if (wide) 150.dp else 128.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
                        ) {
                            items(shown, key = { it.id }) { game ->
                                val own = remember { FocusRequester() }
                                val requester = if (game.id == shown.first().id && game.id != restoreId) first else own
                                GameCard(game, Modifier, requester) { onOpen(game) }
                                if (restorePending && game.id == restoreId) LaunchedEffect(Unit) {
                                    runCatching { own.requestFocus() }
                                    restorePending = false
                                }
                            }
                        }
                        // With a controller, the first card takes focus when the grid appears and on each filter change
                        // (not when coming back from a game's page: that card is focused instead).
                        var focusedFilter by remember { mutableStateOf(if (restoreId != null) filter else null) }
                        LaunchedEffect(filter) {
                            if (gamepad && focusedFilter != filter) { delay(80); runCatching { first.requestFocus() }; focusedFilter = filter }
                        }
                    }
                }
            }
            if (gamepad) ControllerLegend()
        }
    }
}

/** Remote Play: the account's consoles (stream from your own Xbox). */
@Composable
private fun ConsolesPane(consoles: List<XboxCloudSession.Console>?, loading: Boolean, error: String?, gamepad: Boolean,
                         onConsole: (XboxCloudSession.Console) -> Unit) {
    val first = remember { FocusRequester() }
    when {
        loading && consoles == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Looking for your consoles…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        consoles.isNullOrEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No consoles found. On your Xbox: Settings › Devices & connections › Remote features › Enable remote features, " +
                "and keep it on or in sleep mode.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> {
            val list = consoles.orEmpty()
            LazyVerticalGrid(columns = GridCells.Adaptive(220.dp), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
                items(list, key = { it.serverId }) { c ->
                    val shape = RoundedCornerShape(16.dp)
                    val own = remember { FocusRequester() }
                    val requester = if (c.serverId == list.first().serverId) first else own
                    Column(Modifier.focusRequester(requester).focusHighlight(shape, zoom = 1.04f, width = 3.dp).clip(shape)
                        .background(MaterialTheme.colorScheme.surface).clickable { onConsole(c) }.padding(18.dp)) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(XboxGreen), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(c.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(listOf(consoleType(c.type), powerText(c.power)).filter { it.isNotEmpty() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (gamepad) LaunchedEffect(list.size) { delay(80); runCatching { first.requestFocus() } }
        }
    }
}

private fun consoleType(type: String): String = when (type) {
    "XboxSeriesX" -> "Xbox Series X"; "XboxSeriesS" -> "Xbox Series S"; "XboxOne" -> "Xbox One"; "XboxOneS" -> "Xbox One S"
    "XboxOneX", "XboxOneXDevkit" -> "Xbox One X"; else -> type.replace(Regex("([a-z])([A-Z])"), "$1 $2")
}

private fun powerText(power: String): String = when (power) {
    "On" -> "On"; "ConnectedStandby" -> "Sleeping (wakes when you connect)"; "Off" -> "Off"; else -> ""
}

@Composable
private fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = CircleShape
    Box(Modifier.padding(2.dp).focusHighlight(shape, zoom = 1.1f).clip(shape).clickable(onClick = onClick).padding(10.dp),
        contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val bg by animateColorAsState(if (selected) Accent else MaterialTheme.colorScheme.surfaceVariant, tween(220), label = "pill-bg")
    val fg by animateColorAsState(if (selected) Color(0xFF04170A) else MaterialTheme.colorScheme.onSurface, tween(220), label = "pill-fg")
    Row(Modifier.focusHighlight(shape, zoom = 1.06f).clip(shape).background(bg).clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
        if (count >= 0) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PadHint(label: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 7.dp, vertical = 4.dp))
}

@Composable
private fun ControllerLegend() {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("A" to "Select", "B" to "Back", "X" to "Refresh", "Y" to "Search", "☰" to "Settings").forEach { (key, what) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PadHint(key)
                Spacer(Modifier.width(6.dp))
                Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SyncDot() {
    val pulse = rememberInfiniteTransition(label = "sync")
    val a by pulse.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "sync-alpha")
    Box(Modifier.padding(horizontal = 10.dp).size(9.dp).alpha(a).clip(CircleShape).background(Accent))
}

@Composable
private fun SkeletonGrid(cell: androidx.compose.ui.unit.Dp) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val a by pulse.animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skeleton-alpha")
    LazyVerticalGrid(columns = GridCells.Adaptive(cell), userScrollEnabled = false,
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        items(12) {
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(0.75f).alpha(a).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.7f).height(10.dp).alpha(a).clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
    }
}

@Composable
private fun GameCard(game: XboxGame, modifier: Modifier, requester: FocusRequester, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.75f).focusRequester(requester).focusHighlight(shape, zoom = 1.06f, width = 3.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
            AsyncImage(model = ImageRequest.Builder(ctx).data(game.posterUrl ?: game.heroUrl).crossfade(280).build(),
                contentDescription = game.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.TopStart).padding(7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (game.cloud) Badge("CLOUD", XboxGreen)
                if (game.owned) Badge("OWNED", Color(0xCC1E2228))
                else if (game.gamePass) Badge("GAME PASS", Color(0xCC1E2228))
            }
        }
        Text(game.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2,
            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp))
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(color).padding(horizontal = 6.dp, vertical = 3.dp))
}

// ---------------------------------------------------------------------------------------------------
// Game details

@Composable
private fun DetailPage(game: XboxGame, onBack: () -> Unit, onPlay: () -> Unit, onWebPlayer: () -> Unit) {
    val ctx = LocalContext.current
    val primary = remember { FocusRequester() }
    var wait by remember(game.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(game.id) { if (game.cloud) wait = withContext(Dispatchers.IO) { cloudWait(ctx.applicationContext, game) } }
    LaunchedEffect(game.id) { delay(60); runCatching { primary.requestFocus() } }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth > maxHeight && maxWidth > 600.dp
        AsyncImage(model = ImageRequest.Builder(ctx).data(game.heroUrl ?: game.posterUrl).crossfade(320).build(),
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = if (wide) Modifier.fillMaxSize().alpha(0.55f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Box(Modifier.fillMaxSize().background(
            if (wide) Brush.horizontalGradient(0f to Color(0xF20A0C0F), 0.55f to Color(0xD90A0C0F), 1f to Color(0x330A0C0F))
            else Brush.verticalGradient(0f to Color.Transparent, 0.22f to Color(0x660A0C0F), 0.42f to Color(0xFF0A0C0F))))
        Column(Modifier.fillMaxHeight().then(if (wide) Modifier.fillMaxWidth(0.6f) else Modifier.fillMaxWidth())
            .windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)) {
            Box(Modifier.focusHighlight(CircleShape, zoom = 1.1f).clip(CircleShape).background(Color(0x99000000))
                .clickable(onClick = onBack).padding(10.dp)) {
                Icon(Icons.Filled.Close, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(if (wide) 40.dp else 150.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (game.cloud) Badge("CLOUD", XboxGreen)
                if (game.owned) Badge("OWNED", Color(0xFF2A3038)) else if (game.gamePass) Badge("GAME PASS", Color(0xFF2A3038))
            }
            Spacer(Modifier.height(10.dp))
            Text(game.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            AnimatedVisibility(wait != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text("Cloud wait: ${wait.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = Accent,
                    modifier = Modifier.padding(top = 4.dp))
            }
            if (game.publisher.isNotBlank()) Text(game.publisher, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(22.dp))
            if (game.cloud) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Play", Icons.Filled.PlayArrow, primary = true, modifier = Modifier.focusRequester(primary), onClick = onPlay)
                    PillButton("Microsoft's player", null, primary = false, onClick = onWebPlayer)
                }
            } else {
                Text("Microsoft does not stream this game. It can only be installed on a PC or Xbox.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                PillButton("Back", null, primary = false, modifier = Modifier.focusRequester(primary), onClick = onBack)
            }
            if (game.description.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Text(game.description.take(900), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PillButton(label: String, icon: ImageVector?, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(modifier.focusHighlight(shape, zoom = 1.05f).clip(shape)
        .background(if (primary) Accent else MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(icon, null, tint = if (primary) Color(0xFF04170A) else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = if (primary) Color(0xFF04170A) else MaterialTheme.colorScheme.onSurface)
    }
}

// ---------------------------------------------------------------------------------------------------
// Sign out

@Composable
private fun SignOutPage(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { cancel.requestFocus() } }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).padding(28.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface).padding(24.dp)) {
            Text("Sign out?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text("Even Better Xcloud forgets this Microsoft account on this device.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                PillButton("Cancel", null, primary = false, modifier = Modifier.focusRequester(cancel), onClick = onCancel)
                PillButton("Sign out", Icons.Filled.Logout, primary = true, onClick = onConfirm)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------

/** Microsoft's estimated wait for a cloud console for [game] ("about 2 min"), or null when unknown. */
private fun cloudWait(app: Context, game: XboxGame): String? = runCatching {
    val account = XboxAccountStore.signedInAs(app) ?: return@runCatching null
    val session = XboxCloudSession.get(app, account)
    val title = XboxCloudSession.titleFor(session, game.cloudId ?: game.id, game.title) ?: return@runCatching null
    XboxCloudSession.waitSeconds(session, title)?.let { XboxCloudSession.waitText(it) }
}.getOrNull()

/** Bannerlator's library sync: the account's entitlements (Xodus), Microsoft Store details, the public cloud lists. */
private fun loadLibrary(app: Context, account: String, market: String): Result<List<XboxGame>> = runCatching {
    val response = Xodus.run(app, listOf("library", "--json", "--market", market))
    val licenses = XboxCatalog.access(response, account, market, report = { XboxDiag.log(app, it) })
    val products = XboxCatalog.rawProducts(licenses.keys.toList(), market)
    val cloud = runCatching { XboxCatalog.cloudCatalog(market) }
        .onFailure { XboxDiag.log(app, "cloud catalog failed: ${it.javaClass.simpleName}") }.getOrNull()
    val list = mergeEditions(products.mapNotNull { XboxCatalog.product(it, licenses, market, cloud) }).sortedBy { it.title.lowercase() }
    XboxDiag.log(app, "library requested=${licenses.size} returned=${products.size} supported=${list.size} " +
        "cloud=${list.count { it.cloud }} owned=${list.count { it.owned }} gamepass=${list.count { it.gamePass }} " +
        "cloud-lists=${cloud?.gamePass?.size ?: -1}/${cloud?.all?.size ?: -1}")
    runCatching { XboxLibraryCache.write(app, account, market, list) }
    list
}
