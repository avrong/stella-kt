package me.avrong.stella.type

class ListType(val contentType: Type) : Type() {
    override val name: String = "[$contentType]"
}