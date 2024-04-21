package me.avrong.stella.error

import StellaParser

data class AmbiguousVariantTypeError(val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_AMBIGUOUS_VARIANT_TYPE"

    override fun getDescription(parser: StellaParser): String = """
        вариантный тип
          ${expression.toStringTree(parser)}
        невозможно определить 
        в данном контексте отсутсвует ожидаемый вариантный тип
    """.trimIndent()
}