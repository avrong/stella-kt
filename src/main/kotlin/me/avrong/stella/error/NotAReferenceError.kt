package me.avrong.stella.me.avrong.stella.error

import StellaParser
import me.avrong.stella.error.CheckError

data class NotAReferenceError(val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_NOT_A_REFERENCE"

    override fun getDescription(parser: StellaParser): String = """
        попытка разыменовать или присвоить значение выражению не ссылочного типа
            ${expression.toStringTree(parser)}
    """.trimIndent()
}