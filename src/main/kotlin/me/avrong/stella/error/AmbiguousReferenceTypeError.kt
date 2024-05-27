package me.avrong.stella.error

import StellaParser

data class AmbiguousReferenceTypeError(val expression: StellaParser.ConstMemoryContext) : CheckError {
    override val name: String = "ERROR_AMBIGUOUS_REFERENCE_TYPE"

    override fun getDescription(parser: StellaParser): String = """
        неоднозначный тип адреса памяти
            ${expression.toStringTree(parser)}
    """.trimIndent()
}