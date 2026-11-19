package com.banner.cloud.xbox

import org.json.JSONArray
import org.json.JSONObject

/**
 * One Xbox Cloud Gaming session's requests to the streaming service, as Microsoft's web player makes them
 * (Greenlight's shapes, MIT): start, state, connect (login proof token), SDP and ICE exchange, keepalive, stop.
 * Shared by the web-view player and the native player.
 */
internal class XboxCloudSignalling(
    session: XboxCloudSession.Session,
    private val titleId: String,
    private val settings: XboxCloudSettings,
    /** "cloud", or "home" for Remote Play (then [serverId] is the console and [titleId] is empty). */
    private val kind: String = "cloud",
    private val serverId: String = "",
    private val note: (String) -> Unit = {},
    /** A backup sign-in to the console cloud service, tried when the web service keeps refusing the device type. */
    private val consoleSession: (() -> XboxCloudSession.Session?)? = null,
) {
    /** Changes when the service refuses the device type the stream asked as (see [start]). */
    @Volatile private var session: XboxCloudSession.Session = session

    /** "v5/sessions/cloud/<id>" once started. */
    @Volatile var path: String? = null
        private set

    /** The web player's request names: start, state, connect, sdp (offer SDP), ice (JSON array of candidates), keepalive. */
    fun op(op: String, arg: String = ""): XboxCloudSession.Reply = when (op) {
        "start" -> start()
        "state" -> XboxCloudSession.request(session, "GET", "/$path/state")
        "connect" -> connect()
        "sdp" -> sdp(arg)
        "ice" -> ice(arg)
        "keepalive" -> XboxCloudSession.request(session, "POST", "/$path/keepalive", "{}")
        else -> XboxCloudSession.Reply(0, "unknown request")
    }

    /**
     * Starts the session. Some titles refuse some device types ("DeviceTypeNotAllowed": a console-only game such as
     * GTA VI is not streamed to Windows); then it asks again as a TV (tizen, 1080p) and as Android (720p).
     */
    fun start(): XboxCloudSession.Reply {
        if (kind != "cloud") return start(session.osName)
        var reply = startWithDeviceFallback()
        // Still refused as every device type: try the console cloud service (the sign-in says console, not web/PC).
        val console = consoleSession
        if (isDeviceBlocked(reply) && console != null) {
            val cs = runCatching { console() }.getOrNull()
            if (cs != null) {
                note("still blocked; signing in to the console service and retrying (experimental)")
                session = cs
                reply = startWithDeviceFallback()
                if (reply.status in 200..299) note("started on the console service")
            }
        }
        return reply
    }

    private fun isDeviceBlocked(reply: XboxCloudSession.Reply) = reply.status == 400 && reply.body.contains("DeviceTypeNotAllowed")

    /** Tries the session's device type, then TV and phone, while the service keeps answering DeviceTypeNotAllowed. */
    private fun startWithDeviceFallback(): XboxCloudSession.Reply {
        val first = session.osName
        var reply = start(first)
        for (os in listOf("tizen", "android").filter { it != first }) {
            if (!isDeviceBlocked(reply)) break
            note("device type ${session.osName} not allowed for this title, asking as $os")
            session = session.withOs(os)
            reply = start(os)
        }
        if (reply.status in 200..299 && session.osName != first) note("started as ${session.osName}")
        return reply
    }

    private fun start(os: String): XboxCloudSession.Reply {
        val body = JSONObject().put("clientSessionId", "").put("titleId", titleId).put("systemUpdateGroup", "")
            .put("settings", JSONObject().put("nanoVersion", "V3;WebrtcTransport.dll").put("enableOptionalDataCollection", false)
                .put("enableTextToSpeech", false).put("highContrast", 0).put("locale", settings.language.ifEmpty { java.util.Locale.getDefault().toLanguageTag() })
                .put("useIceConnection", false).put("timezoneOffsetMinutes", java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000)
                .put("sdkType", "web").put("osName", os))
            .put("serverId", serverId).put("fallbackRegionNames", JSONArray())
        return XboxCloudSession.request(session, "POST", "/v5/sessions/$kind/play", body.toString()).also { reply ->
            if (reply.status in 200..299) path = JSONObject(reply.body).getString("sessionPath").trim('/')
        }
    }

    fun connect(): XboxCloudSession.Reply {
        val token = session.userToken ?: return XboxCloudSession.Reply(0, "no cloud transfer token for this account")
        return XboxCloudSession.request(session, "POST", "/$path/connect", JSONObject().put("userToken", token).toString())
    }

    fun sdp(offer: String): XboxCloudSession.Reply {
        val body = JSONObject().put("messageType", "offer").put("sdp", offer).put("requestId", "1").put("configuration", JSONObject()
            .put("chatConfiguration", JSONObject().put("bytesPerSample", 2).put("expectedClipDurationMs", 20)
                .put("format", JSONObject().put("codec", "opus").put("container", "webm")).put("numChannels", 1).put("sampleFrequencyHz", 24000))
            .put("chat", version(1, 1)).put("control", version(1, 3)).put("input", version(1, 9)).put("message", version(1, 1))
            .put("reliableinput", version(9, 9)).put("unreliableinput", version(9, 9)))
        val posted = XboxCloudSession.request(session, "POST", "/$path/sdp", body.toString())
        return if (posted.status !in 200..299) posted else poll("/$path/sdp")
    }

    /**
     * The microphone: a second offer once a send track is on the audio transceiver, flagged as a chat
     * renegotiation (xbox-xcloud-player's stream.ts, MIT: requestId 2, isMediaStreamsChatRenegotiation).
     */
    fun chatSdp(offer: String): XboxCloudSession.Reply {
        val body = JSONObject().put("messageType", "offer").put("sdp", offer).put("requestId", "2")
            .put("configuration", JSONObject().put("isMediaStreamsChatRenegotiation", true))
        val posted = XboxCloudSession.request(session, "POST", "/$path/sdp", body.toString())
        return if (posted.status !in 200..299) posted else poll("/$path/sdp")
    }

    /** The session's configuration (Remote Play: the console's addresses for extra ICE candidates). */
    fun configuration(): XboxCloudSession.Reply = XboxCloudSession.request(session, "GET", "/$path/configuration")

    fun ice(candidates: String): XboxCloudSession.Reply {
        val posted = XboxCloudSession.request(session, "POST", "/$path/ice", JSONObject().put("candidates", JSONArray(candidates)).toString())
        return if (posted.status !in 200..299) posted else poll("/$path/ice")
    }

    /** Ends the cloud session (idempotent). */
    fun stop(): XboxCloudSession.Reply? {
        val current = path ?: return null
        path = null
        return runCatching { XboxCloudSession.request(session, "DELETE", "/$current") }.getOrNull()
    }

    private fun version(min: Int, max: Int) = JSONObject().put("minVersion", min).put("maxVersion", max)

    /** The exchange answers 204 until the console has replied. */
    private fun poll(path: String): XboxCloudSession.Reply {
        repeat(60) {
            val reply = XboxCloudSession.request(session, "GET", path)
            if (reply.status != 204) return reply
            Thread.sleep(500)
        }
        return XboxCloudSession.Reply(0, "no answer from the cloud console")
    }
}
