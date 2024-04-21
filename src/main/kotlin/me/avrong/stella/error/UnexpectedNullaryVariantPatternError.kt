package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type


data class UnexpectedNullaryVariantPatternError(val expr: StellaParser.PatternContext, val type: Type) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_NULLARY_VARIANT_PATTERN"

    override fun getDescription(parser: StellaParser): String = """
        паттерн
           ${expr.toStringTree(parser)}
        содержит тег без данных, хотя ожидается тег с данными
           $type
    """.trimIndent()
}