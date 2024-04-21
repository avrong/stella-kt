package me.avrong.stella

import StellaLexer
import StellaParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.pathString
import kotlin.io.path.readText

@Disabled
class CheckOkTest {
    @Test
    fun testCasesOk() {
        val testsPath = Paths.get("").toAbsolutePath().resolve("stella-tests/ok/")
        val stellaTests = Files.walk(testsPath)
            .filter { item -> Files.isRegularFile(item) && item.pathString.endsWith(".st")  }

        for (testFile in stellaTests) {
            val lexer = StellaLexer(CharStreams.fromString(testFile.readText()))
            val tokens = CommonTokenStream(lexer)
            val parser = StellaParser(tokens)
            val typeCheckContext = TypeCheckContext()
            val typeCheckErrorPrinter = TypeCheckErrorPrinter(parser)
            val typeCheckVisitor = TypeCheckVisitor(typeCheckContext, typeCheckErrorPrinter)

            println(testFile)
            parser.program().accept(typeCheckVisitor)
        }
    }

}