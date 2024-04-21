package me.avrong.stella.error

import StellaParser

data class DuplicatePatternVariableError(val pattern: StellaParser.PatternContext, val variable: String) : CheckError {
    override val name: String = "ERROR_DUPLICATE_PATTERN_VARIABLE"

    override fun getDescription(parser: StellaParser): String = """
        переменная $variable
        встречается больше 1 раза в паттерне
          ${pattern.toStringTree(parser)}
    """.trimIndent()
}