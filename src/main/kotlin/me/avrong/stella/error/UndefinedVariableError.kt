package me.avrong.stella.error

import StellaParser
import org.antlr.v4.runtime.RuleContext

data class UndefinedVariableError(val varName: String, val parentExpression: RuleContext) : CheckError {
    override val name: String = "ERROR_UNDEFINED_VARIABLE"

    override fun getDescription(parser: StellaParser): String = """
        в выражении
          ${parentExpression.toStringTree(parser)}
        содержится необъявленная переменная
          $varName
    """.trimIndent()
}