package me.avrong.me.avrong.stella.error

import StellaParser
import me.avrong.me.avrong.stella.type.VariantType

data class UnexpectedVariantLabelError(val label: String, val type: VariantType, val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_VARIANT_LABEL"

    override fun getDescription(parser: StellaParser): String = """
        неожиданная метка 
          $label
        для типа варианта
          $type
        в выражении
          ${expression.toStringTree(parser)}
    """.trimIndent()
}