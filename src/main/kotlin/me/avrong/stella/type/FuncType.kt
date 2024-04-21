package me.avrong.stella.type

class FuncType(val argTypes: List<Type>, val returnType: Type) : Type() {
    override val name: String = "fn(${argTypes.joinToString(", ")}) -> $returnType"
}