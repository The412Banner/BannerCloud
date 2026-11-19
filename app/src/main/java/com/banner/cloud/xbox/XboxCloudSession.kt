package com.banner.cloud.xbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Xbox Cloud Gaming sign-in from the store's own account: Xodus' `cloud-token` turns the stored Microsoft
 * account into a streaming-service token (no web sign-in), kept in memory only until shortly before it
 * expires. The request shapes follow Greenlight / xal-node by unknownskl (MIT).
 */
internal object XboxCloudSession {
    class Region(val name: String, val baseUri: String, val isDefault: Boolean)

    class Session(val account: String, val token: String, val offering: String, val baseUri: String, val regionList: List<Region>,
                  /** Microsoft account token for a session's /connect step; null when the account could not get one. */
                  val userToken: String?, private val expiresAt: Long,
                  /** The os name sent with each request (it selects the stream resolution). */
                  val osName: String = "windows") {
        val fresh get() = System.currentTimeMillis() < expiresAt - 5 * 60_000L
        val regions get() = regionList.size

        /** This session against [regionName]'s server (Microsoft's default when empty or unknown), asking as [os]. */
        fun forStream(regionName: String, os: String): Session = Session(account, token, offering,
            regionList.firstOrNull { it.name == regionName }?.baseUri ?: baseUri, regionList, userToken, expiresAt, os)
    }

    @Volatile private var cached: Session? = null

    @Volatile private var cachedBypass: String = "off"

    fun get(app: Context, account: String): Session {
        val bypass = XboxCloudSettings.load(app).bypassRegion
        cached?.takeIf { it.account == account && it.fresh && cachedBypass == bypass }?.let { return it }
        // Region bypass (Better xCloud's): the backend's cloud login presents an address in that country.
        val env = XboxCloudSettings.BYPASS_IPS[bypass]?.let { mapOf("NEXUS_CLOUD_FORWARDED_FOR" to it) } ?: emptyMap()
        if (env.isNotEmpty()) XboxDiag.log(app, "cloud sign-in with region bypass: $bypass")
        val json = Xodus.run(app, listOf("cloud-token"), timeout = 60, extraEnv = env)
        cachedBypass = bypass
        require(json.getInt("schema") == 1 && json.getString("account") == account)
        val array = json.getJSONArray("regions")
        val regions = (0 until array.length()).map { array.getJSONObject(it) }
            .map { Region(it.getString("name"), it.getString("baseUri"), it.optBoolean("isDefault")) }
        val default = regions.firstOrNull { it.isDefault } ?: regions.firstOrNull() ?: error("No Xbox Cloud Gaming region")
        val seconds = json.optLong("durationInSeconds").coerceIn(0, 24 * 3600)
        return Session(account, json.getString("gsToken"), json.getString("offering"), default.baseUri, regions,
            json.optString("userToken").takeIf { !json.isNull("userToken") && it.isNotEmpty() }, System.currentTimeMillis() + seconds * 1000).also { cached = it }
    }

    fun clear() { cached = null; cachedHome = null; titleIds = null; looked = null }

    // ---- Remote Play (your own Xbox) ----

    class Console(val serverId: String, val name: String, val type: String, val power: String)

    @Volatile private var cachedHome: Session? = null

    /**
     * The Remote Play (xHome) session: the backend's login to the "xhome" offering (NEXUS_CLOUD_OFFERING=xhome;
     * xhome.gssv-play-prod.xboxlive.com/v2/login/user, as Better xCloud's remote-play-manager, MIT).
     */
    fun getHome(app: Context, account: String): Session {
        cachedHome?.takeIf { it.account == account && it.fresh }?.let { return it }
        val json = Xodus.run(app, listOf("cloud-token"), timeout = 60, extraEnv = mapOf("NEXUS_CLOUD_OFFERING" to "xhome"))
        require(json.getInt("schema") == 1 && json.getString("account") == account)
        val array = json.getJSONArray("regions")
        val regions = (0 until array.length()).map { array.getJSONObject(it) }
            .map { Region(it.getString("name"), it.getString("baseUri"), it.optBoolean("isDefault")) }
        val default = regions.firstOrNull { it.isDefault } ?: regions.firstOrNull() ?: error("No Remote Play region")
        val seconds = json.optLong("durationInSeconds").coerceIn(0, 24 * 3600)
        return Session(account, json.getString("gsToken"), json.getString("offering"), default.baseUri, regions,
            json.optString("userToken").takeIf { !json.isNull("userToken") && it.isNotEmpty() }, System.currentTimeMillis() + seconds * 1000).also { cachedHome = it }
    }

