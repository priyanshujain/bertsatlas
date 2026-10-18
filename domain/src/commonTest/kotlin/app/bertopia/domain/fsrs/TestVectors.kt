package app.bertopia.domain.fsrs

import app.bertopia.domain.TEST_RESOURCES_DIRECTORY
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.math.abs
import kotlin.test.assertTrue

fun readVectors(name: String): JsonObject {
    val text = SystemFileSystem.source(Path("$TEST_RESOURCES_DIRECTORY/fsrs/$name")).buffered().use { it.readString() }
    return Json.parseToJsonElement(text).jsonObject
}

fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
fun JsonObject.arr(key: String): JsonArray = getValue(key).jsonArray
fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int
fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long
fun JsonObject.double(key: String): Double = getValue(key).jsonPrimitive.double
fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
fun JsonObject.doubleOrNull(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { it.content != "null" }?.double
fun JsonElement.doubles(): List<Double> = jsonArray.map { it.jsonPrimitive.double }

fun assertClose(expected: Double, actual: Double, relative: Double, label: String) {
    val tolerance = maxOf(relative * abs(expected), relative)
    assertTrue(abs(expected - actual) <= tolerance, "$label expected $expected but was $actual")
}
