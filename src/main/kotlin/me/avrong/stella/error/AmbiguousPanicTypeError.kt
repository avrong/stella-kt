package me.avrong.stella.error

import StellaParser

data class AmbiguousPanicTypeError(val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_AMBIGUOUS_PANIC_TYPE"

    override fun getDescription(parser: StellaParser): String = """
        неоднозначный тип ошибки
            ${expression.toStringTree(parser)}
    """.trimIndent()
}