package com.example.unaparoladelgiorno.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

/**
 * Scrapes unaparolaalgiorno.it directly (no WebView) and returns a [WordOfDay].
 *
 * The site is a Nuxt app that gets redesigned from time to time, so nothing
 * here depends on one exact markup shape:
 *
 *  1. Home page -> today's word link. We look for a link to /significato/<slug>
 *     inside a heading (h1, then h2, then h3). If headings change again we fall
 *     back to the first /significato/ link in the body: the navigation has none,
 *     and today's word comes before the "Recenti" list of older words.
 *  2. Word page -> read as plain text in document order and sliced on the
 *     labels "significato" / "etimologia" (case-insensitive: older layouts used
 *     "Significato", the current one uses lowercase), the «...» example, and the
 *     "Parola pubblicata il" date line.
 *  3. Full article ([WordOfDay.fullText], sanitized HTML for Html.fromHtml) ->
 *     the paragraphs that follow the example, up to the "Parola pubblicata il"
 *     line. Legacy container selectors and a plain-text slice are kept as
 *     fallbacks.
 *  4. If slicing finds no definition, fall back to the og:description meta tag
 *     ("Word - definition. Etimo: ...").
 *
 * Failures throw [ScrapeException] with a readable message.
 */
object WordScraper {

    private const val BASE_URL = "https://unaparolaalgiorno.it"
    private const val USER_AGENT =
        "Mozilla/5.0 (Android; Mobile) UnaParolaWidget/1.0 (widget personale, non commerciale)"
    private const val TIMEOUT_MS = 15_000
    private const val PUBLISHED_MARKER = "Parola pubblicata il"

    private val SIGNIFICATO = Regex("(?i)\\bsignificato\\b")
    private val ETIMOLOGIA = Regex("(?i)\\betimologia\\b")

    suspend fun fetchTodayWord(): WordOfDay = withContext(Dispatchers.IO) {
        val homeDoc = fetchDocument(BASE_URL)
        val link = findTodayLink(homeDoc)
            ?: throw ScrapeException(
                "Impossibile individuare la parola del giorno nella home page " +
                    "(la struttura del sito potrebbe essere cambiata)."
            )

        val href = link.attr("href")
        val detailUrl = link.absUrl("href").ifBlank {
            if (href.startsWith("http")) href else BASE_URL + "/" + href.trimStart('/')
        }
        fetchWordFromDetailPage(detailUrl)
    }

    private fun findTodayLink(doc: Document): Element? =
        doc.select("h1 a[href*=/significato/]").firstOrNull()
            ?: doc.select("h2 a[href*=/significato/]").firstOrNull()
            ?: doc.select("h3 a[href*=/significato/]").firstOrNull()
            ?: doc.select("a[href*=/significato/]").firstOrNull()

    suspend fun fetchWordFromDetailPage(url: String): WordOfDay = withContext(Dispatchers.IO) {
        parseWordPage(fetchDocument(url), url)
    }

    /** Pure parsing step (no network), so it can be unit-tested against saved HTML. */
    internal fun parseWordPage(doc: Document, url: String): WordOfDay {
        val word = doc.select("h1").first()?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ScrapeException("Titolo della parola non trovato in $url")

        val bodyText = doc.body().text()
        val afterTitle = bodyText.substringAfter(word, "").trim()
        if (afterTitle.isBlank()) {
            throw ScrapeException("Contenuto della pagina non riconosciuto per \"$word\"")
        }

        // Text between the title and the "significato" label is the syllabication,
        // possibly followed by a "#commenti" link on the current layout.
        val sig = SIGNIFICATO.find(afterTitle)
        val syllabication = sig
            ?.let { afterTitle.substring(0, it.range.first) }
            ?.replace("#commenti", "")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length < 40 && !it.contains(' ') }

        val afterSignificato = sig?.let { afterTitle.substring(it.range.last + 1) } ?: ""
        val eti = ETIMOLOGIA.find(afterSignificato)

        var definition: String? =
            (if (eti != null) afterSignificato.substring(0, eti.range.first) else "")
                .trim()
                .ifBlank { null }

        val afterEtimologia = eti?.let { afterSignificato.substring(it.range.last + 1) } ?: ""
        val etymology = afterEtimologia
            .substringBefore("\u00AB", afterEtimologia)
            .trim()
            .ifBlank { null }

        val example = Regex("\u00AB([^\u00BB]{3,400})\u00BB")
            .find(afterEtimologia)
            ?.groupValues?.get(1)?.trim()

        val publishedDate = Regex("$PUBLISHED_MARKER\\s+([0-9]{1,2}\\s+\\p{L}+\\s+[0-9]{4})")
            .find(bodyText)
            ?.groupValues?.get(1)

        if (definition == null) {
            // og:description looks like "Word - definition. Etimo: ..."
            definition = doc.select("meta[property=og:description]").attr("content")
                .substringAfter(" - ", "")
                .substringBefore(". Etimo", "")
                .trim()
                .ifBlank { null }
        }

        if (definition == null) {
            throw ScrapeException(
                "Definizione non trovata per \"$word\" (layout della pagina cambiato?)"
            )
        }

        val fullHtml = extractArticleHtml(doc, example, url)
            ?: legacyArticleHtml(doc)
            ?: afterEtimologia.substringAfter(example ?: "", "")
                .substringBefore(PUBLISHED_MARKER)
                .trim()
                .takeIf { example != null && it.isNotBlank() }

        return WordOfDay(
            word = word,
            syllabication = syllabication,
            definition = definition,
            etymology = etymology,
            example = example,
            publishedDate = publishedDate,
            fullText = fullHtml?.takeIf { it.isNotBlank() },
            sourceUrl = url,
            fetchedAtEpochMillis = System.currentTimeMillis()
        )
    }

    /**
     * Collects the article paragraphs that follow the usage example, stopping at the
     * "Parola pubblicata il ..." line or the page footer, and returns them as
     * sanitized HTML (basic tags only) ready for Html.fromHtml.
     */
    private fun extractArticleHtml(doc: Document, example: String?, url: String): String? {
        val marker = example?.take(25)?.takeIf { it.isNotBlank() } ?: return null
        val elements = doc.body().allElements.toList()

        // Deepest element holding the example sentence.
        val start = elements.indexOfFirst { el ->
            el.text().contains(marker) && el.children().none { it.text().contains(marker) }
        }
        if (start < 0) return null

        val paragraphs = elements.drop(start + 1)
            .filter { it.tagName() == "p" }
            .takeWhile { p ->
                !p.text().contains(PUBLISHED_MARKER) && p.parents().none { it.tagName() == "footer" }
            }
            .filter { it.text().isNotBlank() }
        if (paragraphs.isEmpty()) return null

        val html = paragraphs.joinToString("") { "<p>${it.html()}</p>" }
        return Jsoup.clean(html, url, Safelist.basic()).trim().ifBlank { null }
    }

    /** Older layouts exposed the article inside a dedicated container. */
    private fun legacyArticleHtml(doc: Document): String? {
        val container = doc.select(".wp-content, .article-content, #article-body").firstOrNull()
            ?: return null
        return Jsoup.clean(container.html(), Safelist.basic())
            .substringBefore(PUBLISHED_MARKER)
            .trim()
            .ifBlank { null }
    }

    private fun fetchDocument(url: String): Document =
        Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS)
            .get()
}
