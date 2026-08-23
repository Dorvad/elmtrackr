package com.elmtrackr.wear

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Format placeholders have to match across locales.
 *
 * `getString(id, arg)` runs the resolved string through `String.format`, so a
 * translation that carries a placeholder the default string does not — or a
 * differently numbered one — throws `MissingFormatArgumentException` at the
 * moment that screen is drawn. On the watch that is a crash on the idle face
 * for every user in that language and nobody else, which is close to
 * undetectable without a check like this one.
 */
class WearStringResourceTest {

    @Test
    fun translationsDeclareTheSamePlaceholdersAsTheDefaultLocale() {
        val resDir = resolveResDir()
        val default = readStrings(File(resDir, "values/strings.xml"))
        assertTrue("No default strings found under $resDir", default.isNotEmpty())

        val translations = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-")
        }.orEmpty()
        assertTrue("Expected translated resources next to the default ones", translations.isNotEmpty())

        for (dir in translations) {
            val strings = readStrings(File(dir, "strings.xml"))
            for ((name, value) in strings) {
                val expected = default[name] ?: continue
                assertEquals(
                    "Placeholder mismatch in ${dir.name}/strings.xml for \"$name\"",
                    placeholders(expected),
                    placeholders(value),
                )
            }
        }
    }

    private fun placeholders(value: String): List<String> =
        PLACEHOLDER.findAll(value).map { it.value }.sorted().toList()

    private fun readStrings(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val root = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to (it.textContent ?: "") }
    }

    private fun resolveResDir(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (candidate in listOf("src/main/res", "wear/src/main/res")) {
                val file = File(dir, candidate)
                if (File(file, "values/strings.xml").isFile) return file
            }
            dir = dir.parentFile
        }
        throw AssertionError("Could not locate the wear res directory from ${System.getProperty("user.dir")}")
    }

    private companion object {
        // %s, %d, %1$s, %2$d — the escaped %% literal is deliberately not a match.
        val PLACEHOLDER = Regex("%(?:\\d+\\$)?[a-zA-Z]")
    }
}
