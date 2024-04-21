package me.avrong.stella.error

import StellaParser

data class IncorrectNumberOfArgumentsError(val actual: Int, val expected: Int, val expression: StellaParser.ExprContext) :
    CheckError {
    override val name: String = "ERROR_INCORRECT_NUMBER_OF_ARGUMENTS"

    override fun getDescription(parser: StellaParser): String = """
        вызов
          ${expression.toStringTree(parser)}
        происходит с $actual аргументами, хотя должен с $expected
    """.trimIndent()
}