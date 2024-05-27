package me.avrong.stella

import StellaParser.*
import StellaParserBaseVisitor
import me.avrong.stella.error.*
import me.avrong.stella.error.AmbiguousPanicTypeError
import me.avrong.stella.error.AmbiguousReferenceTypeError
import me.avrong.stella.error.NotAReferenceError
import me.avrong.stella.error.UnexpectedMemoryAddressError
import me.avrong.stella.type.RefType
import me.avrong.stella.type.*

class TypeCheckVisitor(
    private val context: TypeCheckContext,
    private val errorPrinter: TypeCheckErrorPrinter
) : StellaParserBaseVisitor<Type>() {
    override fun visitProgram(ctx: ProgramContext): Type {
        var mainType: Type? = null

        for (decl in ctx.decls) {
            val type = decl.accept(this)

            if (decl is DeclFunContext) {
                context.setType(decl.name.text, type)

                if (decl.name.text == "main") {
                    mainType = type
                }
            }
        }

        if (mainType == null) errorPrinter.printError(MissingMainError)

        val mainArgsSize = (mainType as FuncType).argTypes.size
        if (mainArgsSize != 1) errorPrinter.printError(IncorrectArityOfMainError(mainArgsSize))

        return mainType
    }

    override fun visitDeclFun(ctx: DeclFunContext): Type {
        val params = ctx.paramDecls
        val paramTypes = params.map { Pair(it.name.text, it.paramType.accept(this)) }

        val returnType = ctx.returnType.accept(this)
        val funcType = FuncType(paramTypes.map { it.second }, returnType)

        return context.runWithTypes<Type>(paramTypes.plus(Pair(ctx.name.text, funcType))) {
            val nestedFunctions = ctx.localDecls.filterIsInstance<DeclFunContext>().map {
                Pair(it.name.text, it.accept(this))
            }

            context.runWithTypes(nestedFunctions) {
                context.runWithExpected(returnType) {
                    val returnExpressionType = ctx.returnExpr.accept(this)

                    if (returnType != returnExpressionType) {
                        errorPrinter.printError(
                            UnexpectedTypeForExpressionError(
                                returnType,
                                returnExpressionType,
                                ctx.returnExpr
                            )
                        )
                    }

                    funcType
                }
            }
        }
    }

    override fun visitIsZero(ctx: IsZeroContext): Type {
        val argType = context.runWithExpected(NatType) { ctx.n.accept(this) }
        if (argType != NatType) errorPrinter.printError(UnexpectedTypeForExpressionError(NatType, argType, ctx))
        return BoolType
    }

    override fun visitVar(ctx: VarContext): Type {
        val varName = ctx.name.text
        val varType = context.getType(varName)
        return varType ?: errorPrinter.printError(UndefinedVariableError(varName, ctx.parent))
    }

    override fun visitDotRecord(ctx: DotRecordContext): Type {
        val exprType = context.runWithExpected(null) { ctx.expr().accept(this) }
        if (exprType !is RecordType) errorPrinter.printError(NotARecordError(ctx.expr(), exprType))

        val fieldTypeInfo = exprType.fields.firstOrNull { it.first == ctx.label.text }
            ?: errorPrinter.printError(UnexpectedFieldAccessError(exprType, ctx, ctx.label.text))

        return fieldTypeInfo.second
    }

    override fun visitList(ctx: ListContext): Type {
        val expectedType = context.getExpectedType()
        if (expectedType != null && expectedType !is ListType) errorPrinter.printError(
            UnexpectedListError(
                expectedType,
                ctx
            )
        )
        if (ctx.exprs.isEmpty()) return expectedType ?: errorPrinter.printError(AmbiguousListError(ctx))

        val firstElemType = context.runWithExpected((expectedType as? ListType)?.contentType) {
            ctx.exprs.first().accept(this)
        }

        context.runWithExpected(firstElemType) {
            ctx.exprs.drop(1).forEach {
                val exprType = it.accept(this)
                if (firstElemType != exprType) {
                    errorPrinter.printError(UnexpectedTypeForExpressionError(firstElemType, exprType, ctx))
                }
            }
        }

        return ListType(firstElemType)
    }

    override fun visitHead(ctx: HeadContext): Type {
        val listType = context.runWithExpected(null) { ctx.list.accept(this) }
        if (listType !is ListType) {
            errorPrinter.printError(NotAListError(ctx, listType))
        }

        return listType.contentType
    }

    override fun visitTerminatingSemicolon(ctx: TerminatingSemicolonContext): Type {
        return ctx.expr().accept(this)
    }

    override fun visitAbstraction(ctx: AbstractionContext): Type {
        val expectedType = context.getExpectedType()
        if (expectedType != null && expectedType !is FuncType) {
            errorPrinter.printError(UnexpectedLambdaError(expectedType, ctx))
        }
        val expectedArgTypes = (expectedType as? FuncType)?.argTypes

        val params = ctx.paramDecls
        if (expectedArgTypes != null && expectedArgTypes.size != params.size) {
            errorPrinter.printError(UnexpectedNumberOfParametersInLambdaError(expectedArgTypes.size, ctx))
        }

        val paramsInfo = mutableListOf<Pair<String, Type>>()
        for (i in params.indices) {
            val name = params[i].name.text
            val expectedParamType = expectedArgTypes?.get(i)
            val paramType = context.runWithExpected(expectedParamType) { params[i].stellatype().accept(this) }

            if (expectedParamType != null && paramType != expectedParamType) {
                errorPrinter.printError(UnexpectedTypeForParameterError(paramType, expectedParamType, ctx))
            }

            paramsInfo.add(Pair(name, paramType))
        }

        return context.runWithTypes(paramsInfo) {
            FuncType(paramsInfo.map { it.second }, context.runWithExpected((expectedType as? FuncType)?.returnType) {
                ctx.returnExpr.accept(this)
            })
        }
    }

    override fun visitVariant(ctx: VariantContext): Type {
        val expectedType = context.getExpectedType() ?: errorPrinter.printError(AmbiguousVariantTypeError(ctx))
        if (expectedType !is VariantType) {
            errorPrinter.printError(UnexpectedVariantError(expectedType, ctx))
        }
        val variantLabel = ctx.label.text
        val expectedLabel = expectedType.variants.firstOrNull { it.first == variantLabel }
            ?: errorPrinter.printError(UnexpectedVariantLabelError(variantLabel, expectedType, ctx))

        if (expectedLabel.second == null && ctx.rhs != null) errorPrinter.printError(
            UnexpectedDataForNullaryLabelError(
                ctx,
                expectedType
            )
        )
        if (expectedLabel.second != null && ctx.rhs == null) errorPrinter.printError(
            MissingDataForLabelError(
                ctx,
                expectedType
            )
        )

        if (ctx.rhs != null) {
            val variantType = context.runWithExpected(expectedLabel.second) { ctx.rhs.accept(this) }

            if (expectedLabel.second!! != variantType) errorPrinter.printError(
                UnexpectedTypeForExpressionError(
                    expectedLabel.second!!,
                    variantType,
                    ctx
                )
            )
        }

        return expectedType
    }


    override fun visitIf(ctx: IfContext): Type {
        val conditionType = context.runWithExpected(BoolType) { ctx.condition.accept(this) }
        if (conditionType != BoolType) errorPrinter.printError(
            UnexpectedTypeForExpressionError(
                BoolType,
                conditionType,
                ctx
            )
        )

        val thenType = ctx.thenExpr.accept(this)
        val elseType = ctx.elseExpr.accept(this)

        if (thenType != elseType) {
            errorPrinter.printError(UnexpectedTypeForExpressionError(thenType, elseType, ctx))
        }

        return thenType
    }

    override fun visitApplication(ctx: ApplicationContext): Type {
        val funType = context.runWithExpected(null) { ctx.`fun`.accept(this) }

        if (funType !is FuncType) errorPrinter.printError(NotAFunctionError(ctx.`fun`, funType))

        if (funType.argTypes.size != ctx.args.size) errorPrinter.printError(
            IncorrectNumberOfArgumentsError(
                ctx.args.size,
                funType.argTypes.size,
                ctx
            )
        )
        for (i in funType.argTypes.indices) {
            val expectedType = funType.argTypes[i]
            val exprType = context.runWithExpected(expectedType) { ctx.args[i].accept(this) }
            if (expectedType != exprType) errorPrinter.printError(
                UnexpectedTypeForExpressionError(
                    expectedType,
                    exprType,
                    ctx
                )
            )
        }

        return funType.returnType
    }


    override fun visitIsEmpty(ctx: IsEmptyContext): Type {
        val argType = context.runWithExpected(null) { ctx.list.accept(this) }
        if (argType !is ListType) errorPrinter.printError(NotAListError(ctx, argType))

        return BoolType
    }

    override fun visitSucc(ctx: SuccContext): Type {
        val argType = context.runWithExpected(NatType) { ctx.n.accept(this) }
        if (argType != NatType) {
            errorPrinter.printError(UnexpectedTypeForExpressionError(NatType, argType, ctx))
        }

        return NatType
    }

    override fun visitInl(ctx: InlContext): Type {
        val expectedType = context.getExpectedType() ?: errorPrinter.printError(AmbiguousSumTypeError(ctx))
        if (expectedType !is SumType) {
            errorPrinter.printError(UnexpectedInjectionError(expectedType, ctx))
        }

        val leftType = context.runWithExpected(expectedType.left) { ctx.expr().accept(this) }

        return SumType(leftType, expectedType.right)
    }

    override fun visitInr(ctx: InrContext): Type {
        val expectedType = context.getExpectedType() ?: errorPrinter.printError(AmbiguousSumTypeError(ctx))
        if (expectedType !is SumType) {
            errorPrinter.printError(UnexpectedInjectionError(expectedType, ctx))
        }

        val rightType = context.runWithExpected(expectedType.right) { ctx.expr().accept(this) }

        return SumType(expectedType.left, rightType)
    }

    override fun visitMatch(ctx: MatchContext): Type {
        val expressionType = context.runWithExpected(null) { ctx.expr().accept(this) }
        val cases = ctx.cases
        if (cases.isEmpty()) {
            errorPrinter.printError(IllegalEmptyMatchingError(ctx))
        }

        val casesType = processMatchCase(cases.first(), expressionType)

        cases.drop(1).forEach {
            val caseType = processMatchCase(it, expressionType)
            if (casesType != caseType) {
                errorPrinter.printError(UnexpectedTypeForExpressionError(casesType, caseType, ctx))
            }
        }

        return casesType
    }

    private fun processMatchCase(ctx: MatchCaseContext, expressionType: Type): Type {
        val pattern = ctx.pattern()
        val variables = getVariablesTypesFromPattern(pattern, expressionType)
        val duplicate = variables.map { it.first }
            .groupingBy { it }
            .eachCount()
            .asIterable()
            .firstOrNull { it.value > 1 }
        if (duplicate != null) errorPrinter.printError(DuplicatePatternVariableError(pattern, duplicate.key))

        return context.runWithTypes(variables) { ctx.expr().accept(this) }
    }

    private fun getVariablesTypesFromPattern(pattern: PatternContext, type: Type): List<Pair<String, Type>> {
        return when (pattern) {
            is PatternVarContext -> listOf(Pair(pattern.name.text, type))
            is ParenthesisedPatternContext -> getVariablesTypesFromPattern(pattern.pattern(), type)
            is PatternFalseContext -> {
                if (type != BoolType) errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                listOf()
            }

            is PatternTrueContext -> {
                if (type != BoolType) errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                listOf()
            }

            is PatternUnitContext -> {
                if (type != UnitType) errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                listOf()
            }

            is PatternInlContext -> {
                if (type !is SumType) errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                getVariablesTypesFromPattern(pattern.pattern(), type.left)
            }

            is PatternInrContext -> {
                if (type !is SumType) errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                getVariablesTypesFromPattern(pattern.pattern(), type.right)
            }

            is PatternIntContext -> {
                if (type != NatType) errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                listOf()
            }

            is PatternSuccContext -> {
                if (type != NatType) errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                getVariablesTypesFromPattern(pattern.pattern(), type)
            }

            is PatternRecordContext -> {
                if (type !is RecordType || pattern.patterns.map { it.label.text }.toSet() !=
                    type.fields.map { it.first }.toSet()
                ) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                buildList {
                    pattern.patterns.forEach {
                        addAll(getVariablesTypesFromPattern(it.pattern(),
                            type.fields.first { f -> f.first == it.label.text }.second
                        )
                        )
                    }
                }
            }

            is PatternTupleContext -> {
                if (type !is TupleType || type.types.size != pattern.patterns.size) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }

                pattern.patterns.withIndex().flatMap {
                    getVariablesTypesFromPattern(it.value, type.types[it.index])
                }
            }

            is PatternVariantContext -> {
                if (type !is VariantType || !type.variants.any { it.first == pattern.label.text }) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                val labelType = type.variants.first { it.first == pattern.label.text }.second
                if (labelType != null && pattern.pattern() == null) {
                    errorPrinter.printError(UnexpectedNullaryVariantPatternError(pattern, type))
                }
                if (labelType == null && pattern.pattern() != null) {
                    errorPrinter.printError(UnexpectedNonNullaryVariantPatternError(pattern, type))
                }
                labelType?.let {
                    getVariablesTypesFromPattern(pattern.pattern(), it)
                } ?: listOf()
            }

            is PatternListContext -> {
                if (type !is ListType) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                pattern.patterns.flatMap { getVariablesTypesFromPattern(it, type.contentType) }
            }

            is PatternConsContext -> {
                if (type !is ListType) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                buildList {
                    addAll(getVariablesTypesFromPattern(pattern.head, type.contentType))
                    addAll(getVariablesTypesFromPattern(pattern.tail, type))
                }
            }

            else -> throw IllegalStateException("Illegal state at pattern")
        }
    }

    override fun visitParenthesisedExpr(ctx: ParenthesisedExprContext): Type = ctx.expr().accept(this)

    override fun visitTail(ctx: TailContext): Type {
        val listType = context.runWithExpected(null) { ctx.list.accept(this) }
        if (listType !is ListType) {
            errorPrinter.printError(NotAListError(ctx, listType))
        }

        return listType
    }

    override fun visitRecord(ctx: RecordContext): Type {
        val expectedType = context.getExpectedType()
        if (expectedType != null && expectedType !is RecordType) errorPrinter.printError(
            UnexpectedRecordError(
                expectedType,
                ctx
            )
        )

        val fields = mutableListOf<Pair<String, Type>>()
        for (binding in ctx.bindings) {
            val type =
                (expectedType as? RecordType)?.fields?.firstOrNull { it.first == binding.name.text }?.second
            fields.add(Pair(binding.name.text, context.runWithExpected(type) {
                binding.rhs.accept(this)
            }))
        }

        val actualType = RecordType(fields)

        if ((expectedType as? RecordType)?.fields != null) {
            val unexpectedFields = fields.subtract(expectedType.fields.toSet())
            if (unexpectedFields.isNotEmpty()) errorPrinter.printError(
                UnexpectedRecordFieldsError(
                    expectedType,
                    actualType,
                    ctx,
                    unexpectedFields
                )
            )

            val missingFields = expectedType.fields.subtract(fields.toSet())
            if (missingFields.isNotEmpty()) errorPrinter.printError(
                MissingRecordFieldsError(
                    expectedType,
                    actualType,
                    ctx,
                    missingFields
                )
            )
        }

        return expectedType as? RecordType ?: actualType
    }

    override fun visitPred(ctx: PredContext): Type {
        val argType = context.runWithExpected(NatType) { ctx.n.accept(this) }
        if (argType != NatType) errorPrinter.printError(UnexpectedTypeForExpressionError(NatType, argType, ctx))

        return NatType
    }

    override fun visitTypeAsc(ctx: TypeAscContext): Type {
        val expectedType = ctx.stellatype().accept(this)
        val expressionType = context.runWithExpected(expectedType) { ctx.expr().accept(this) }

        if (expectedType != expressionType) errorPrinter.printError(
            UnexpectedTypeForExpressionError(
                expectedType,
                expressionType,
                ctx
            )
        )

        return expectedType
    }

    override fun visitNatRec(ctx: NatRecContext): Type {
        val nType = context.runWithExpected(NatType) { ctx.n.accept(this) }
        if (nType != NatType) errorPrinter.printError(UnexpectedTypeForExpressionError(NatType, nType, ctx))

        val zType = ctx.initial.accept(this)
        val expectedSType = FuncType(listOf(NatType), FuncType(listOf(zType), zType))
        val sType = context.runWithExpected(expectedSType) { ctx.step.accept(this) }

        if (expectedSType != sType) errorPrinter.printError(UnexpectedTypeForExpressionError(expectedSType, sType, ctx))

        return zType
    }

    override fun visitDotTuple(ctx: DotTupleContext): Type {
        val tupleType = context.runWithExpected(null) { ctx.expr().accept(this) }
        if (tupleType !is TupleType) {
            errorPrinter.printError(NotATupleError(ctx, tupleType))
        }

        val index = ctx.index.text.toInt() - 1
        if (tupleType.types.size <= index) {
            errorPrinter.printError(TupleIndexOfBoundsError(ctx, index))
        }

        return tupleType.types[ctx.index.text.toInt() - 1]
    }

    override fun visitFix(ctx: FixContext): Type {
        val expressionExpectedType = context.getExpectedType()?.let { FuncType(listOf(it), it) }
        val expressionType = context.runWithExpected(expressionExpectedType) { ctx.expr().accept(this) }
        if (expressionType !is FuncType || expressionType.argTypes.size != 1) {
            errorPrinter.printError(NotAFunctionError(ctx.expr(), expressionType))
        }

        val expectedType = FuncType(expressionType.argTypes, expressionType.argTypes.first())
        if (expectedType != expressionType) {
            errorPrinter.printError(UnexpectedTypeForExpressionError(expectedType, expressionType, ctx))
        }

        return expectedType.argTypes.first()
    }

    override fun visitLetRec(ctx: LetRecContext): Type {
        val variables = mutableListOf<Pair<String, Type>>()

        for (binding in ctx.patternBindings) {
            val pattern = binding.pattern()
            if (pattern !is PatternAscContext) {
                errorPrinter.printError(AmbiguousPatternTypeError(binding.pattern()))
            }

            val expectedPatternType = pattern.stellatype().accept(this)
            val vars = getVariablesInfoFromPattern(binding.pattern(), expectedPatternType)

            val duplicate = vars.map { it.first }
                .groupingBy { it }
                .eachCount()
                .asIterable()
                .firstOrNull { it.value > 1 }

            if (duplicate != null) {
                errorPrinter.printError(DuplicatePatternVariableError(binding.pattern(), duplicate.key))
            }
            variables.addAll(vars)

            val bindingType = context.runWithExpected(expectedPatternType) {
                context.runWithTypes(variables) { binding.expr().accept(this) }
            }

            if (expectedPatternType == bindingType) {
                errorPrinter.printError(UnexpectedPatternForTypeError(bindingType, pattern))
            }

            if (!isExhaustive(listOf(binding.pattern()), bindingType
            )) {
                errorPrinter.printError(NonExhaustiveLetPatternsError(bindingType, ctx))
            }
        }

        return context.runWithTypes(variables) {
            ctx.expr().accept(this)
        }
    }

    override fun visitLet(ctx: LetContext): Type {
        val variables = mutableListOf<Pair<String, Type>>()

        for (binding in ctx.patternBindings) {
            val bindingType = context.runWithExpected(null) {
                context.runWithTypes(variables) { binding.expr().accept(this) }
            }

            if (!isExhaustive(listOf(binding.pattern()), bindingType)) {
                errorPrinter.printError(NonExhaustiveLetPatternsError(bindingType, ctx))
            }

            val vars = getVariablesInfoFromPattern(binding.pattern(), bindingType)
            val duplicate = vars.map { it.first }.groupingBy { it }.eachCount().asIterable()
                .firstOrNull { it.value > 1 }

            if (duplicate != null) {
                errorPrinter.printError(DuplicatePatternVariableError(binding.pattern(), duplicate.key))
            }

            variables.addAll(vars)
        }

        return context.runWithTypes(variables) {
            ctx.expr().accept(this)
        }
    }

    override fun visitTuple(ctx: TupleContext): Type {
        val expected = context.getExpectedType()
        if (expected != null && expected !is TupleType) {
            errorPrinter.printError(UnexpectedTupleError(expected, ctx))
        }

        if (expected is TupleType && expected.types.size != ctx.expr().size) {
            errorPrinter.printError(UnexpectedTupleLengthError(expected, ctx))
        }

        val expressionTypes = mutableListOf<Type>()
        for (i in ctx.exprs.indices) {
            expressionTypes.add(context.runWithExpected((expected as? TupleType)?.types?.get(i)) {
                ctx.exprs[i].accept(this)
            })
        }
        return TupleType(expressionTypes)
    }

    override fun visitConsList(ctx: ConsListContext): Type {
        val expected = context.getExpectedType()
        if (expected != null && expected !is ListType) {
            errorPrinter.printError(UnexpectedListError(expected, ctx))
        }

        val headType = context.runWithExpected((expected as? ListType)?.contentType) {
            ctx.head.accept(this)
        }
        val expectedListType = ListType(headType)
        val tailType = context.runWithExpected(expectedListType) { ctx.tail.accept(this) }
        if (expectedListType != tailType) {
            errorPrinter.printError(UnexpectedTypeForExpressionError(expectedListType, tailType, ctx))
        }

        return expectedListType
    }

    override fun visitTypeFun(ctx: TypeFunContext): Type {
        val paramType = ctx.paramTypes.map { it.accept(this) }
        val returnType = ctx.returnType.accept(this)

        return FuncType(paramType, returnType)
    }

    override fun visitTypeRecord(ctx: TypeRecordContext): Type {
        val fields = mutableListOf<Pair<String, Type>>()
        for (field in ctx.fieldTypes) {
            fields.add(Pair(field.label.text, field.stellatype().accept(this)))
        }

        return RecordType(fields)
    }

    override fun visitTypeTuple(ctx: TypeTupleContext): Type = TupleType(ctx.types.map { it.accept(this) })
    override fun visitTypeSum(ctx: TypeSumContext): Type = SumType(ctx.left.accept(this), ctx.right.accept(this))
    override fun visitTypeVariant(ctx: TypeVariantContext): Type =
        VariantType(ctx.fieldTypes.map { Pair(it.label.text, it.stellatype()?.accept(this)) })

    override fun visitTypeParens(ctx: TypeParensContext): Type = ctx.stellatype().accept(this)
    override fun visitTypeList(ctx: TypeListContext): Type = ListType(ctx.stellatype().accept(this))
    override fun visitConstUnit(ctx: ConstUnitContext): Type = UnitType
    override fun visitConstTrue(ctx: ConstTrueContext): Type = BoolType
    override fun visitConstFalse(ctx: ConstFalseContext): Type = BoolType
    override fun visitConstInt(ctx: ConstIntContext): Type = NatType

    override fun visitTypeBool(ctx: TypeBoolContext) = BoolType
    override fun visitTypeUnit(ctx: TypeUnitContext): Type = UnitType
    override fun visitTypeNat(ctx: TypeNatContext): Type = NatType
    override fun visitTypeRef(ctx: TypeRefContext): Type = RefType(ctx.stellatype().accept(this))

    override fun visitPanic(ctx: PanicContext): Type = context.getExpectedType() ?:
        errorPrinter.printError(AmbiguousPanicTypeError(ctx))

    override fun visitSequence(ctx: SequenceContext): Type {
        val leftType = context.runWithExpected(UnitType) {
            ctx.expr1.accept(this)
        }

        if (leftType == UnitType) {
            errorPrinter.printError(UnexpectedTypeForExpressionError(UnitType, leftType, ctx.expr1))
        }

        return ctx.expr2.accept(this)
    }

    override fun visitConstMemory(ctx: ConstMemoryContext): Type {
        val expectedType = context.getExpectedType() ?:
            errorPrinter.printError(AmbiguousReferenceTypeError(ctx))

        if (expectedType !is RefType) {
            errorPrinter.printError(UnexpectedMemoryAddressError(ctx, expectedType))
        }

        return expectedType
    }

    override fun visitRef(ctx: RefContext): Type {
        val nestedType = context.runWithExpected((context.getExpectedType() as? RefType)?.innerType) {
            ctx.expr().accept(this)
        }

        return RefType(nestedType)
    }

    override fun visitDeref(ctx: DerefContext): Type {
        val expressionType = context.runWithExpected(context.getExpectedType()?.let { RefType(it) }) {
            ctx.expr().accept(this)
        }
        if (expressionType !is RefType) {
            errorPrinter.printError(NotAReferenceError(ctx))
        }

        return expressionType.innerType
    }

    override fun visitAssign(ctx: AssignContext): Type {
        val leftType = context.runWithExpected(null) { ctx.lhs.accept(this) }

        if (leftType !is RefType) {
            errorPrinter.printError(NotAReferenceError(ctx))
        }

        val rightType = context.runWithExpected(leftType.innerType) { ctx.rhs.accept(this) }

        if (leftType.innerType != rightType) {
            errorPrinter.printError(UnexpectedTypeForExpressionError(leftType.innerType, rightType, ctx))
        }

        return UnitType
    }


    private fun getVariablesInfoFromPattern(pattern: PatternContext, type: Type): List<Pair<String, Type>> {
        return when (pattern) {
            is PatternVarContext -> listOf(Pair(pattern.name.text, type))

            is ParenthesisedPatternContext -> getVariablesInfoFromPattern(pattern.pattern(), type)

            is PatternFalseContext -> {
                if (type != BoolType) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                listOf()
            }

            is PatternTrueContext -> {
                if (type != BoolType) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                listOf()
            }

            is PatternUnitContext -> {
                if (type != UnitType) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                listOf()
            }

            is PatternInlContext -> {
                if (type !is SumType) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                getVariablesInfoFromPattern(pattern.pattern(), type.right)
            }

            is PatternInrContext -> {
                if (type !is SumType) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                getVariablesInfoFromPattern(pattern.pattern(), type.right)
            }

            is PatternIntContext -> {
                if (type != NatType) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                listOf()
            }

            is PatternSuccContext -> {
                if (type != NatType) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                getVariablesInfoFromPattern(pattern.pattern(), type)
            }

            is PatternRecordContext -> {
                if (type !is RecordType || pattern.patterns.map { it.label.text }.toSet() !=
                    type.fields.map { it.first }.toSet()) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }

                buildList { pattern.patterns.forEach { addAll(getVariablesInfoFromPattern(it.pattern(),
                    type.fields.first { f -> f.first == it.label.text }.second)) } }
            }

            is PatternTupleContext -> {
                if (type !is TupleType || type.types.size != pattern.patterns.size) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }

                pattern.patterns.withIndex().flatMap {
                    getVariablesInfoFromPattern(it.value, type.types[it.index])
                }
            }

            is PatternVariantContext -> {
                if (type !is VariantType || !type.variants.any { it.first == pattern.label.text }) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                val labelType = type.variants.first { it.first == pattern.label.text }.second
                if (labelType != null && pattern.pattern() == null) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                if (labelType == null && pattern.pattern() != null) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                labelType?.let {
                    getVariablesInfoFromPattern(pattern.pattern(), it)
                } ?: listOf()
            }

            is PatternListContext -> {
                if (type !is ListType) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                pattern.patterns.flatMap { getVariablesInfoFromPattern(it, type.contentType) }
            }

            is PatternConsContext -> {
                if (type !is ListType) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                buildList {
                    addAll(getVariablesInfoFromPattern(pattern.head, type.contentType))
                    addAll(getVariablesInfoFromPattern(pattern.tail, type))
                }
            }

            is PatternAscContext -> getVariablesInfoFromPattern(pattern.pattern(), type)

            else -> throw IllegalStateException("Unexpected pattern context")
        }
    }
}