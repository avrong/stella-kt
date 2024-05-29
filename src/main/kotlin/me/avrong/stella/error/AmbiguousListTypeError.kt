package me.avrong.stella.error

import StellaParser

data class AmbiguousListTypeError(val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_AMBIGUOUS_LIST_TYPE"

    override fun getDescription(parser: StellaParser): String = """
        тип списка
            ${expression.toStringTree(parser)}
        невозможно определить, в данном контексте отсутствует ожидаемый тип списка
    """.trimIndent()
}