package me.avrong.stella

import StellaLexer
import StellaParser
import me.avrong.stella.context.TypeCheckContext
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.pathString
import kotlin.io.path.readText

class CheckOkTest {
    @Test
    fun testOkCases() {
        val testsPath = Paths.get("").toAbsolutePath().resolve("stella-tests/ok/")
        val stellaTests = Files.walk(testsPath)
            .filter { item -> Files.isRegularFile(item) && item.pathString.endsWith(".st")  }

        var all = 0
        var ok = 0
        var wrong = 0

        for (testFile in stellaTests) {
            val lexer = StellaLexer(CharStreams.fromString(testFile.readText()))
            val tokens = CommonTokenStream(lexer)
            val parser = StellaParser(tokens)
            val typeCheckContext = TypeCheckContext()
            val typeCheckErrorPrinter = TypeCheckErrorPrinter(parser)
            val typeCheckVisitor = TypeCheckVisitor(typeCheckContext, typeCheckErrorPrinter)

            try {
                parser.program().accept(typeCheckVisitor)
                println("OK ${testFile.fileName}")
                ok += 1
            } catch (e: RuntimeException) {
                println("WRONG ${testFile.fileName}:\n${e.message}")
                wrong += 1
            }

            println()
            all += 1
        }

        val resultMessage = "$all DONE: $ok OK, $wrong WRONG"
        println(resultMessage)

        assert(all == ok) { resultMessage }
    }
}