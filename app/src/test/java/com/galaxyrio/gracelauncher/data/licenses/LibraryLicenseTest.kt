package com.galaxyrio.gracelauncher.data.licenses

import org.junit.Assert.*
import org.junit.Test

class LibraryLicenseTest {
    private val library = LibraryLicense(
        "material", "Material Components", "androidx.compose.material3:material3-android", "1.5.0-alpha26",
        listOf("The Android Open Source Project"), "https://developer.android.com/jetpack/compose",
        listOf(LicenseLink("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0")),
    )

    @Test fun searchMatchesActualNameCoordinatesVersionDeveloperAndLicense() {
        listOf("", "   ", " MATERIAL ", "material3-android", "alpha26", "open SOURCE", "Apache").forEach {
            assertTrue("Expected match for '$it'", library.matches(it))
        }
        assertFalse(library.matches("unrelated"))
    }

    @Test fun onlyAbsoluteHttpAndHttpsUrlsCanBeOpened() {
        assertEquals("https://example.com/license", " https://example.com/license ".toWebUrlOrNull())
        assertEquals("http://example.com", "http://example.com".toWebUrlOrNull())
        listOf(null, "", "javascript:alert(1)", "file:///data/data/private", "intent://app", "https:relative", "//example.com").forEach {
            assertNull(it.toWebUrlOrNull())
        }
    }

    @Test fun missingMetadataIsNotReplacedWithAnInventedLicense() {
        assertNull(null.toSpdxLicenseUrlOrNull())
        assertNull("../MIT".toSpdxLicenseUrlOrNull())
        assertEquals("https://spdx.org/licenses/Apache-2.0.html", "Apache-2.0".toSpdxLicenseUrlOrNull())
        assertEquals("https://spdx.org/licenses/GPL-2.0+.html", "GPL-2.0+".toSpdxLicenseUrlOrNull())
        assertFalse(library.copy(licenses = emptyList()).matches("Apache"))
    }
}
