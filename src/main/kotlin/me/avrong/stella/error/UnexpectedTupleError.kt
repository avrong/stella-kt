package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type

data class UnexpectedTupleError(val expected: Type?, val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_TUPLE"

    override fun getDescription(parser: StellaParser): String = """
        ожидается не тип кортежа
            $expected
        но получен тип кортежа для выражения
            ${expression.toStringTree(parser)}
       
    """.trimIndent()
}