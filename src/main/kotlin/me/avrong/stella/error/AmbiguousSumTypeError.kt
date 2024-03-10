package me.avrong.me.avrong.stella.error

import StellaParser

data class AmbiguousSumTypeError(val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_AMBIGUOUS_SUM_TYPE"

    override fun getDescription(parser: StellaParser): String = """
        тип инъекции
          ${expression.toStringTree(parser)}
        невозможно определить 
        в данном контексте отсутсвует ожидаемый тип-сумма
    """.trimIndent()
}