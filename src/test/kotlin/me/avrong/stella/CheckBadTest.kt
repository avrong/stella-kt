package me.avrong.stella

import StellaLexer
import StellaParser
import me.avrong.stella.context.TypeCheckContext
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.readText

class CheckBadTest {
    @Test
    fun testBadCases() {
        val resourcesPath = Paths.get("").toAbsolutePath().resolve("stella-tests/bad/")
        val stellaTests = Files.walk(resourcesPath)
            .filter { item -> Files.isRegularFile(item) }
            .filter { item -> item.toString().endsWith(".st") }

        var noError = 0
        var ok = 0
        var wrong = 0
        var all = 0

        for (testFile in stellaTests) {
            val lexer = StellaLexer(CharStreams.fromString(testFile.readText()))
            val tokens = CommonTokenStream(lexer)
            val parser = StellaParser(tokens)
            val typeCheckContext = TypeCheckContext()
            val typeCheckErrorPrinter = TypeCheckErrorPrinter(parser)
            val typeCheckVisitor = TypeCheckVisitor(typeCheckContext, typeCheckErrorPrinter)

            try {
                parser.program().accept(typeCheckVisitor)
                println("WRONG (NO ERROR) result for ${testFile.parent.name} in ${testFile.fileName}")
                noError += 1
            } catch (e: RuntimeException) {
                if (e.message?.contains(testFile.parent.name) != true) {
                    println("WRONG result for ${testFile.parent.name} in ${testFile.fileName}:\n${e.message}")
                    wrong += 1
                } else {
                    println("OK ${testFile.parent.name} in ${testFile.fileName}")
                    ok += 1
                }
            }

            println()
            all += 1
        }

        val resultMessage = "$all DONE: $ok OK, $wrong WRONG, $noError NOERROR"
        println(resultMessage)

        assert(all == ok) { resultMessage }
    }
}