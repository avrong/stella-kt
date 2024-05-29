package me.avrong.stella.solver

import StellaParser
import me.avrong.stella.type.Type

class OccursInfiniteTypeException(
    val expected: Type,
    val actual: Type,
    val expression: StellaParser.ExprContext
) : Throwable()