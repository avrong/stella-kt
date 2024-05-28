package me.avrong.stella.error

import StellaParser

data class AmbiguousListError(val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_AMBIGUOUS_LIST"

    override fun getDescription(parser: StellaParser): String = """
        тип списка
            ${expression.toStringTree(parser)}
        невозможно определить, в данном контексте отсутсвует ожидаемый тип списка
    """.trimIndent()
}