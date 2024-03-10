package me.avrong.me.avrong.stella.error

import StellaParser

object MissingMainError : CheckError {
    override val name = "ERROR_MISSING_MAIN"
    override fun getDescription(parser: StellaParser): String = "функция main не найдена в программе"
}
