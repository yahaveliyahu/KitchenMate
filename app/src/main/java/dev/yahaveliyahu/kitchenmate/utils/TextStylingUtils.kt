package dev.yahaveliyahu.kitchenmate.utils

    import android.content.Context
    import android.graphics.Color
    import android.graphics.Typeface
    import android.text.Spannable
    import android.text.SpannableStringBuilder
    import android.text.style.ForegroundColorSpan
    import android.text.style.StyleSpan
    import androidx.core.graphics.toColorInt
    import dev.yahaveliyahu.kitchenmate.service.RecipeMatcher

fun buildSmartIngredientHighlight(
    context: Context,
    ingredients: List<String>,
    userIngredients: List<String>,
    commonIngredients: List<String>,
    bulletColor: Int? = null
): SpannableStringBuilder {
    val matcher = RecipeMatcher()
    val builder = SpannableStringBuilder()

    fun toSingular(word: String): String {
        return when {
            word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
            word.endsWith("oes") && word.length > 4 -> word.dropLast(3) + "o"
            word.endsWith("es") && word.length > 4 -> word.dropLast(1)
            word.endsWith("es") && word.length > 4 -> word.dropLast(2)
            word.endsWith("s") && word.length > 3 && !word.endsWith("ss") -> word.dropLast(1)
            word.endsWith("s") -> word.dropLast(1)
            else -> word
        }
    }

    val normalizedCommonIngredients = commonIngredients.map {
        matcher.normalizeIngredient(toSingular(it.lowercase().trim()))
    }.toSet()

    val normalizedUserIngredients = userIngredients.map {
        matcher.normalizeIngredient(toSingular(it.lowercase().trim()))
    }

    for (ingredientLine in ingredients) {
        val bullet = "\u2022 "
        val lineStart = builder.length
        builder.append(bullet)

        val lineTextStart = builder.length
        builder.append(ingredientLine + "\n")
        val lineTextEnd = builder.length

        builder.setSpan(
            ForegroundColorSpan(Color.BLACK),
            lineTextStart,
            lineTextEnd - 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val originalWords = ingredientLine.split(Regex("\\s+"))
        val normalizedWords = originalWords.map {
            matcher.normalizeIngredient(toSingular(it.lowercase().trim()))
        }

        var offset = lineTextStart
        var i = 0

        while (i < normalizedWords.size) {
            var matchLength = 0
            var matchText = ""
            var normalizedCandidate = ""

            for (len in 3 downTo 1) {
                if (i + len <= normalizedWords.size) {
                    val subNorm = normalizedWords.subList(i, i + len)
                    val subOrig = originalWords.subList(i, i + len)

                    val candidateNorm = subNorm.joinToString(" ")
                    val reversedNorm = subNorm.reversed().joinToString(" ")
                    val candidateText = subOrig.joinToString(" ")

                    if (candidateNorm in normalizedCommonIngredients || reversedNorm in normalizedCommonIngredients) {
                        normalizedCandidate = if (candidateNorm in normalizedCommonIngredients) candidateNorm else reversedNorm
                        matchLength = len
                        matchText = candidateText
                        break
                    }
                }
            }

            if (matchLength > 0) {
                val matchStart = ingredientLine.indexOf(matchText, offset - lineTextStart, ignoreCase = true) + lineTextStart
                val matchEnd = matchStart + matchText.length

                val color = if (normalizedCandidate in normalizedUserIngredients) {
                    "#2E7D32".toColorInt() // Green
                } else {
                    "#C62828".toColorInt() // Red
                }

                builder.setSpan(
                    ForegroundColorSpan(color),
                    matchStart,
                    matchEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                offset = matchEnd + 1
                i += matchLength
            } else {
                offset += originalWords[i].length + 1
                i++
            }
        }

        if (bulletColor != null) {
            builder.setSpan(
                ForegroundColorSpan(bulletColor),
                lineStart,
                lineStart + bullet.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    return builder
}

// Function for coloring preparation instructions – number highlighted and colored
    fun buildColoredInstructionsText(
        context: Context,
        instructions: String,
        numberingColor: Int
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val steps = instructions.split(".")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        steps.forEachIndexed { index, step ->
            val prefix = "${index + 1}. "
            val start = builder.length
            builder.append(prefix + step + "\n")

            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                start + prefix.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                ForegroundColorSpan(numberingColor),
                start,
                start + prefix.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return builder
    }