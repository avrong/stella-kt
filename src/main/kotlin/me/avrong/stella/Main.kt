package me.avrong.stella

import StellaLexer
import StellaParser
import me.avrong.stella.context.TypeCheckContext
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.util.*
import kotlin.system.exitProcess

fun main() {
    var source = ""

    val scanner = Scanner(System.`in`).apply {
        useDelimiter(System.lineSeparator()) // Use correct line separator
    }

    while (scanner.hasNextLine()) {
        val line = scanner.nextLine()
        source += line + "\n"
    }

    scanner.close()

    val inputStream = CharStreams.fromString(source)
    val lexer = StellaLexer(inputStream)
    val tokenStream = CommonTokenStream(lexer)
    val parser = StellaParser(tokenStream)
    val typeCheckContext = TypeCheckContext()
    val typeCheckErrorPrinter = TypeCheckErrorPrinter(parser)
    val typeCheckVisitor = TypeCheckVisitor(typeCheckContext, typeCheckErrorPrinter)

    try {
        parser.program().accept(typeCheckVisitor)
    } catch (e: RuntimeException) {
        System.err.println(e.message)
        exitProcess(1)
    }
}