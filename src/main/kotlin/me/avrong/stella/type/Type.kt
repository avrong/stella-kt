package me.avrong.stella.type

abstract class Type {
    abstract val name : String
    override fun toString(): String = name

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is Type) return false

        return name == other.name
    }
}