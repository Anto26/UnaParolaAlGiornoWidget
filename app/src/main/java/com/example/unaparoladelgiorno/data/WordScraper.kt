package com.example.unaparoladelgiorno.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist

/**
 * Scrapes unaparolaalgiorno.it directly (no WebView, no rendering the page
 * for the user) and returns a structured [WordOfDay].
 *
 * Approach, in two steps:
 *  1. The home page is fetched to find *which* word is today's word. It is
 *     always presented under the "La parola del giorno" heading as an <h2>
 *     whose link points to /significato/<slug>. Other <h2> elements on the
 *     home page (e.g. the shop banner) never link there, so that selector
 *     reliably isolates the right one without depending on brittle CSS
 *     classes.
 *  2. The word's own dedicated page (/significato/<slug>) is fetched, which
 *     has a very consistent, site-wide layout: title, syllabication,
 *     "Significato" label + definition, "Etimologia" label + etymology, then
 *     a quoted usage example in « » before the long-form article text. We
 *     read the page as plain text (in document order) and slice it using
 *     those labels/markers rather than hard-coding CSS class names, which
 *     tend to change whenever the front-end framework rebuilds its bundle.
 *
 * If the site's markup changes enough to break these heuristics, the calls
 * below will throw [ScrapeException] with a description of what step failed,
 * which the repository/worker surfaces instead of silently showing garbage.
 */
object WordScraper {

    private const val BASE_URL = "https://unaparolaalgiorno.it"
    private const val USER_AGENT =
        "Mozilla/5.0 (Android; Mobile) UnaParolaWidget/1.0 (widget personale, non commerciale)"
    private const val TIMEOUT_MS = 15_000

    suspend fun fetchTodayWord(): WordOfDay = withContext(Dispatchers.IO) {
        val homeDoc = fetchDocument(BASE_URL)

        // The daily word is the only <h2> on the home page whose link points
        // to a /significato/ detail page.
        val wordLink = homeDoc.select("h2 a[href*=/significato/]").firstOrNull()
            ?: throw ScrapeException(
                "Impossibile individuare la parola del giorno nella home page " +
                    "(la struttura del sito potrebbe essere cambiata)."
            )

        val href = wordLink.attr("href")
        val detailUrl = wordLink.absUrl("href").ifBlank {
            if (href.startsWith("http")) href else BASE_URL.trimEnd('/') + "/" + href.trimStart('/')
        }

        fetchWordFromDetailPage(detailUrl)
    }

    suspend fun fetchWordFromDetailPage(url: String): WordOfDay = withContext(Dispatchers.IO) {
        val doc = fetchDocument(url)

        val word = doc.select("h1").first()?.text()?.trim()
            ?: throw ScrapeException("Titolo della parola non trovato in $url")

        val bodyText = doc.body().text()
        val afterTitle = bodyText.substringAfter(word, "").trim()
        if (afterTitle.isBlank()) {
            throw ScrapeException("Contenuto della pagina non riconosciuto per \"$word\"")
        }

        val syllabication = afterTitle
            .substringBefore("Significato", "")
            .trim()
            .takeIf { it.isNotBlank() && it.length < 40 }

        val afterSignificato = afterTitle.substringAfter("Significato", "")
        val definition = afterSignificato
            .substringBefore("Etimologia", "")
            .trim()
            .ifBlank { null }

        val afterEtimologia = afterSignificato.substringAfter("Etimologia", "")
        val etymology = afterEtimologia
            .substringBefore("«", afterEtimologia)
            .trim()
            .ifBlank { null }

        val example = Regex("«([^»]{3,400})»")
            .find(afterEtimologia)
            ?.groupValues
            ?.get(1)
            ?.trim()

        val fullTextElement = doc.select(".wp-content, .article-content, #article-body, article").firstOrNull()
        val fullHtml = if (fullTextElement != null) {
            Jsoup.clean(fullTextElement.html(), Safelist.basic())
        } else {
            afterEtimologia.substringAfter(example ?: "", "").trim()
                .replace("\n", "<br>")
        }.substringBefore("Parola pubblicata il").trim()

        val publishedDate = Regex("Parola pubblicata il\\s+([0-9]{1,2}\\s+\\p{L}+\\s+[0-9]{4})")
            .find(bodyText)
            ?.groupValues
            ?.get(1)

        WordOfDay(
            word = word,
            syllabication = syllabication,
            definition = definition ?: "",
            etymology = etymology,
            example = example,
            publishedDate = publishedDate,
            fullText = fullHtml.takeIf { it.isNotBlank() },
            sourceUrl = url,
            fetchedAtEpochMillis = System.currentTimeMillis()
        )
    }

    private fun fetchDocument(url: String): Document =
        Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS)
            .get()
}
