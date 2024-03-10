package me.avrong.me.avrong.stella.error

import StellaParser
import me.avrong.me.avrong.stella.type.Type

data class UnexpectedTypeForParameterError(val expected: Type, val actual: Type?, val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_TYPE_FOR_PARAMETER"

    override fun getDescription(parser: StellaParser): String = """
        параметр ожидаемого типа
          $expected
        не соответствует актуальному
          $actual
        для выражения
          ${expression.toStringTree(parser)}
    """.trimIndent()
}