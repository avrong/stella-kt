package me.avrong.stella.error

import StellaParser

data class IncorrectNumberOfTypeArgumentsError(
    val expected: Int,
    val actual: Int,
    val expression: StellaParser.ExprContext
) : CheckError {
    override val name: String = "ERROR_INCORRECT_NUMBER_OF_TYPE_ARGUMENTS"

    override fun getDescription(parser: StellaParser): String = """
        вызов универсальной функции
            ${expression.toStringTree(parser)}
        происходит с некорректным количеством типов-аргументов
            $actual
        вместо
            $expected 
    """.trimIndent()
}