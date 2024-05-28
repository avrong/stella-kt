package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.Type

data class UnexpectedVariantError(
    val expectedType: Type,
    val expression: StellaParser.ExprContext
) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_VARIANT"

    override fun getDescription(parser: StellaParser): String = """
        получен вариант 
            ${expression.toStringTree(parser)}
        но ожидается не вариантный тип
    """.trimIndent()
}