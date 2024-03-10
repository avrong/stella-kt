package me.avrong.me.avrong.stella.error

import StellaParser
import me.avrong.me.avrong.stella.type.RecordType

data class UnexpectedFieldAccessError(val recordType: RecordType, val expression: StellaParser.ExprContext, val label: String) : CheckError {
    override val name: String
        get() = "ERROR_UNEXPECTED_FIELD_ACCESS"

    override fun getDescription(parser: StellaParser): String = """
    попытка извлечь отсутствующее поле записи
      $label
    для типа записи
      $recordType
    в выражении
      ${expression.toStringTree(parser)}
    """.trimIndent()
}