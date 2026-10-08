package twee.inspections

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import twee.parser.TweeJson
import twee.settings.TweeCheckSettings

/** Port of src/twee-project.ts field-presence checks and uuid 10 acceptance; no schema validation. */
object StoryDataChecks {
    data class Result(val malformedJson: Boolean, val messages: List<String>)
    private val uuid = Regex("(?:[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)", RegexOption.IGNORE_CASE)
    fun validIfid(value: String) = uuid.matches(value)
    private fun truthy(value: JsonElement?): Boolean {
        if (value == null || value.isJsonNull) return false
        if (!value.isJsonPrimitive) return true // Empty JS arrays/objects are truthy.
        val primitive = value.asJsonPrimitive
        return when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isNumber -> primitive.asDouble != 0.0
            else -> primitive.asString.isNotEmpty()
        }
    }
    fun evaluate(text: String, options: TweeCheckSettings.Options): Result {
        if (!TweeJson.valid(text)) return Result(true, listOf("Malformed StoryData JSON!"))
        val data = JsonParser.parseString(text)
        // JSON null is valid syntax, but the inherited property access fails during project validation.
        if (data.isJsonNull) return Result(false, listOf("Malformed StoryData JSON: Cannot read properties of null"))
        fun field(name: String): JsonElement? = if (data.isJsonObject) data.asJsonObject.get(name) else null
        val errors = mutableListOf<String>()
        val ifid = field("ifid")
        if (options.ifid) {
            if (!truthy(ifid)) errors.add("IFID not found!")
            else if (ifid == null || !ifid.isJsonPrimitive || !ifid.asJsonPrimitive.isString || !validIfid(ifid.asString)) errors.add("Invalid IFID!")
        }
        if (options.format && !truthy(field("format"))) errors.add("Story Format name not found!")
        if (options.formatVersion && !truthy(field("format-version"))) errors.add("Story Format version not found!")
        return Result(false, errors.map { "Malformed StoryData: $it" })
    }
}
