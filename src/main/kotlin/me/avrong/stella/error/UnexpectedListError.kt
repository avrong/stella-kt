package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type

data class UnexpectedListError(val expected: Type?, val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_LIST"

    override fun getDescription(parser: StellaParser): String = """
        ожидается не тип списка
            $expected
        для выражения
            ${expression.toStringTree(parser)}
    """.trimIndent()
}
