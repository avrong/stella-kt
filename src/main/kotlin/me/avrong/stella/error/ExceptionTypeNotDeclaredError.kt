package me.avrong.stella.error

import StellaParser

data class ExceptionTypeNotDeclaredError(val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_EXCEPTION_TYPE_NOT_DECLARED"

    override fun getDescription(parser: StellaParser): String = """
        в программе используются исключения, но не объявлен их тип
            ${expression.toStringTree(parser)}
    """.trimIndent()
}