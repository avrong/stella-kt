package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type

data class UnexpectedTypeForExpressionError(val expected: Type, val actual: Type?, val expression: StellaParser.ExprContext) :
    CheckError {
    override val name: String = "ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION"

    override fun getDescription(parser: StellaParser): String = """
        ожидается тип
          $expected
        но получен тип
          $actual
        для выражения
          ${expression.toStringTree(parser)}
    """.trimIndent()
}