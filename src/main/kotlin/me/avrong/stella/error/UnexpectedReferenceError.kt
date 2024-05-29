package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type

data class UnexpectedReferenceError(
    val expected: Type?,
    val expression: StellaParser.ExprContext
) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_REFERENCE"

    override fun getDescription(parser: StellaParser): String = """
        ожидается не ссылочный тип
            $expected
        для выражения
            ${expression.toStringTree(parser)}
    """.trimIndent()
}