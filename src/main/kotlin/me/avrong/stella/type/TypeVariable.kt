package me.avrong.stella.type

data class TypeVariable(val num: Int) : Type() {
    override val name: String get() = "?T$num"
}