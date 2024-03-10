package me.avrong.me.avrong.stella.type

class RecordType(val fields: List<Pair<String, Type>>) : Type() {
    private val fieldsStr = fields.joinToString(", ") { "${it.first}: ${it.second}" }
    override val name: String = "{$fieldsStr}"
}