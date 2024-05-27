package me.avrong.stella.error

import StellaParser

data class AmbiguousPatternTypeError(val pattern: StellaParser.PatternContext) : CheckError {
    override val name: String = "ERROR_AMBIGUOUS_PATTERN_TYPE"

    override fun getDescription(parser: StellaParser): String = """
        невозможно определить тип для паттерна
            ${pattern.toStringTree(parser)}
    """.trimIndent()
}