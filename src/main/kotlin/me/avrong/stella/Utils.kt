package me.avrong.stella

import StellaParser.*
import me.avrong.stella.type.*

fun isExhaustive(patternList: List<PatternContext>, expressionType: Type, typeCheckVisitor: TypeCheckVisitor): Boolean {
    val patterns = patternList.map { unwrapPattern(it, typeCheckVisitor).first }

    if (patterns.any { it is PatternVarContext }) return true

    return when (expressionType) {
        BoolType ->
            patterns.any { it is PatternTrueContext } &&
                    patterns.any { it is PatternFalseContext }

        UnitType ->
            patterns.any { it is PatternUnitContext }

        NatType ->
            checkNatExhaustiveness(patterns)

        is TupleType -> {
            val tuplePatterns = patterns.filterIsInstance<PatternTupleContext>()
                .filter { it.patterns.size == expressionType.types.size }

            expressionType.types.withIndex().all {
                isExhaustive(tuplePatterns.map { p -> p.patterns[it.index] }, it.value, typeCheckVisitor)
            }
        }

        is RecordType -> {
            val recordPatterns = patterns.filterIsInstance<PatternRecordContext>()

            return expressionType.fields.all { field ->
                val labelPatterns = recordPatterns
                    .map { it.patterns }
                    .filter { it.size == expressionType.fields.size && it.any { lp -> lp.label.text == field.first } }
                    .map { it.first { lp -> lp.label.text == field.first }.pattern() }

                isExhaustive(labelPatterns, field.second, typeCheckVisitor)
            }
        }

        is SumType -> {
            val inls = patterns.filterIsInstance<PatternInlContext>()
                .map { it.pattern() }
            val inrs = patterns.filterIsInstance<PatternInrContext>()
                .map { it.pattern() }

            return isExhaustive(inls, expressionType.left, typeCheckVisitor)
                    && isExhaustive(inrs, expressionType.right, typeCheckVisitor)
        }

        is VariantType -> {
            val variantPatterns = patterns.filterIsInstance<PatternVariantContext>()

            return expressionType.variants.all {
                val varType = it.second
                if (varType != null) {
                    isExhaustive(
                        variantPatterns.filter { p -> p.label.text == it.first }.mapNotNull { p -> p.pattern() },
                        varType,
                        typeCheckVisitor
                    )
                } else {
                    variantPatterns.any { p -> p.label.text == it.first }
                }
            }
        }

        is ListType -> checkListTypeExhaustiveness(patterns, expressionType.contentType, typeCheckVisitor)

        is FuncType, is RefType, is TopType, is BotType -> false
        else -> throw IllegalStateException("Unexpected expression type")
    }
}

fun unwrapPattern(pattern: PatternContext, typeCheckVisitor: TypeCheckVisitor): Pair<PatternContext, Type?> {
    var resultPattern = pattern
    var type : Type? = null

    while (
        resultPattern is ParenthesisedPatternContext
        || resultPattern is PatternAscContext
        || resultPattern is PatternCastAsContext
    ) {
        when (resultPattern) {
            is ParenthesisedPatternContext -> {
                resultPattern = resultPattern.pattern()
            }

            is PatternAscContext -> {
                type = resultPattern.stellatype().accept(typeCheckVisitor)
                resultPattern = resultPattern.pattern()
            }

            is PatternCastAsContext -> {
                resultPattern = resultPattern.pattern()
            }
        }
    }

    return Pair(resultPattern, type)
}

private fun checkNatExhaustiveness(patterns: List<PatternContext>): Boolean {
    fun countLevelAndGetNestedPattern(succPattern: PatternSuccContext): Pair<Int, PatternContext> {
        var result = 0
        var pattern: PatternContext = succPattern
        while (pattern is PatternSuccContext) {
            result++
            pattern = pattern.pattern()
        }
        return Pair(result, pattern)
    }

    val anotherNumbers = mutableSetOf<Int>()

    var leftmostSucc = -1
    for (pattern in patterns) {
        if (pattern is PatternSuccContext) {
            val (level, nestedPattern) = countLevelAndGetNestedPattern(pattern)
            if (nestedPattern is PatternVarContext && (leftmostSucc == -1 || level < leftmostSucc)) {
                leftmostSucc = level
            } else if (nestedPattern is PatternIntContext) {
                anotherNumbers.add(level + nestedPattern.n.text.toInt())
            }
        } else if (pattern is PatternIntContext) {
            anotherNumbers.add(pattern.n.text.toInt())
        }
    }
    if (leftmostSucc == -1) return false

    return (0..<leftmostSucc).all { anotherNumbers.contains(it) }
}

private fun checkListTypeExhaustiveness(patterns: List<PatternContext>, contentType: Type, typeCheckVisitor: TypeCheckVisitor): Boolean {
    data class ListPatternInfo(val nestedPatterns: List<PatternContext>, val size: Int, val hasVarAtEnd: Boolean)

    fun getPatternInfo(ctx: PatternContext): ListPatternInfo {
        if (ctx is PatternListContext) {
            return ListPatternInfo(ctx.patterns, ctx.patterns.size, false)
        }
        if (ctx is PatternConsContext) {
            val nestedPatterns = mutableListOf<PatternContext>()
            var currentPattern = ctx

            while (currentPattern is PatternConsContext) {
                nestedPatterns.add(currentPattern.head)
                currentPattern = currentPattern.tail
            }

            if (currentPattern is PatternVarContext) {
                return ListPatternInfo(nestedPatterns, nestedPatterns.size, true)
            }
            if (currentPattern is PatternListContext) {
                nestedPatterns.addAll(currentPattern.patterns)
                return ListPatternInfo(nestedPatterns, nestedPatterns.size, false)
            }
        }
        throw IllegalStateException()
    }

    val patternsInfo = patterns.filter { it is PatternListContext || it is PatternConsContext }
        .map { getPatternInfo(it) }

    val smallestSizeWithVarAtEnd = patternsInfo.filter { it.hasVarAtEnd }.minByOrNull { it.size }?.size
        ?: return false

    if (!patternsInfo.any { it.size == 0 }) {
        return false
    }

    for (i in 1..smallestSizeWithVarAtEnd) {
        val patternsWithSize = patternsInfo.filter { it.size == i }
        for (j in 0..<i) {
            if (!isExhaustive(patternsWithSize.map { it.nestedPatterns[j] }, contentType, typeCheckVisitor)) {
                return false
            }
        }
    }
    return true
}
