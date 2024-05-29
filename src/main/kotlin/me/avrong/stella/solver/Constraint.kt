package me.avrong.stella.solver

import StellaParser
import me.avrong.stella.type.Type

data class Constraint(val lhv: Type, val rhv: Type, val expression: StellaParser.ExprContext)