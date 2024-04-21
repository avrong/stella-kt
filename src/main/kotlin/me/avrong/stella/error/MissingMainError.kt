package me.avrong.stella.error

import StellaParser
import me.avrong.stella.error.CheckError

object MissingMainError : CheckError {
    override val name = "ERROR_MISSING_MAIN"
    override fun getDescription(parser: StellaParser): String = "функция main не найдена в программе"
}
