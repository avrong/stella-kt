package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type

data class NotARecordError(val expression: StellaParser.ExprContext, val type: Type) : CheckError {
    override val name: String = "ERROR_NOT_A_RECORD"

    override fun getDescription(parser: StellaParser): String = """
        для выражения
          ${expression.toStringTree(parser)}
        ожидается тип записи
        но получен тип
          $type
    """.trimIndent()
}