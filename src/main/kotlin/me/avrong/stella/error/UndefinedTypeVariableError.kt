package me.avrong.stella.error

import StellaParser

data class UndefinedTypeVariableError(val variable: String) : CheckError {
    override val name: String = "ERROR_UNDEFINED_TYPE_VARIABLE"

    override fun getDescription(parser: StellaParser): String = """
        встретилась необъявленная типовая переменная
            $variable
    """.trimIndent()
}