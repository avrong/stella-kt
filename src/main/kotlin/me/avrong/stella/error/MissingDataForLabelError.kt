package me.avrong.me.avrong.stella.error

import StellaParser
import me.avrong.me.avrong.stella.type.Type

data class MissingDataForLabelError(val expr: StellaParser.ExprContext, val type: Type) : CheckError {
    override val name: String = "ERROR_MISSING_DATA_FOR_LABEL"

    override fun getDescription(parser: StellaParser): String = """
        выражение
          ${expr.toStringTree(parser)}
        не содержит даннные для метки, ожидается тег с данными
          $type
    """.trimIndent()
}