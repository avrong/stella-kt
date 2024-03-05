package me.avrong

import StellaLexer
import StellaParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

fun main() {
    println("Hello World!")

    val inputStream = CharStreams.fromFileName("input.stella")
    val lexer = StellaLexer(inputStream)
    val tokenStream = CommonTokenStream(lexer)
    val parser = StellaParser(tokenStream)
    val result = parser.program().toStringTree()

    print(result)
}