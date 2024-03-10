package me.avrong.me.avrong.stella.error

import StellaParser
import me.avrong.me.avrong.stella.type.Type

data class UnexpectedLambdaError(val expected: Type?, val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_LAMBDA"

    override fun getDescription(parser: StellaParser): String = """
        ожидается не функциональный тип
          $expected
        но получен функциональный тип
        для выражения
          ${expression.toStringTree(parser)}
    """.trimIndent()
}