    /** The account's consoles with Remote Play on (GET /v6/servers/home?mr=50, default region first, first non-empty answer). */
    fun consoles(session: Session): Pair<Session, List<Console>> {
        val order = session.regionList.sortedByDescending { it.isDefault }
        var last: Pair<Session, List<Console>> = session to emptyList()
        for (region in order) {
            val regional = session.forStream(region.name, session.osName)
            val reply = runCatching { request(regional, "GET", "/v6/servers/home?mr=50") }.getOrNull() ?: continue
            if (reply.status != 200) continue
            val results = runCatching { JSONObject(reply.body).optJSONArray("results") }.getOrNull() ?: JSONArray()
            val list = (0 until results.length()).mapNotNull { results.optJSONObject(it) }.mapNotNull { c ->
                val id = c.optString("serverId").takeIf { it.isNotBlank() && it.length <= 64 } ?: return@mapNotNull null
                Console(id, c.optString("deviceName").ifBlank { "Xbox" }.take(60), c.optString("consoleType"), c.optString("powerState"))
            }
            last = regional to list
            if (list.isNotEmpty()) return last
        }
        return last
    }

    @Volatile private var titleIds: Pair<String, Map<String, String>>? = null

    /** Store product id -> cloud title id for the titles this account may stream, from the streaming service itself. */
    fun titles(session: Session): Map<String, String> {
        titleIds?.takeIf { it.first == session.account }?.let { return it.second }
        val reply = request(session, "GET", "/v2/titles")
        check(reply.status == 200) { "Xbox Cloud Gaming answered ${reply.status}" }
        val results = JSONObject(reply.body).optJSONArray("results") ?: JSONArray()
        val map = (0 until results.length()).mapNotNull { index ->
            val row = results.optJSONObject(index) ?: return@mapNotNull null
            val product = row.optJSONObject("details")?.optString("productId")?.uppercase().orEmpty()
            val title = row.optString("titleId")
            if (product.matches(Regex("[A-Z0-9]{12}")) && title.isNotBlank() && title.length <= 64) product to title else null
        }.toMap()
        titleIds = session.account to map
        return map
    }

    /**
     * Microsoft's estimated wait for a free cloud console for [titleId], in seconds (GET /v1/waittime/{titleId}, as
     * Better xCloud's getWaitTime, MIT); null when unknown.
     */
    fun waitSeconds(session: Session, titleId: String): Int? = runCatching {
        val reply = request(session, "GET", "/v1/waittime/$titleId")
        if (reply.status != 200) return@runCatching null
        val json = JSONObject(reply.body)
        val total = json.optInt("estimatedTotalWaitTimeInSeconds", -1)
        if (total >= 0) total else {
            val parts = listOf("estimatedProvisioningTimeInSeconds", "estimatedAllocationTimeInSeconds").map { json.optInt(it, -1) }
            if (parts.all { it < 0 }) null else parts.filter { it >= 0 }.sum()
        }
    }.getOrNull()

    /** "about 2 min" / "under a minute" for [seconds]. */
    fun waitText(seconds: Int): String = when {
        seconds < 60 -> "under a minute"
        seconds < 3600 -> "about ${(seconds + 30) / 60} min"
        else -> "about ${seconds / 3600} h ${(seconds % 3600) / 60} min"
    }

    @Volatile private var looked: Pair<String, MutableMap<String, String>>? = null

