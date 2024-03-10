package me.avrong.me.avrong.stella.error

import StellaParser
import me.avrong.me.avrong.stella.type.Type

data class UnexpectedInjectionError(val expectedType: Type, val expression: StellaParser.ExprContext) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_INJECTION"

    override fun getDescription(parser: StellaParser): String = """
        получена инъекция 
          ${expression.toStringTree(parser)}
        но ожидается тип отличный от суммы
          $expectedType
    """.trimIndent()
}