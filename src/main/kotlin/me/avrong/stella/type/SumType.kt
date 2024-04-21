package me.avrong.stella.type

import me.avrong.stella.type.Type

class SumType(val left: Type, val right: Type) : Type() {
    override val name: String = "$left + $right"
}