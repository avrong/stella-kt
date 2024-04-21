package me.avrong.stella.error

import StellaParser

data class UnexpectedNumberOfParametersInLambdaError(val expected: Int, val expression: StellaParser.ExprContext) :
    CheckError {
    override val name: String = "ERROR_UNEXPECTED_NUMBER_OF_PARAMETERS_IN_LAMBDA"

    override fun getDescription(parser: StellaParser): String = """
        количество параметров анонимной функции
           ${expression.toStringTree(parser)}
        не совпадает с ожидаемым количеством параметров $expected
    """.trimIndent()
}