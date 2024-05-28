package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type

data class UnexpectedSubtypeError(
    val expected: Type,
    val actual: Type?,
    val expression: StellaParser.ExprContext
) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_SUBTYPE"

    override fun getDescription(parser: StellaParser): String = """
        ожидается подтип типа
            $expected
        но получен тип
            $actual
        для выражения
            ${expression.toStringTree(parser)}
    """.trimIndent()
}