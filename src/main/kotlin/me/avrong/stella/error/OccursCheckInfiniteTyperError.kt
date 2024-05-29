package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type

data class OccursCheckInfiniteTyperError(
    val expected: Type,
    val actual: Type?,
    val expression: StellaParser.ExprContext
) : CheckError {
    override val name: String = "ERROR_OCCURS_CHECK_INFINITE_TYPE"

    override fun getDescription(parser: StellaParser): String = """
        во время унификации
            $expected
        и
            $actual
        для выражения
            ${expression.toStringTree(parser)}
        возникает ограничение, порождающее (запрещенный) бесконечный тип
    """.trimIndent()
}