package me.avrong.stella.error

import StellaParser

data class NotAGenericFunctionError(val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_NOT_A_GENERIC_FUNCTION"

    override fun getDescription(parser: StellaParser): String = """
        при попытке применить универсальное выражение к типовому аргументу, выражение оказалось не универсальной функцией:
            ${expression.toStringTree(parser)}
    """.trimIndent()
}