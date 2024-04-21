package me.avrong.stella

import StellaParser
import me.avrong.stella.error.CheckError

class TypeCheckErrorPrinter(val parser: StellaParser) {
    fun printError(error: CheckError): Nothing {
        error.emit(parser)
    }
}