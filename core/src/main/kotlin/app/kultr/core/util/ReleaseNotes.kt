package app.kultr.core.util

/**
 * The small part of Markdown that release notes use, read into blocks the app
 * can lay out: headings, bullet points and paragraphs, with bold, code and
 * links inside them. The "Install" section, which only makes sense on the
 * release page, is left out.
 */
object ReleaseNotes {
    sealed interface Block {
        data class Heading(val level: Int, val text: List<Span>) : Block
        data class Bullet(val text: List<Span>) : Block
        data class Paragraph(val text: List<Span>) : Block
    }

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val code: Boolean = false,
        val link: String? = null,
    )

    private val HEADING = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val BULLET = Regex("^\\s*[-*+]\\s+(.*)$")
    private val SKIPPED_SECTIONS = setOf("install", "installing", "download")

    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = StringBuilder()
        var bullet: StringBuilder? = null
        var skipping = false
        var skipLevel = 0

        fun flush() {
            bullet?.let { blocks += Block.Bullet(inline(it.toString())) }
            bullet = null
            if (paragraph.isNotEmpty()) blocks += Block.Paragraph(inline(paragraph.toString()))
            paragraph.clear()
        }

        for (raw in markdown.replace("\r\n", "\n").lines()) {
            val line = raw.trimEnd()
            val heading = HEADING.find(line)
            if (heading != null) {
                flush()
                val level = heading.groupValues[1].length
                val title = heading.groupValues[2]
                if (skipping && level > skipLevel) continue
                skipping = title.trim().lowercase() in SKIPPED_SECTIONS
                skipLevel = level
                if (!skipping) blocks += Block.Heading(level, inline(title))
                continue
            }
            if (skipping) continue
            val item = BULLET.find(line)
            when {
                line.isBlank() -> flush()
                item != null -> {
                    flush()
                    bullet = StringBuilder(item.groupValues[1].trim())
                }
                // A wrapped line carries on the bullet or paragraph above it.
                bullet != null -> bullet!!.append(' ').append(line.trim())
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
        }
        flush()
        return blocks
    }

    /** Bold (`**`/`__`), `code` and [links](url); anything else stays as written. */
    fun inline(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        val plain = StringBuilder()
        var bold = false
        var i = 0

        fun emit(span: Span) {
            if (plain.isNotEmpty()) {
                spans += Span(plain.toString(), bold = bold)
                plain.clear()
            }
            spans += span
        }

        while (i < text.length) {
            val c = text[i]
            when {
                (text.startsWith("**", i) || text.startsWith("__", i)) -> {
                    if (plain.isNotEmpty()) spans += Span(plain.toString(), bold = bold)
                    plain.clear()
                    bold = !bold
                    i += 2
                }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) {
                        plain.append(c)
                        i++
                    } else {
                        emit(Span(text.substring(i + 1, end), bold = bold, code = true))
                        i = end + 1
                    }
                }
                c == '[' -> {
                    val close = text.indexOf("](", i + 1)
                    val end = if (close < 0) -1 else text.indexOf(')', close + 2)
                    if (close < 0 || end < 0) {
                        plain.append(c)
                        i++
                    } else {
                        emit(Span(text.substring(i + 1, close), bold = bold, link = text.substring(close + 2, end)))
                        i = end + 1
                    }
                }
                c == '\\' && i + 1 < text.length -> {
                    plain.append(text[i + 1])
                    i += 2
                }
                else -> {
                    plain.append(c)
                    i++
                }
            }
        }
        if (plain.isNotEmpty()) spans += Span(plain.toString(), bold = bold)
        return spans.filter { it.text.isNotEmpty() }
    }
}
