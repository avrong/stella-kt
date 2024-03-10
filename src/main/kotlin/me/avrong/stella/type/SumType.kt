package me.avrong.me.avrong.stella.type

class SumType(val left: Type, val right: Type) : Type() {
    override val name: String = "$left + $right"
}