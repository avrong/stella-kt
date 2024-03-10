package me.avrong.me.avrong.stella.error

import StellaParser
import me.avrong.me.avrong.stella.type.TupleType

data class UnexpectedTupleLengthError(val expected: TupleType, val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_TUPLE_LENGTH"

    override fun getDescription(parser: StellaParser): String = """
        ожидается кортеж
          $expected
        с длинной ${expected.types.size}
        для выражения
          ${expression.toStringTree(parser)}
    """.trimIndent()
}