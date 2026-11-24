# UnaParolaWidget Project Instructions

This project is a non-official Android widget for the website [unaparolaalgiorno.it](https://unaparolaalgiorno.it). It scrapes the "Word of the Day" and displays it on the home screen.

## Technical Stack
- **UI (App)**: Jetpack Compose.
- **UI (Widget)**: Jetpack Glance (version 1.1.1).
- **Network/Parsing**: Jsoup for scraping HTML.
- **Data Persistence**: DataStore Preferences (cached word as JSON).
- **Background Work**: WorkManager for daily word refreshes.

## Architecture
- **`WordScraper`**: Handles Jsoup connection and parsing. It extracts metadata (word, syllabication, definition, etc.) and full HTML article content.
- **`WordRepository`**: The single source of truth. It manages the DataStore cache and triggers refreshes.
- **`SettingsRepository`**: DataStore-backed user settings. Holds the text-size multiplier (`font_scale`, 0.8-1.8) chosen from the app; `WordWidget.provideGlance()` reads it on every redraw and all widget text sizes go through `scaled(base, fontScale)`. After changing it, call `WordWidget().updateAll(context)`.
- **`WordWidget`**: Renders the Glance widget. Supports compact and expanded states. The expanded ("Mostra tutto") state is a `LazyColumn` reading view: title, significato, etimologia/esempio cards, then the article as one `AndroidRemoteViews` item per paragraph (`widget_html_text.xml`: serif, 1.3 line spacing, cream links), then a footer.
- **`WordRefreshWorker`**: Periodically updates the word in the background.

## Coding Guidelines
- **Glance Interoperability**: When rendering HTML with styles (bold, italics, etc.) in Glance, use `AndroidRemoteViews` with a custom `TextView` layout (e.g., `res/layout/widget_html_text.xml`) and `Html.fromHtml`.
- **Widget State**: Use `PreferencesGlanceStateDefinition` for widget-specific state (like expanded/compact toggle).
- **Asynchronous Work**: Always use Kotlin Coroutines.
- **HTML Cleanup**: Use `Jsoup.clean` with `Safelist.basic()` when extracting article content to ensure compatibility and security.

## Common Tasks
- **Updating Widget**: When data changes, call `WordWidget().updateAll(context)`.
- **Modifying Scraper**: `WordScraper` is deliberately tolerant. The site has already changed layout once (home-page word moved from `<h2>` to `<h1>`; word-page labels went from "Significato"/"Etimologia" to lowercase with a `#commenti` link in between). Today's word is found via a `/significato/` link inside `h1`, then `h2`, `h3`, then the first such link on the page. The word page is read as text and sliced on the case-insensitive labels `significato` / `etimologia`, the `«…»` example and the "Parola pubblicata il" line; `og:description` is the fallback for the definition. `fullText` is the sanitized HTML of the `<p>` elements after the example (legacy `.wp-content`-style containers are kept as a fallback). Prefer adding fallbacks over replacing old behaviour, and keep `parseWordPage` free of network access so it stays testable.
