package me.avrong.stella.error

import StellaParser
import kotlin.system.exitProcess

interface CheckError {
    val name: String

    fun getDescription(parser: StellaParser): String

    fun getMessage(parser: StellaParser) =
        "$name:\n" + getDescription(parser).prependIndent("  ")

    fun emit(parser: StellaParser): Nothing {
        val message = getMessage(parser)
        println(message)

        exitProcess(1)
    }
}