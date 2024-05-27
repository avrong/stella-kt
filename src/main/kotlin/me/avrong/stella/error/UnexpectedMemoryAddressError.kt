package me.avrong.stella.me.avrong.stella.error

import StellaParser
import me.avrong.stella.error.CheckError
import me.avrong.stella.type.Type

data class UnexpectedMemoryAddressError(val expression: StellaParser.ExprContext, val type: Type) : CheckError {
    override val name: String = "ERROR_UNEXPECTED_MEMORY_ADDRESS"

    override fun getDescription(parser: StellaParser): String = """
        адрес памяти
            ${expression.toStringTree(parser)}
        используется там, где ожидается тип, отличный от типа-ссылки
            $type
    """.trimIndent()
}