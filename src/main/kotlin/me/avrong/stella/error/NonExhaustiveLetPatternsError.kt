package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type

data class NonExhaustiveLetPatternsError(val expected: Type, val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_NONEXHAUSTIVE_LET_PATTERNS"

    override fun getDescription(parser: StellaParser): String = """
        не все образцы для типа
            $expected
        перечислены в выражении
            ${expression.toStringTree(parser)}
    """.trimIndent()
}