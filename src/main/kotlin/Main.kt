package me.avrong.stella

import StellaLexer
import StellaParser
import me.avrong.me.avrong.stella.TypeCheckContext
import me.avrong.me.avrong.stella.TypeCheckErrorPrinter
import me.avrong.me.avrong.stella.TypeCheckVisitor
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

fun main() {
    var source = ""
    var line = readlnOrNull()

    while (line != null) {
        source += line
        line = readlnOrNull()
    }

    val inputStream = CharStreams.fromString(source)
    val lexer = StellaLexer(inputStream)
    val tokenStream = CommonTokenStream(lexer)
    val parser = StellaParser(tokenStream)
    val typeCheckContext = TypeCheckContext()
    val typeCheckErrorPrinter = TypeCheckErrorPrinter(parser)
    val typeCheckVisitor = TypeCheckVisitor(typeCheckContext, typeCheckErrorPrinter)
    parser.program().accept(typeCheckVisitor)
}