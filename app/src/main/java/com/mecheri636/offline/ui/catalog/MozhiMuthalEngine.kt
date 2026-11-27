package com.mecheri636.offline.ui.catalog

data class WheelEntry(
    val key: String,
    val label: String,
    val count: Int
)

object MozhiMuthalEngine {
    const val ALL_KEY = "__ALL__"
    const val OTHER_TA_GROUP = "பிற"

    private fun uyirmei12(base: String): List<String> = listOf(
        base,
        "${base}\u0BBE", "${base}\u0BBF", "${base}\u0BC0",
        "${base}\u0BC1", "${base}\u0BC2", "${base}\u0BC6",
        "${base}\u0BC7", "${base}\u0BC8", "${base}\u0BCA",
        "${base}\u0BCB", "${base}\u0BCC"
    )

    // Exact 114 Tamil Mozhi Muthal letters across 11 parent groups:
    // உயிர் (12) + க/ச/த/ந/ப/ம/ய/வ (8 x 12 = 96) + ஞ (4) + ங (2) = 114.
    val TAMIL_GROUPS: List<Pair<String, List<String>>> = listOf(
        "உயிர்" to listOf("அ", "ஆ", "இ", "ஈ", "உ", "ஊ", "எ", "ஏ", "ஐ", "ஒ", "ஓ", "ஔ"),
        "க" to uyirmei12("க"),
        "ச" to uyirmei12("ச"),
        "த" to uyirmei12("த"),
        "ந" to uyirmei12("ந"),
        "ப" to uyirmei12("ப"),
        "ம" to uyirmei12("ம"),
        "ய" to uyirmei12("ய"),
        "வ" to uyirmei12("வ"),
        "ஞ" to listOf("ஞ", "ஞ\u0BBE", "ஞ\u0BC6", "ஞ\u0BCA"),
        "ங" to listOf("ங", "ங\u0BBE")
    )

    private val LETTER_TO_PARENT: Map<String, String> = buildMap {
        TAMIL_GROUPS.forEach { (parent, letters) ->
            letters.forEach { ch -> put(ch, parent) }
        }
    }

    private val EN_CATEGORY_ORDER = listOf(
        "VEG" to "Veg",
        "BEAN" to "Beans",
        "TUBER" to "Tubers",
        "GREEN" to "Greens",
        "FRUIT" to "Fruits"
    )

    // Exact Tamil grapheme extractor (never splits vowel signs with raw UTF-16 .first()).
    fun firstTamilGrapheme(raw: String): String {
        val trimmed = raw.trimStart { ch -> !ch.isLetter() && ch !in '\u0B80'..'\u0BFF' }
        if (trimmed.isEmpty()) return "#"
        val first = trimmed[0]
        if (first !in '\u0B80'..'\u0BFF') {
            return first.uppercaseChar().toString()
        }
        val sb = StringBuilder().append(first)
        var i = 1
        while (i < trimmed.length) {
            val next = trimmed[i]
            if (next in '\u0BBE'..'\u0BCD' || next == '\u0BD7' || next == '\u0B82') {
                sb.append(next)
                i++
            } else {
                break
            }
        }
        return sb.toString()
            .replace("\u0BC6\u0BBE", "\u0BCA")
            .replace("\u0BC7\u0BBE", "\u0BCB")
            .replace("\u0BC6\u0BD7", "\u0BCC")
    }

    fun parentTamilGroup(grapheme: String): String =
        LETTER_TO_PARENT[grapheme] ?: OTHER_TA_GROUP

    fun firstEnglishLetter(raw: String): String {
        val c = raw.firstOrNull { it in 'A'..'Z' || it in 'a'..'z' }
        return c?.uppercaseChar()?.toString() ?: "#"
    }

    fun <T> buildTamilLeftGroups(items: List<T>, tamilLabel: (T) -> String): List<WheelEntry> {
        val counts = mutableMapOf<String, Int>()
        items.forEach { item ->
            val parent = parentTamilGroup(firstTamilGrapheme(tamilLabel(item)))
            counts[parent] = (counts[parent] ?: 0) + 1
        }
        val out = mutableListOf(WheelEntry(ALL_KEY, "அனைத்தும்", items.size))
        TAMIL_GROUPS.forEach { (parent, _) ->
            val c = counts[parent] ?: 0
            if (c > 0) out.add(WheelEntry(parent, parent, c))
        }
        val otherCount = counts[OTHER_TA_GROUP] ?: 0
        if (otherCount > 0) out.add(WheelEntry(OTHER_TA_GROUP, OTHER_TA_GROUP, otherCount))
        return out
    }

