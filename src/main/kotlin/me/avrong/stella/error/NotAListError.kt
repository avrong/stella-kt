package me.avrong.me.avrong.stella.error

import StellaParser
import me.avrong.me.avrong.stella.type.Type

data class NotAListError(val expression: StellaParser.ExprContext, val type: Type) : CheckError {
    override val name: String = "ERROR_NOT_A_LIST"

    override fun getDescription(parser: StellaParser): String = """
        для выражения
          ${expression.toStringTree(parser)}
        ожидается тип списка
        но получен тип
          $type
    """.trimIndent()
}