package me.avrong.stella.error

import StellaParser

data class TupleIndexOfBoundsError(val expression: StellaParser.ExprContext, val index: Int) : CheckError {
    override val name: String = "ERROR_TUPLE_INDEX_OUT_OF_BOUNDS"

    override fun getDescription(parser: StellaParser): String = """
        в выражении
          ${expression.toStringTree(parser)}
        попытка извлечь отсутствующий компонент кортежа
          ${index + 1}
    """.trimIndent()
}