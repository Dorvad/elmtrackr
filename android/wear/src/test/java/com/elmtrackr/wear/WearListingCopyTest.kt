package com.elmtrackr.wear

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WO-G2 is a Play Console paste, but the copy that paste comes from lives here.
 * If this file loses the words the reviewer greps for, the next upload fails
 * the listing heading even when the watch itself is fine — which is how 10055
 * was rejected.
 */
class WearListingCopyTest {

    @Test
    fun listingCopyMentionsTileAndComplicationInEveryShippedLanguage() {
        val copy = resolveListingCopy().readText()
        val pasteBlocks = Regex("^> .+$", RegexOption.MULTILINE)
            .findAll(copy)
            .joinToString("\n") { it.value }
        assertTrue("listing copy has no paste-ready paragraphs", pasteBlocks.isNotBlank())
        assertFalse(
            "the paste-ready paragraphs must not say Android Wear",
            pasteBlocks.contains("Android Wear"),
        )
        for (language in listOf("English", "Hebrew", "Arabic", "Russian")) {
            assertTrue("missing $language heading in play-listing-wear-copy.md", copy.contains("## $language"))
        }
        // The reviewer greps the English words, including in translated listings.
        val tileHits = Regex("\\btile\\b", RegexOption.IGNORE_CASE).findAll(copy).count()
        val complicationHits = Regex("\\bcomplication\\b", RegexOption.IGNORE_CASE).findAll(copy).count()
        assertTrue("expected tile in each of 4 languages plus the intro, found $tileHits", tileHits >= 5)
        assertTrue(
            "expected complication in each of 4 languages plus the intro, found $complicationHits",
            complicationHits >= 5,
        )
    }

    private fun resolveListingCopy(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (candidate in listOf(
                "docs/play-listing-wear-copy.md",
                "android/docs/play-listing-wear-copy.md",
            )) {
                val file = File(dir, candidate)
                if (file.isFile) return file
            }
            dir = dir.parentFile
        }
        throw AssertionError(
            "Could not locate play-listing-wear-copy.md from ${System.getProperty("user.dir")}",
        )
    }
}
