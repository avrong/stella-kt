package me.avrong.me.avrong.stella.error

import StellaParser
import me.avrong.me.avrong.stella.type.Type

data class UnexpectedNonNullaryVariantPatternError(val expr: StellaParser.PatternContext, val type: Type) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_NON_NULLARY_VARIANT_PATTERN"

    override fun getDescription(parser: StellaParser): String = """
        паттерн
          ${expr.toStringTree(parser)}
        содержит тег с данными, хотя ожидается тег без
          $type
    """.trimIndent()
}