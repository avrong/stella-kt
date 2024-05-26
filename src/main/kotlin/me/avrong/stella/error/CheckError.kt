package me.avrong.stella.error

import StellaParser

interface CheckError {
    val name: String

    fun getDescription(parser: StellaParser): String

    fun getMessage(parser: StellaParser) =
        "$name:\n" + getDescription(parser).prependIndent("  ")

    fun emit(parser: StellaParser): Nothing {
        val message = getMessage(parser)
        throw RuntimeException(message)
    }
}