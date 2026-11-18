package com.banner.cloud

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.banner.cloud.ui.CloudTheme
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sign-in, then the account's Xbox library: what streams, what is owned, Game Pass; tap a game to play it in the cloud. */
class MainActivity : ComponentActivity() {
    private var account by mutableStateOf<String?>(null)
    private var shownName by mutableStateOf<String?>(null)
    private var message by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readAccount()
        setContent {
            CloudTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        readAccount()
                        if (result.resultCode != Activity.RESULT_OK && account == null) message = "Sign-in did not finish."
                    }
                    val signedIn = account
                    if (signedIn == null) {
                        SignInScreen(Xodus.isAvailable(this), message) {
                            message = null
                            login.launch(Intent(this, XboxLoginActivity::class.java))
                        }
                    } else {
                        LibraryScreen(signedIn, shownName.orEmpty(), message, onMessageShown = { message = null },
                            onSignOut = { signOut() })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        readAccount()
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

@Composable
private fun SignInScreen(backendReady: Boolean, message: String?, onSignIn: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F2A12), Color(0xFF0B0D10)))),
        contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 460.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("BannerCloud", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("Xbox Cloud Gaming with frame generation", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            if (backendReady) {
                Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Sign in with Microsoft") }
            } else {
                Text("The Xbox backend is missing from this build.", color = MaterialTheme.colorScheme.error)
            }
            if (message != null) {
                Spacer(Modifier.height(16.dp))
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private enum class Filter(val label: String) { CLOUD("Cloud"), OWNED("Owned"), GAME_PASS("Game Pass"), ALL("All") }

private fun Filter.matches(game: XboxGame) = when (this) {
    Filter.CLOUD -> game.cloud
    Filter.OWNED -> game.owned
    Filter.GAME_PASS -> game.gamePass
    Filter.ALL -> true
}

@Composable
private fun LibraryScreen(account: String, shownName: String, externalMessage: String?, onMessageShown: () -> Unit,
                          onSignOut: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val scope = rememberCoroutineScope()
    var games by remember { mutableStateOf<List<XboxGame>>(emptyList()) }
    var syncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(Filter.CLOUD) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<XboxGame?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(externalMessage) { if (externalMessage != null) { message = externalMessage; onMessageShown() } }

    fun sync(force: Boolean) {
        if (syncing) return
        syncing = true
        scope.launch {
            val market = Xodus.market()
            val cached = withContext(Dispatchers.IO) { XboxLibraryCache.read(app, account, market) }
            if (cached != null) games = cached.games
            if (cached != null && !cached.needsRefresh(force)) { syncing = false; status = ""; return@launch }
            status = if (games.isEmpty()) "Loading your Xbox library…" else "Refreshing…"
            val result = withContext(Dispatchers.IO) { loadLibrary(app, account, market) }
            result.onSuccess { games = it }
                .onFailure { message = it.message ?: "The Xbox library could not be loaded." }
            status = ""
            syncing = false
        }
    }
    LaunchedEffect(account) { sync(false) }

    val shown = remember(games, filter, query) {
        val q = query.trim().lowercase()
        games.filter { filter.matches(it) && (q.isEmpty() || q in it.title.lowercase()) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BannerCloud", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground)
                if (shownName.isNotEmpty()) Text(shownName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (syncing) CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp)
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Filled.Search, "Search") }
            IconButton(onClick = { sync(true) }, enabled = !syncing) { Icon(Icons.Filled.Refresh, "Refresh") }
            IconButton(onClick = { ctx.startActivity(Intent(ctx, XboxCloudSettingsActivity::class.java)) }) { Icon(Icons.Filled.Settings, "Settings") }
            IconButton(onClick = { confirmSignOut = true }) { Icon(Icons.Filled.Logout, "Sign out") }
        }
        if (searching) OutlinedTextField(value = query, onValueChange = { query = it.take(80) }, singleLine = true,
            placeholder = { Text("Search your library") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach { f ->
                val count = games.count { f.matches(it) }
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} $count") })
            }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        message?.let { text ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { message = null }) {
                Text(text, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (shown.isEmpty() && !syncing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (games.isEmpty()) "No games yet. Tap refresh to load your library." else "Nothing here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { game -> GameCard(game) { selected = game } }
            }
        }
    }

    selected?.let { game ->
        GameDialog(game, onDismiss = { selected = null }, onPlay = {
            selected = null
            ctx.startActivity(XboxCloudPlayerActivity.intent(ctx, game))
        }, onWebPlayer = {
            selected = null
            ctx.startActivity(XboxCloudActivity.intent(ctx, game.cloudUrl))
        })
    }
    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out?") },
        text = { Text("BannerCloud forgets this Microsoft account on this device.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } })
}

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

@Composable
private fun GameCard(game: XboxGame, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.75f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(model = game.posterUrl ?: game.heroUrl, contentDescription = game.title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
            if (game.cloud) Text("CLOUD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC107C10)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(game.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.heightIn(min = 40.dp))
            Text(accessLabel(game), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun accessLabel(game: XboxGame): String = listOfNotNull(
    if (game.owned) "Owned" else if (game.gamePass) "Game Pass" else null,
    if (game.cloud) "Cloud" else "PC only",
).joinToString(" · ")

@Composable
private fun GameDialog(game: XboxGame, onDismiss: () -> Unit, onPlay: () -> Unit, onWebPlayer: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(game.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                game.heroUrl?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)))
                    Spacer(Modifier.height(8.dp))
                }
                Text(accessLabel(game), fontWeight = FontWeight.Bold)
                if (game.publisher.isNotBlank()) Text(game.publisher, style = MaterialTheme.typography.bodySmall)
                if (!game.cloud) {
                    Spacer(Modifier.height(8.dp))
                    Text("Microsoft does not stream this game. It can only be installed on a PC or Xbox.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (game.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(game.description.take(600), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (game.cloud) Button(onClick = onPlay) { Text("Play") }
            else TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            if (game.cloud) Row {
                OutlinedButton(onClick = onWebPlayer) { Text("Microsoft's player") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        })
}
