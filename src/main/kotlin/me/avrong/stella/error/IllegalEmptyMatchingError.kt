package me.avrong.me.avrong.stella.error

import StellaParser

data class IllegalEmptyMatchingError(val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_ILLEGAL_EMPTY_MATCHING"

    override fun getDescription(parser: StellaParser): String = """
        match выражение
          ${expression.toStringTree(parser)}
        с пустым списком альтернатив
    """.trimIndent()
}