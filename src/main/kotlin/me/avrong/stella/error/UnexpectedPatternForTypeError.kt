package me.avrong.me.avrong.stella.error

import StellaParser
import me.avrong.me.avrong.stella.type.Type

data class UnexpectedPatternForTypeError(val type: Type?, val pattern: StellaParser.PatternContext) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_PATTERN_FOR_TYPE"

    override fun getDescription(parser: StellaParser): String = """
        образец
           ${pattern.toStringTree(parser)}
        не соответствует типу разбираемого выражения
           $type
    """.trimIndent()
}