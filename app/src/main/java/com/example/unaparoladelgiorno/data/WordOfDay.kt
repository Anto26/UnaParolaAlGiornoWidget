package com.example.unaparoladelgiorno.data

import org.json.JSONObject

/**
 * A single "parola del giorno" entry, as scraped from unaparolaalgiorno.it.
 * All fields except [word], [definition] and [sourceUrl] are best-effort: the
 * site occasionally omits a syllabication or keeps an entry very short, so we
 * degrade gracefully instead of failing the whole scrape.
 */
data class WordOfDay(
    val word: String,
    val syllabication: String?,
    val definition: String,
    val etymology: String?,
    val example: String?,
    val publishedDate: String?,
    val fullText: String?,
    val sourceUrl: String,
    val fetchedAtEpochMillis: Long
) {
    fun toJson(): String = JSONObject().apply {
        put("word", word)
        put("syllabication", syllabication)
        put("definition", definition)
        put("etymology", etymology)
        put("example", example)
        put("publishedDate", publishedDate)
        put("fullText", fullText)
        put("sourceUrl", sourceUrl)
        put("fetchedAtEpochMillis", fetchedAtEpochMillis)
    }.toString()

    companion object {
        fun fromJson(json: String): WordOfDay {
            val o = JSONObject(json)
            fun optStr(key: String): String? = if (o.isNull(key)) null else o.optString(key)
            return WordOfDay(
                word = o.getString("word"),
                syllabication = optStr("syllabication"),
                definition = o.optString("definition"),
                etymology = optStr("etymology"),
                example = optStr("example"),
                publishedDate = optStr("publishedDate"),
                fullText = optStr("fullText"),
                sourceUrl = o.getString("sourceUrl"),
                fetchedAtEpochMillis = o.optLong("fetchedAtEpochMillis")
            )
        }
    }
}

class ScrapeException(message: String) : Exception(message)
