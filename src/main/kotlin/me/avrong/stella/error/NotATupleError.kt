package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type

data class NotATupleError(val expression: StellaParser.ExprContext, val type: Type) : CheckError {
    override val name: String = "ERROR_NOT_A_TUPLE"

    override fun getDescription(parser: StellaParser): String = """
        для выражения
            ${expression.toStringTree(parser)}
        ожидается тип кортежа, но получен тип
            $type
    """.trimIndent()
}