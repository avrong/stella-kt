package me.avrong.stella.solver

import me.avrong.stella.type.Type

class SolveFailException(
    val expected: Type,
    val actual: Type,
    val expression: StellaParser.ExprContext
) : Throwable()