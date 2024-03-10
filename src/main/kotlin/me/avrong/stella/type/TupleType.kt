package me.avrong.me.avrong.stella.type

class TupleType(val types: List<Type>) : Type() {
    private val typesStr = types.joinToString(", ")
    override val name: String = "{$typesStr}"
}