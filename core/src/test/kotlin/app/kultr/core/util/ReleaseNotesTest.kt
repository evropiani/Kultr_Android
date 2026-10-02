package app.kultr.core.util

import app.kultr.core.util.ReleaseNotes.Block
import app.kultr.core.util.ReleaseNotes.Span
import org.junit.Test
import kotlin.test.assertEquals

class ReleaseNotesTest {
    @Test
    fun readsHeadingsBulletsAndParagraphs() {
        val notes = """
            ## What's new

            - **Search in the bar.** Tap the search
              button and it grows.
            - Plain point

            A paragraph
            over two lines.
        """.trimIndent()
        assertEquals(
            listOf(
                Block.Heading(2, listOf(Span("What's new"))),
                Block.Bullet(listOf(Span("Search in the bar.", bold = true), Span(" Tap the search button and it grows."))),
                Block.Bullet(listOf(Span("Plain point"))),
                Block.Paragraph(listOf(Span("A paragraph over two lines."))),
            ),
            ReleaseNotes.parse(notes),
        )
    }

    @Test
    fun leavesOutTheInstallSection() {
        val notes = """
            ## What's new
            - One
            ## Install
            Download **Kultr-1.4.0.apk** below.
            ### Checksums
            abc
            ## Thanks
            Everyone.
        """.trimIndent()
        assertEquals(
            listOf(
                Block.Heading(2, listOf(Span("What's new"))),
                Block.Bullet(listOf(Span("One"))),
                Block.Heading(2, listOf(Span("Thanks"))),
                Block.Paragraph(listOf(Span("Everyone."))),
            ),
            ReleaseNotes.parse(notes),
        )
    }

    @Test
    fun readsCodeLinksAndEscapes() {
        assertEquals(
            listOf(
                Span("Key "),
                Span("3C:26", code = true),
                Span(", see "),
                Span("the page", link = "https://kultr.cc/"),
                Span(" *really*"),
            ),
            ReleaseNotes.inline("Key `3C:26`, see [the page](https://kultr.cc/) \\*really\\*"),
        )
        // Unclosed marks are kept as written.
        assertEquals(listOf(Span("a ` b [c")), ReleaseNotes.inline("a ` b [c"))
    }
}