    /**
     * The cloud title id for one Store product: the account's title list first, else Microsoft's lookup by
     * product id (POST /v2/titles with alternateIds, as Better xCloud's getTitleInfo, MIT). The list does not
     * include every game the account can stream (games it owns outside Game Pass), the lookup does.
     */
    fun titleFor(session: Session, productId: String, name: String = "", log: (String) -> Unit = {}): String? {
        val product = productId.uppercase()
        val list = titles(session)
        list[product]?.let { return it }
        val cache = looked?.takeIf { it.first == session.account }?.second ?: HashMap<String, String>().also { looked = session.account to it }
        cache[product]?.let { return it }
        // 1. Microsoft's lookup by product id. Its answer may name another product (the edition the service
        //    streams); its title id is still the one to start.
        val body = JSONObject().put("alternateIds", JSONArray().put(product)).put("alternateIdType", "productId").toString()
        val reply = runCatching { request(session, "POST", "/v2/titles", body) }.getOrNull()
        val rows = reply?.takeIf { it.status == 200 }?.let { runCatching { JSONObject(it.body).optJSONArray("results") }.getOrNull() }
        val row = rows?.optJSONObject(0)
        val title = row?.optString("titleId").orEmpty()
        val rowProduct = row?.optJSONObject("details")?.optString("productId")?.uppercase().orEmpty()
        log("title lookup $product: http ${reply?.status ?: -1}, results ${rows?.length() ?: -1}, title ${title.ifEmpty { "-" }}, product ${rowProduct.ifEmpty { "-" }}")
        if (title.isNotBlank() && title.length <= 64) { cache[product] = title; return title }
        // 2. The service's title ids are the game's name in capitals ("FORTNITE", "FORZAHORIZON5"): match the
        //    card's name without its edition words against the account's list.
        val key = nameKey(name)
        if (key.length >= 4) {
            val byName = list.values.filter { it.uppercase() == key }.distinct()
            log("title by name $key: ${byName.joinToString().ifEmpty { "none" }}; similar ${list.values.filter { key.take(6) in it.uppercase() }.distinct().take(8).joinToString()}")
            if (byName.size == 1) { cache[product] = byName[0]; return byName[0] }
        }
        return null
    }

    private val EDITION_WORDS = Regex("(STANDARD|DELUXE|PREMIUM|ULTIMATE|GOLD|COMPLETE|DEFINITIVE|ENHANCED|ANNIVERSARY|GAMEOFTHEYEAR|GOTY)?EDITION$|GAMEPREVIEW$|FORWINDOWS(10)?$|XBOXSERIESXS$|XBOXONE$|PC$")

    /** "Forza Horizon 6 Standard Edition" -> "FORZAHORIZON6". */
    internal fun nameKey(name: String): String {
        var key = name.uppercase().replace(Regex("[^A-Z0-9]"), "")
        repeat(3) { key = key.replace(EDITION_WORDS, "") }
        return key
    }

    class Reply(val status: Int, val body: String)

    /** One request to the streaming service as Microsoft's web player makes it (Greenlight's shapes). */
    fun request(session: Session, method: String, path: String, body: String? = null): Reply {
        val connection = URL(session.baseUri + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer ${session.token}")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Gssv-Client", "XboxComBrowser")
            connection.setRequestProperty("X-MS-Device-Info", deviceInfo(session.osName))
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes() }?.also { require(it.size <= 16_000_000) } ?: ByteArray(0)
            return Reply(status, String(text, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }

    private fun deviceInfo(os: String) = JSONObject()
        .put("appInfo", JSONObject().put("env", JSONObject().put("clientAppId", "www.xbox.com").put("clientAppType", "browser")
            .put("clientAppVersion", "21.1.98").put("clientSdkVersion", "8.5.3").put("httpEnvironment", "prod").put("sdkInstallId", "")))
        .put("dev", JSONObject()
            .put("hw", JSONObject().put("make", "Microsoft").put("model", "unknown").put("sdktype", "web"))
            .put("os", JSONObject().put("name", os).put("ver", "22631.2715").put("platform", "desktop"))
            .put("displayInfo", JSONObject().put("dimensions", JSONObject().put("widthInPixels", 1920).put("heightInPixels", 1080))
                .put("pixelDensity", JSONObject().put("dpiX", 2).put("dpiY", 2)))
            .put("browser", JSONObject().put("browserName", "chrome").put("browserVersion", "119.0")))
        .toString()
}
