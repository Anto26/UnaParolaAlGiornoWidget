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
- **`WordWidget`**: Renders the Glance widget. Supports compact and expanded states.
- **`WordRefreshWorker`**: Periodically updates the word in the background.

## Coding Guidelines
- **Glance Interoperability**: When rendering HTML with styles (bold, italics, etc.) in Glance, use `AndroidRemoteViews` with a custom `TextView` layout (e.g., `res/layout/widget_html_text.xml`) and `Html.fromHtml`.
- **Widget State**: Use `PreferencesGlanceStateDefinition` for widget-specific state (like expanded/compact toggle).
- **Asynchronous Work**: Always use Kotlin Coroutines.
- **HTML Cleanup**: Use `Jsoup.clean` with `Safelist.basic()` when extracting article content to ensure compatibility and security.

## Common Tasks
- **Updating Widget**: When data changes, call `WordWidget().updateAll(context)`.
- **Modifying Scraper**: Be aware that the scraper relies on CSS selectors (`.wp-content`, etc.) and text-based markers ("Parola pubblicata il"). Always verify these if the site layout changes.
