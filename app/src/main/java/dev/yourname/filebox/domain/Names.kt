package dev.yourname.filebox.domain

/** Name rules shared by S10 (create, rename). FAT/exFAT are the strictest common filesystems. */
object Names {
    private val illegal = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    enum class Problem { Empty, IllegalChars, TrailingDotOrSpace, TooLong, Exists }

    fun check(name: String): Problem? = when {
        name.isBlank() -> Problem.Empty
        name.any { it in illegal || it.code < 32 } -> Problem.IllegalChars
        name.endsWith('.') || name.endsWith(' ') -> Problem.TrailingDotOrSpace
        name.toByteArray().size > 255 -> Problem.TooLong
        else -> null
    }

    fun ext(name: String) = name.substringAfterLast('.', "").lowercase()

    /** "report.pdf" -> "report (1).pdf", "report (1).pdf" -> "report (2).pdf". */
    fun nextFree(name: String, isDir: Boolean, exists: (String) -> Boolean): String {
        val dot = if (isDir) -1 else name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val suffix = if (dot > 0) name.substring(dot) else ""
        val stem = Regex("""^(.*) \((\d+)\)$""").matchEntire(base)?.groupValues?.get(1) ?: base
        var n = 1
        while (true) {
            val candidate = "$stem ($n)$suffix"
            if (!exists(candidate)) return candidate
            n++
        }
    }
}
