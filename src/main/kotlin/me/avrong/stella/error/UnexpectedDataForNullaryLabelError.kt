package me.avrong.me.avrong.stella.error

import StellaParser
import me.avrong.me.avrong.stella.type.Type

data class UnexpectedDataForNullaryLabelError(val expr: StellaParser.ExprContext, val type: Type) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_DATA_FOR_NULLARY_LABEL"

    override fun getDescription(parser: StellaParser): String = """
    выражение
       ${expr.toStringTree(parser)}
    содержит даннные для метки, хотя ожидается тег без данных
       $type
    """.trimIndent()
}