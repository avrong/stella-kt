package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type

data class UnexpectedRecordError(val expected: Type?, val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_RECORD"

    override fun getDescription(parser: StellaParser): String = """
        ожидается не тип записи
            $expected
        для выражения
            ${expression.toStringTree(parser)}
    """.trimIndent()
}