package me.avrong.stella.error

import StellaParser
import me.avrong.stella.type.RecordType
import me.avrong.stella.type.Type

data class MissingRecordFieldsError(
    val expected: Type?,
    val actual: RecordType,
    val expression: StellaParser.ExprContext,
    val fields: Set<String>
) : CheckError {
    override val name: String = "ERROR_MISSING_RECORD_FIELDS"

    override fun getDescription(parser: StellaParser): String = """
        ожидается тип
            $expected
        но получен тип записи
            $actual
        в котором нет полей
            ${fields.joinToString(", ") }
        для выражения
            ${expression.toStringTree(parser)}
    """.trimIndent()
}