package me.avrong.me.avrong.stella.error

import StellaParser

data class IncorrectArityOfMainError(val n: Int) : CheckError {
    override val name: String = "ERROR_INCORRECT_ARITY_OF_MAIN"

    override fun getDescription(parser: StellaParser): String = """
    ERROR_INCORRECT_ARITY_OF_MAIN:
      функция main объявлена с $n параметрами, хотя должна быть с 1 параметром
    """.trimIndent()
}