package com.galaxyrio.gracelauncher.data.licenses

import android.content.Context
import com.galaxyrio.gracelauncher.R
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

internal data class LicenseLink(val name: String, val url: String?)

internal data class LibraryLicense(
    val id: String,
    val name: String,
    val artifactId: String,
    val version: String?,
    val developers: List<String>,
    val website: String?,
    val licenses: List<LicenseLink>,
) {
    fun matches(query: String): Boolean {
        val term = query.trim()
        return term.isEmpty() || listOfNotNull(name, artifactId, version, website)
            .plus(developers).plus(licenses.map { it.name })
            .any { it.contains(term, ignoreCase = true) }
    }
}

/** Same generated dependency catalog as Sudoku, scoped to this app's build. */
internal class LicensesRepository(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun getLibraries(): List<LibraryLicense> = withContext(Dispatchers.IO) {
        Libs.Builder().withJson(applicationContext, R.raw.aboutlibraries).build().libraries.map { library ->
            LibraryLicense(
                id = library.uniqueId,
                name = library.name,
                artifactId = library.artifactId,
                version = library.artifactVersion,
                developers = library.developers.mapNotNull { it.name },
                website = library.website.toWebUrlOrNull() ?: library.scm?.url.toWebUrlOrNull(),
                licenses = library.licenses.map { license ->
                    LicenseLink(
                        name = license.name,
                        url = license.url.toWebUrlOrNull() ?: license.spdxId.toSpdxLicenseUrlOrNull(),
                    )
                }.sortedBy { it.name },
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}

internal fun String?.toWebUrlOrNull(): String? {
    val value = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    return value.takeIf {
        (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) && !uri.host.isNullOrBlank()
    }
}

internal fun String?.toSpdxLicenseUrlOrNull(): String? {
    val id = this?.trim()?.takeIf(SpdxIdPattern::matches) ?: return null
    return "https://spdx.org/licenses/$id.html"
}

private val SpdxIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9.+-]*")
