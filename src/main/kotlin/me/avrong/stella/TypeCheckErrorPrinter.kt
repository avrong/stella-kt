package me.avrong.me.avrong.stella

import StellaParser
import me.avrong.me.avrong.stella.error.CheckError

class TypeCheckErrorPrinter(val parser: StellaParser) {
    fun printError(error: CheckError): Nothing {
        error.emit(parser)
    }
}