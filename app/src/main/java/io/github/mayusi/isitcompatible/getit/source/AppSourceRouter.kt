package io.github.mayusi.isitcompatible.getit.source

import io.github.mayusi.isitcompatible.getit.manifest.AppEntry
import io.github.mayusi.isitcompatible.getit.manifest.SourceKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the right [AppSource] for an [AppEntry] and calls into it.
 *
 * Everything outside `source/` talks to this. Adding a new source
 * type means writing one new implementation and adding it to the `when`.
 */
@Singleton
class AppSourceRouter @Inject constructor(
    private val github: GitHubReleasesSource,
    private val gitea: GiteaSource,
    private val htmlScrape: HtmlScrapeSource,
) {
    suspend fun resolve(entry: AppEntry): ResolveResult = when (entry.source) {
        SourceKind.GITHUB -> github.resolve(entry)
        SourceKind.GITEA -> gitea.resolve(entry)
        SourceKind.HTML_SCRAPE -> htmlScrape.resolve(entry)
        SourceKind.UNKNOWN -> ResolveResult.Failed("Source not supported yet")
    }
}
