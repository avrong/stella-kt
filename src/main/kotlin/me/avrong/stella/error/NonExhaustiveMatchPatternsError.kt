package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type

data class NonExhaustiveMatchPatternsError(val expectedType: Type, val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_NONEXHAUSTIVE_MATCH_PATTERNS"

    override fun getDescription(parser: StellaParser): String = """
        не все образцы для типа
            $expectedType
        перечислены в выражении
            ${expression.toStringTree(parser)}
    """.trimIndent()
}