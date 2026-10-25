package com.imad.itube.account

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Small helpers for digging through InnerTube responses, which nest everything deeply. */
internal fun JsonElement?.at(vararg path: Any): JsonElement? {
    var cur: JsonElement? = this
    for (p in path) {
        cur = when (p) {
            is String -> (cur as? JsonObject)?.get(p)
            is Int -> (cur as? JsonArray)?.getOrNull(p)
            else -> null
        } ?: return null
    }
    return cur
}

internal fun JsonElement?.str(vararg path: Any): String? = (at(*path) as? JsonPrimitive)?.contentOrNull

/** Text from either `{simpleText}`, `{runs:[{text}]}` or `{content}`. */
internal fun JsonElement?.text(vararg path: Any): String? {
    val node = at(*path) ?: return null
    node.str("simpleText")?.let { return it }
    node.str("content")?.let { return it }
    val runs = node.at("runs") as? JsonArray ?: return (node as? JsonPrimitive)?.contentOrNull
    return runs.joinToString("") { it.str("text").orEmpty() }.ifBlank { null }
}

/** Every object stored under [key], anywhere below this node, in document order. */
internal fun JsonElement.findAll(key: String, out: MutableList<JsonObject> = mutableListOf()): List<JsonObject> {
    when (this) {
        is JsonObject -> for ((k, v) in this) {
            if (k == key && v is JsonObject) out += v else v.findAll(key, out)
        }
        is JsonArray -> forEach { it.findAll(key, out) }
        else -> {}
    }
    return out
}

internal fun JsonElement.findFirst(key: String): JsonObject? {
    when (this) {
        is JsonObject -> for ((k, v) in this) {
            if (k == key && v is JsonObject) return v
            v.findFirst(key)?.let { return it }
        }
        is JsonArray -> forEach { el -> el.findFirst(key)?.let { return it } }
        else -> {}
    }
    return null
}

/** Last (largest) url of a `{thumbnails:[...]}` or `{sources:[...]}` list. */
internal fun JsonElement?.lastImage(): String? {
    val list = (at("thumbnails") ?: at("sources")) as? JsonArray ?: return null
    val url = list.lastOrNull().str("url") ?: return null
    return if (url.startsWith("//")) "https:$url" else url
}
