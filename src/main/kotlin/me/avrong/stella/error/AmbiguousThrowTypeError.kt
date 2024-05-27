package me.avrong.stella.error

import StellaParser

data class AmbiguousThrowTypeError(val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_AMBIGUOUS_THROW_TYPE"

    override fun getDescription(parser: StellaParser): String = """
        неоднозначный тип throw выражения
            ${expression.toStringTree(parser)}
    """.trimIndent()
}