    fun <T> buildTamilRightLetters(
        items: List<T>,
        selectedParent: String,
        tamilLabel: (T) -> String
    ): List<WheelEntry> {
        val counts = mutableMapOf<String, Int>()
        var groupTotal = 0
        items.forEach { item ->
            val g = firstTamilGrapheme(tamilLabel(item))
            val p = parentTamilGroup(g)
            if (selectedParent == ALL_KEY || p == selectedParent) {
                counts[g] = (counts[g] ?: 0) + 1
                groupTotal++
            }
        }
        val out = mutableListOf(WheelEntry(ALL_KEY, "அனைத்தும்", groupTotal))
        if (selectedParent == ALL_KEY) {
            TAMIL_GROUPS.forEach { (_, letters) ->
                letters.forEach { l -> counts[l]?.let { out.add(WheelEntry(l, l, it)) } }
            }
            counts.keys.filter { it !in LETTER_TO_PARENT }.sorted().forEach { l ->
                out.add(WheelEntry(l, l, counts[l] ?: 0))
            }
        } else if (selectedParent == OTHER_TA_GROUP) {
            counts.keys.sorted().forEach { l ->
                out.add(WheelEntry(l, l, counts[l] ?: 0))
            }
        } else {
            val ordered = TAMIL_GROUPS.firstOrNull { it.first == selectedParent }?.second.orEmpty()
            ordered.forEach { l -> counts[l]?.let { out.add(WheelEntry(l, l, it)) } }
        }
        return out
    }

    fun <T> buildEnglishLeftCategories(items: List<T>, categoryCode: (T) -> String): List<WheelEntry> {
        val counts = items.groupingBy(categoryCode).eachCount()
        val out = mutableListOf(WheelEntry(ALL_KEY, "All", items.size))
        EN_CATEGORY_ORDER.forEach { (code, lbl) ->
            val c = counts[code] ?: 0
            if (c > 0) out.add(WheelEntry(code, lbl, c))
        }
        counts.keys.filter { k -> EN_CATEGORY_ORDER.none { it.first == k } }.sorted().forEach { extra ->
            out.add(WheelEntry(extra, extra, counts[extra] ?: 0))
        }
        return out
    }

    fun <T> buildEnglishRightLetters(
        items: List<T>,
        selectedCategory: String,
        categoryCode: (T) -> String,
        englishLabel: (T) -> String
    ): List<WheelEntry> {
        val counts = mutableMapOf<String, Int>()
        var total = 0
        items.forEach { item ->
            if (selectedCategory == ALL_KEY || categoryCode(item) == selectedCategory) {
                val ch = firstEnglishLetter(englishLabel(item))
                counts[ch] = (counts[ch] ?: 0) + 1
                total++
            }
        }
        val out = mutableListOf(WheelEntry(ALL_KEY, "A–Z", total))
        ('A'..'Z').forEach { c ->
            val k = c.toString()
            counts[k]?.let { out.add(WheelEntry(k, k, it)) }
        }
        counts["#"]?.let { out.add(WheelEntry("#", "#", it)) }
        return out
    }

    fun <T> filterByWheel(
        items: List<T>,
        isTamilMode: Boolean,
        selectedLeft: String,
        selectedRight: String,
        tamilLabel: (T) -> String,
        englishLabel: (T) -> String,
        categoryCode: (T) -> String
    ): List<T> {
        if (selectedLeft == ALL_KEY && selectedRight == ALL_KEY) return items
        return items.filter { item ->
            if (isTamilMode) {
                val g = firstTamilGrapheme(tamilLabel(item))
                val p = parentTamilGroup(g)
                val leftOk = selectedLeft == ALL_KEY || p == selectedLeft
                val rightOk = selectedRight == ALL_KEY || g == selectedRight
                leftOk && rightOk
            } else {
                val leftOk = selectedLeft == ALL_KEY || categoryCode(item) == selectedLeft
                val rightOk = selectedRight == ALL_KEY || firstEnglishLetter(englishLabel(item)) == selectedRight
                leftOk && rightOk
            }
        }
    }
}
