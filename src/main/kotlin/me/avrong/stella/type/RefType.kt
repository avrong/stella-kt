package me.avrong.stella.type

data class RefType(val innerType: Type) : Type() {
    override val name: String = "&$innerType"
}