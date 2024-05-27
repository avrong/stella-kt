package me.avrong.stella.me.avrong.stella.type

import me.avrong.stella.type.Type

data class RefType(val innerType: Type) : Type() {
    override val name: String = "&$innerType"
}