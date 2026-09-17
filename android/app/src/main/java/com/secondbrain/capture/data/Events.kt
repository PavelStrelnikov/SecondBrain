package com.secondbrain.capture.data

import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Сборка JSON-события в формате, который принимает ядро: POST /api/events. */
object Events {
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun isoTime(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(iso)

    fun build(
        key: String,
        type: String,
        source: String,
        direction: String?,
        occurredAtMs: Long,
        deviceId: String,
        payload: JSONObject,
    ): JSONObject = JSONObject().apply {
        put("event_key", key)
        put("type", type)
        put("source", source)
        if (direction != null) put("direction", direction)
        put("occurred_at", isoTime(occurredAtMs))
        put("device_id", deviceId)
        put("payload", payload)
    }

    fun sha1(vararg parts: Any?): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(parts.joinToString("||") { it?.toString() ?: "" }.toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
