package me.avrong.stella.type

data class UniversalTypeVariable(val variable: String) : Type() {
    override val name: String get() = variable
}