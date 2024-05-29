package me.avrong.stella

import me.avrong.stella.context.StellaExtensions
import StellaParser.*
import StellaParserBaseVisitor
import me.avrong.stella.context.TypeCheckContext
import me.avrong.stella.error.*
import me.avrong.stella.error.AmbiguousPanicTypeError
import me.avrong.stella.error.AmbiguousReferenceTypeError
import me.avrong.stella.error.NotAReferenceError
import me.avrong.stella.error.UnexpectedMemoryAddressError
import me.avrong.stella.solver.Constraint
import me.avrong.stella.type.RefType
import me.avrong.stella.type.*
import me.avrong.stella.type.BoolType.substitute

class TypeCheckVisitor(
    private val context: TypeCheckContext,
    private val errorPrinter: TypeCheckErrorPrinter
) : StellaParserBaseVisitor<Type>() {

    override fun visitProgram(ctx: ProgramContext): Type {
        var mainType: Type? = null

        for (extension in ctx.extensions) {
            extension.accept(this)
        }

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

    override fun visitAnExtension(ctx: AnExtensionContext): Type {
        val extensionNames = ctx.extensionNames.map { it.text }
        context.extensions.addAll(extensionNames)

        return UnitType
    }

    override fun visitDeclFun(ctx: DeclFunContext): Type {
        val params = ctx.paramDecls
        val paramTypes = params.map { Pair(it.name.text, it.paramType.accept(this)) }

        val returnType = ctx.returnType.accept(this)
        val funcType = FuncType(paramTypes.map { it.second }, returnType)

        return context.runWithTypes<Type>(paramTypes + Pair(ctx.name.text, funcType)) {
            val nestedFunctions = ctx.localDecls.filterIsInstance<DeclFunContext>().map {
                Pair(it.name.text, it.accept(this))
            }

            context.runWithTypes(nestedFunctions) {
                context.runWithExpected(returnType) {
                    val returnExpressionType = ctx.returnExpr.accept(this)

                    if (returnExpressionType.isNotApplicable(returnType)) {
                        errorPrinter.printError(unexpectedTypeError(returnType, returnExpressionType, ctx.returnExpr))
                    }

                    funcType
                }
            }
        }
    }

    override fun visitIsZero(ctx: IsZeroContext): Type {
        val argType = context.runWithExpected(NatType) { ctx.n.accept(this) }
        if (argType.isNotApplicable(NatType)) errorPrinter.printError(unexpectedTypeError(NatType, argType, ctx))
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

        if (
            expectedType != null
            && expectedType !is ListType
            && expectedType !is TopType
            && !context.isTypeReconstructionEnabled()
            && expectedType !is UniversalTypeVariable
        ) {
            errorPrinter.printError(UnexpectedListError(expectedType, ctx))
        }

        if (ctx.exprs.isEmpty()) {
            return expectedType ?: if (context.extensions.contains(StellaExtensions.AMBIGUOUS_TYPE_AS_BOTTOM)) {
                ListType(BotType)
            } else {
                errorPrinter.printError(AmbiguousListTypeError(ctx))
            }
        }

        val firstElemType = context.runWithExpected((expectedType as? ListType)?.contentType) {
            ctx.exprs.first().accept(this)
        }

        context.runWithExpected(firstElemType) {
            ctx.exprs.drop(1).forEach {
                val exprType = it.accept(this)
                if (exprType.isNotApplicable(firstElemType)) {
                    errorPrinter.printError(unexpectedTypeError(firstElemType, exprType, ctx))
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
        if (
            expectedType != null
            && expectedType !is FuncType
            && expectedType !is TopType
            && !context.isTypeReconstructionEnabled()
            && expectedType !is UniversalTypeVariable
        ) {
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

            if (expectedParamType != null && paramType.isNotApplicable(expectedParamType)) {
                if (context.extensions.contains(StellaExtensions.STRUCTURAL_SUBTYPRING)) {
                    errorPrinter.printError(UnexpectedSubtypeError(expectedParamType, paramType, ctx))
                } else {
                    errorPrinter.printError(UnexpectedTypeForParameterError(paramType, expectedParamType, ctx))
                }
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
            UnexpectedDataForNullaryLabelError(ctx, expectedType)
        )
        if (expectedLabel.second != null && ctx.rhs == null) errorPrinter.printError(
            MissingDataForLabelError(ctx, expectedType)
        )

        if (ctx.rhs != null) {
            val variantType = context.runWithExpected(expectedLabel.second) { ctx.rhs.accept(this) }

            if (variantType.isNotApplicable(expectedLabel.second!!)) errorPrinter.printError(
                unexpectedTypeError(expectedLabel.second!!, variantType, ctx)
            )
        }

        return expectedType
    }


    override fun visitIf(ctx: IfContext): Type {
        val conditionType = context.runWithExpected(BoolType) { ctx.condition.accept(this) }
        if (conditionType.isNotApplicable(BoolType)) errorPrinter.printError(
            unexpectedTypeError(BoolType, conditionType, ctx)
        )

        val thenType = ctx.thenExpr.accept(this)
        val elseType = context.runWithExpected(thenType) { ctx.elseExpr.accept(this) }

        if (elseType.isNotApplicable(thenType)) {
            errorPrinter.printError(unexpectedTypeError(thenType, elseType, ctx))
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
            if (exprType.isNotApplicable(expectedType)) errorPrinter.printError(
                unexpectedTypeError(expectedType, exprType, ctx)
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
        if (argType.isNotApplicable(NatType)) {
            errorPrinter.printError(unexpectedTypeError(NatType, argType, ctx))
        }

        return NatType
    }

    override fun visitInl(ctx: InlContext): Type {
        val expectedType = context.getExpectedType()

        if (expectedType == null && !context.extensions.contains(StellaExtensions.AMBIGUOUS_TYPE_AS_BOTTOM)) {
            errorPrinter.printError(AmbiguousSumTypeError(ctx))
        }

        if (
            expectedType != null
            && expectedType !is SumType
            && expectedType !is TopType
            && !context.isTypeReconstructionEnabled()
            && expectedType !is UniversalTypeVariable
        ) {
            errorPrinter.printError(UnexpectedInjectionError(expectedType, ctx))
        }

        val leftType = context.runWithExpected((expectedType as? SumType)?.left) {
            ctx.expr().accept(this)
        }

        return SumType(leftType, (expectedType as? SumType)?.right ?: BotType)
    }

    override fun visitInr(ctx: InrContext): Type {
        val expectedType = context.getExpectedType()

        if (expectedType == null && !context.extensions.contains(StellaExtensions.AMBIGUOUS_TYPE_AS_BOTTOM)) {
            errorPrinter.printError(AmbiguousSumTypeError(ctx))
        }

        if (
            expectedType != null
            && expectedType !is SumType
            && expectedType !is TopType
            && !context.isTypeReconstructionEnabled()
            && expectedType !is UniversalTypeVariable
        ) {
            errorPrinter.printError(UnexpectedInjectionError(expectedType, ctx))
        }

        val rightType = context.runWithExpected((expectedType as? SumType)?.right) {
            ctx.expr().accept(this)
        }

        return SumType((expectedType as? SumType)?.left ?: BotType, rightType)
    }

    override fun visitMatch(ctx: MatchContext): Type {
        val expressionType = context.runWithExpected(null) { ctx.expr().accept(this) }
        val cases = ctx.cases
        if (cases.isEmpty()) {
            errorPrinter.printError(IllegalEmptyMatchingError(ctx))
        }

        val isExhaustive = isExhaustive(
            cases.map { it.pattern() },
            expressionType,
            this,
            context.constraints
        )

        val casesType = processMatchCase(cases.first(), expressionType)

        cases.drop(1).forEach {
            val caseType = processMatchCase(it, expressionType)
            if (caseType.isNotApplicable(casesType)) {
                errorPrinter.printError(unexpectedTypeError(casesType, caseType, ctx))
            }
        }

        if (!isExhaustive) errorPrinter.printError(NonExhaustiveMatchPatternsError(expressionType, ctx))

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
                if (type != BoolType && type !is TypeVariable) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                listOf()
            }

            is PatternTrueContext -> {
                if (type != BoolType && type !is TypeVariable) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                listOf()
            }

            is PatternUnitContext -> {
                if (type != UnitType && type !is TypeVariable) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                listOf()
            }

            is PatternInlContext -> {
                if (type !is SumType && type !is TypeVariable) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }

                if (type is SumType) {
                    getVariablesInfoFromPattern(pattern.pattern(), type.left)
                } else {
                   getVariablesInfoFromPattern(pattern.pattern(), TypeVariable(++context.typeVariablesNum))
                }
            }

            is PatternInrContext -> {
                if (type !is SumType && type !is TypeVariable) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }

                if (type is SumType) {
                    getVariablesInfoFromPattern(pattern.pattern(), type.right)
                } else {
                    getVariablesInfoFromPattern(pattern.pattern(), TypeVariable(++context.typeVariablesNum))
                }
            }

            is PatternIntContext -> {
                if (type != NatType && type !is TypeVariable) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                listOf()
            }

            is PatternSuccContext -> {
                if (type != NatType && type !is TypeVariable) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }
                getVariablesTypesFromPattern(pattern.pattern(), NatType)
            }

            is PatternRecordContext -> {
                if ((type !is RecordType
                        || pattern.patterns.map { it.label.text }.toSet() != type.fields.map { it.first }.toSet()
                    ) && type !is TypeVariable
                ) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }

                if (type is RecordType) {
                    buildList {
                        pattern.patterns.forEach {
                            addAll(getVariablesTypesFromPattern(it.pattern(),
                                type.fields.first { f -> f.first == it.label.text }.second
                            ))
                        }
                    }
                } else {
                    buildList {
                        pattern.patterns.forEach {
                            addAll(getVariablesInfoFromPattern(it.pattern(), TypeVariable(++context.typeVariablesNum)))
                        }
                    }
                }
            }

            is PatternTupleContext -> {
                if ((type !is TupleType || type.types.size != pattern.patterns.size) && type !is TypeVariable) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }

                if (type is TupleType) {
                    pattern.patterns.withIndex().flatMap {
                        getVariablesInfoFromPattern(it.value, type.types[it.index])
                    }
                } else {
                    pattern.patterns.withIndex().flatMap {
                        getVariablesInfoFromPattern(it.value, TypeVariable(++context.typeVariablesNum))
                    }
                }
            }

            is PatternVariantContext -> {
                if (
                    (type !is VariantType || !type.variants.any { it.first == pattern.label.text })
                    && type !is TypeVariable
                ) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }

                if (type is VariantType) {
                    val labelType = type.variants.first { it.first == pattern.label.text }.second

                    if (labelType != null && pattern.pattern() == null) {
                        errorPrinter.printError(UnexpectedNullaryVariantPatternError(pattern, type))
                    }

                    if (labelType == null && pattern.pattern() != null) {
                        errorPrinter.printError(UnexpectedNonNullaryVariantPatternError(pattern, type))
                    }

                    labelType?.let {
                        getVariablesInfoFromPattern(pattern.pattern(), it)
                    } ?: listOf()
                } else {
                    getVariablesInfoFromPattern(pattern.pattern(), TypeVariable(++context.typeVariablesNum))
                }
            }

            is PatternListContext -> {
                if (type !is ListType && type !is TypeVariable) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }

                if (type is ListType) {
                    pattern.patterns.flatMap { getVariablesInfoFromPattern(it, type.contentType) }
                } else {
                    pattern.patterns.flatMap { getVariablesInfoFromPattern(it, TypeVariable(++context.typeVariablesNum)) }
                }
            }

            is PatternConsContext -> {
                if (type !is ListType && type !is TypeVariable) {
                    errorPrinter.printError(UnexpectedPatternForTypeError(type, pattern))
                }

                if (type is ListType) {
                    buildList {
                        addAll(getVariablesInfoFromPattern(pattern.head, type.contentType))
                        addAll(getVariablesInfoFromPattern(pattern.tail, type))
                    }
                } else {
                    buildList {
                        addAll(getVariablesInfoFromPattern(pattern.head, TypeVariable(++context.typeVariablesNum)))
                    }
                }
            }

            is PatternCastAsContext -> getVariablesInfoFromPattern(pattern.pattern(), pattern.stellatype().accept(this))

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
        if (
            expectedType != null
            && expectedType !is RecordType
            && expectedType !is TopType
            && expectedType !is UniversalTypeVariable
        ) {
            errorPrinter.printError(UnexpectedRecordError(expectedType, ctx))
        }

        val fields = mutableListOf<Pair<String, Type>>()
        for (binding in ctx.bindings) {
            val type = (expectedType as? RecordType)?.fields
                ?.firstOrNull { it.first == binding.name.text }?.second

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

            val missingFields = (expectedType.fields - fields.toSet()).map { it.first }.toSet()
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
        if (argType.isNotApplicable(NatType)) errorPrinter.printError(unexpectedTypeError(NatType, argType, ctx))

        return NatType
    }

    override fun visitTypeAsc(ctx: TypeAscContext): Type {
        val expectedType = ctx.stellatype().accept(this)
        val expressionType = context.runWithExpected(expectedType) { ctx.expr().accept(this) }

        if (expressionType.isNotApplicable(expectedType)) errorPrinter.printError(
            unexpectedTypeError(expectedType, expressionType, ctx)
        )

        return expectedType
    }

    override fun visitNatRec(ctx: NatRecContext): Type {
        val nType = context.runWithExpected(NatType) { ctx.n.accept(this) }
        if (nType.isNotApplicable(NatType)) errorPrinter.printError(unexpectedTypeError(NatType, nType, ctx))

        val zType = ctx.initial.accept(this)
        val expectedSType = FuncType(listOf(NatType), FuncType(listOf(zType), zType))
        val sType = context.runWithExpected(expectedSType) { ctx.step.accept(this) }

        if (sType.isNotApplicable(expectedSType))
            errorPrinter.printError(unexpectedTypeError(expectedSType, sType, ctx))

        return zType
    }

    override fun visitDotTuple(ctx: DotTupleContext): Type {
        val tupleType = context.runWithExpected(null) { ctx.expr().accept(this) }
        if (tupleType !is TupleType && !context.isTypeReconstructionEnabled()) {
            errorPrinter.printError(NotATupleError(ctx, tupleType))
        }

        val index = ctx.index.text.toInt() - 1
        if (tupleType is TupleType && tupleType.types.size <= index || tupleType is TypeVariable && index >= 3) {
            errorPrinter.printError(TupleIndexOfBoundsError(ctx, index))
        }

        return if (tupleType is TupleType) {
            tupleType.types[ctx.index.text.toInt() - 1]
        } else {
            TypeVariable(++context.typeVariablesNum).also {
                context.constraints.add(
                    Constraint(
                        tupleType, TupleType(
                            if (index == 1)
                                listOf(it, TypeVariable(++context.typeVariablesNum)) else
                                listOf(TypeVariable(++context.typeVariablesNum), it)
                        ), ctx
                    )
                )
            }
        }
    }

    override fun visitFix(ctx: FixContext): Type {
        val expressionExpectedType = context.getExpectedType()?.let { FuncType(listOf(it), it) }
        val expressionType = context.runWithExpected(expressionExpectedType) { ctx.expr().accept(this) }
        if ((expressionType !is FuncType || expressionType.argTypes.size != 1) && !context.isTypeReconstructionEnabled()) {
            errorPrinter.printError(NotAFunctionError(ctx.expr(), expressionType))
        }

        val expectedType = if (expressionType is FuncType) FuncType(expressionType.argTypes, expressionType.argTypes.first())
            else TypeVariable(++context.typeVariablesNum).let { FuncType(listOf(it), it) }

        if (expressionType.isNotApplicable(expectedType)) {
            errorPrinter.printError(unexpectedTypeError(expectedType, expressionType, ctx))
        }

        return expectedType.argTypes.first()
    }

    override fun visitLetRec(ctx: LetRecContext): Type {
        val variables = mutableListOf<Pair<String, Type>>()

        for (binding in ctx.patternBindings) {
            val (pattern, expectedPatternType) = unwrapPattern(binding.pattern(), this)

            if (expectedPatternType == null)
                errorPrinter.printError(AmbiguousPatternTypeError(binding.pattern()))

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

            if (bindingType.isNotApplicable(expectedPatternType)) {
                errorPrinter.printError(UnexpectedPatternForTypeError(bindingType, pattern))
            }

            if (!isExhaustive(listOf(binding.pattern()), bindingType, this, context.constraints)) {
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
            val (pattern, expectedPatternType) = unwrapPattern(binding.pattern(), this)

            val bindingType = context.runWithExpected(null) {
                context.runWithTypes(variables) { binding.expr().accept(this) }
            }

            if (expectedPatternType != null && bindingType.isNotApplicable(expectedPatternType)) {
                errorPrinter.printError(UnexpectedPatternForTypeError(expectedPatternType, pattern))
            }

            if (!isExhaustive(listOf(binding.pattern()), bindingType, this, context.constraints)) {
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
        if (
            expected != null
            && expected !is TupleType
            && expected !is TopType
            && !context.isTypeReconstructionEnabled()
            && expected !is UniversalTypeVariable
        ) {
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
        if (
            expected != null
            && expected !is ListType
            && expected !is TopType
            && context.isTypeReconstructionEnabled()
            && expected !is UniversalTypeVariable
        ) {
            errorPrinter.printError(UnexpectedListError(expected, ctx))
        }

        val headType = context.runWithExpected((expected as? ListType)?.contentType) {
            ctx.head.accept(this)
        }
        val expectedListType = ListType(headType)
        val tailType = context.runWithExpected(expectedListType) { ctx.tail.accept(this) }
        if (tailType.isNotApplicable(expectedListType)) {
            errorPrinter.printError(unexpectedTypeError(expectedListType, tailType, ctx))
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
    override fun visitTypeTop(ctx: TypeTopContext?): Type = TopType
    override fun visitTypeBottom(ctx: TypeBottomContext?): Type = BotType

    override fun visitPanic(ctx: PanicContext): Type = context.getExpectedType()
        ?: if (context.extensions.contains(StellaExtensions.AMBIGUOUS_TYPE_AS_BOTTOM)) {
            BotType
        } else {
            errorPrinter.printError(AmbiguousPanicTypeError(ctx))
        }

    override fun visitSequence(ctx: SequenceContext): Type {
        val leftType = context.runWithExpected(UnitType) {
            ctx.expr1.accept(this)
        }

        if (leftType.isNotApplicable(UnitType)) {
            errorPrinter.printError(unexpectedTypeError(UnitType, leftType, ctx.expr1))
        }

        return ctx.expr2.accept(this)
    }

    override fun visitConstMemory(ctx: ConstMemoryContext): Type {
        val expectedType = context.getExpectedType() ?:
            errorPrinter.printError(AmbiguousReferenceTypeError(ctx))

        if (expectedType !is RefType && expectedType !is TopType) {
            errorPrinter.printError(UnexpectedMemoryAddressError(ctx, expectedType))
        }

        return expectedType
    }

    override fun visitRef(ctx: RefContext): Type {
        val expectedType = context.getExpectedType()

        if (
            expectedType != null
            && expectedType !is RefType
            && expectedType !is TopType
            && expectedType !is UniversalTypeVariable
        ) {
            errorPrinter.printError(UnexpectedReferenceError(expectedType, ctx.expr()))
        }

        val expectedRefType = expectedType as? RefType

        val innerType = context.runWithExpected(expectedRefType?.innerType) {
            ctx.expr().accept(this)
        }

        if (expectedRefType != null && !innerType.isApplicable(expectedRefType.innerType)) {
            unexpectedTypeError(expectedRefType.innerType, innerType, ctx)
        }

        return expectedType ?: RefType(innerType)
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

        if (rightType.isNotApplicable(leftType.innerType)) {
            errorPrinter.printError(unexpectedTypeError(leftType.innerType, rightType, ctx))
        }

        return UnitType
    }

    override fun visitDeclExceptionType(ctx: DeclExceptionTypeContext): Type {
        val type = ctx.exceptionType.accept(this)
        context.declaredExceptionsType = type

        return type
    }

    override fun visitTryCatch(ctx: TryCatchContext): Type {
        val tryType = ctx.tryExpr.accept(this)
        val variablesFromPattern = getVariablesInfoFromPattern(
            ctx.pattern(),
            context.declaredExceptionsType ?: errorPrinter.printError(ExceptionTypeNotDeclaredError(ctx))
        )

        val fallbackType = context.runWithTypes(variablesFromPattern) {
            context.runWithExpected(tryType) { ctx.fallbackExpr.accept(this) }
        }

        if (fallbackType.isNotApplicable(tryType)) {
            errorPrinter.printError(unexpectedTypeError(tryType, fallbackType, ctx))
        }

        return tryType
    }

    override fun visitThrow(ctx: ThrowContext): Type {
        val declaredExceptionType = context.declaredExceptionsType
            ?: errorPrinter.printError(ExceptionTypeNotDeclaredError(ctx))

        val exprType = context.runWithExpected(declaredExceptionType) { ctx.expr().accept(this) }
        if (exprType.isNotApplicable(declaredExceptionType)) {
            errorPrinter.printError(unexpectedTypeError(declaredExceptionType, exprType, ctx))
        }

        return context.getExpectedType() ?: if (context.extensions.contains(StellaExtensions.AMBIGUOUS_TYPE_AS_BOTTOM)) {
            BotType
        } else {
            errorPrinter.printError(AmbiguousThrowTypeError(ctx))
        }
    }

    override fun visitTryWith(ctx: TryWithContext): Type {
        val tryType = ctx.tryExpr.accept(this)
        val fallbackType = context.runWithExpected(tryType) { ctx.fallbackExpr.accept(this) }

        if (fallbackType.isNotApplicable(tryType)) {
            errorPrinter.printError(unexpectedTypeError(tryType, fallbackType, ctx))
        }

        return tryType
    }

    override fun visitTypeCast(ctx: TypeCastContext): Type {
        context.runWithExpected(null) { ctx.expr().accept(this) }
        return ctx.stellatype().accept(this)
    }

    override fun visitDeclExceptionVariant(ctx: DeclExceptionVariantContext): Type {
        val type = ctx.stellatype().accept(this)
        val label = ctx.name.text

        context.declaredExceptionsType =
            VariantType(((context.declaredExceptionsType as? VariantType)?.variants ?: emptyList()) + (label to type))

        return type
    }

    override fun visitTryCastAs(ctx: TryCastAsContext): Type {
        context.runWithExpected(null) { ctx.tryExpr.accept(this) }
        val variablesFromPattern = getVariablesInfoFromPattern(ctx.pattern(), ctx.type_.accept(this))

        val bodyType = context.runWithTypes(variablesFromPattern) { ctx.expr_.accept(this) }
        val fallbackType = context.runWithExpected(bodyType) { ctx.fallbackExpr.accept(this) }

        if (!fallbackType.isApplicable(bodyType)) {
            unexpectedTypeError(fallbackType, bodyType, ctx)
        }

        return bodyType
    }

    override fun visitDeclFunGeneric(ctx: DeclFunGenericContext): Type {
        val typeParams = ctx.generics.map { UniversalTypeVariable(it.text) }
        val params = ctx.paramDecls
        val paramsInfo = context.runWithGenerics(typeParams) {
            params.map { Pair(it.name.text, it.paramType.accept(this)) }
        }
        val returnType = context.runWithGenerics(typeParams) { ctx.returnType.accept(this) }
        var funcType: Type = FuncType(paramsInfo.map { it.second }, returnType)
        if (typeParams.isNotEmpty()) {
            funcType = UniversalType(typeParams, funcType)
        }

        return context.runWithTypes(paramsInfo.plus(Pair(ctx.name.text, funcType))) {
            val nestedFunctions = ctx.localDecls.filterIsInstance<DeclFunContext>().map {
                Pair(it.name.text, it.accept(this))
            }
            context.runWithTypes(nestedFunctions) {
                context.runWithExpected(returnType) {
                    context.runWithGenerics(typeParams) {
                        val returnExpressionType = ctx.returnExpr.accept(this)

                        if (!returnExpressionType.isApplicable(returnType)) {
                            unexpectedTypeError(returnType, returnExpressionType, ctx.returnExpr)
                        }

                        funcType
                    }
                }
            }
        }
    }

    override fun visitTypeAbstraction(ctx: TypeAbstractionContext): Type {
        val generics = ctx.generics.map { UniversalTypeVariable(it.text) }

        var expectedType: Type? = context.getExpectedType() as? UniversalType
        if (expectedType is UniversalType && expectedType.variables.size == generics.size) {
            expectedType = expectedType.nestedType.substitute(
                expectedType.variables.mapIndexed { index, typeVar -> Pair(typeVar, generics[index]) }.toMap()
            )
        }

        val nestedType = context.runWithExpected(expectedType) {
            context.runWithGenerics(generics) { ctx.expr().accept(this) }
        }
        return UniversalType(generics, nestedType)
    }

    override fun visitTypeApplication(ctx: TypeApplicationContext): Type {
        val funType = ctx.`fun`.accept(this)
        if (funType !is UniversalType) {
            errorPrinter.printError(NotAGenericFunctionError(ctx))
        }
        if (ctx.types.size != funType.variables.size) {
            errorPrinter.printError(
                IncorrectNumberOfTypeArgumentsError(ctx.types.size, funType.variables.size, ctx)
            )
        }

        val typeArgs = ctx.types.map { it.accept(this) }
        val typesMapping = funType.variables
            .mapIndexed { index, typeVar -> Pair(typeVar, typeArgs[index]) }.toMap()

        return funType.substitute(typesMapping)
    }

    override fun visitTypeVar(ctx: TypeVarContext): Type {
        return context.getGeneric(ctx.name.text) ?: run {
            errorPrinter.printError(UndefinedTypeVariableError(ctx.name.text))
        }
    }

    override fun visitTypeAuto(ctx: TypeAutoContext?): Type =
        TypeVariable(++context.typeVariablesNum)

    override fun visitTypeForAll(ctx: TypeForAllContext): Type {
        val typeArgs = ctx.types.map { UniversalTypeVariable(it.text) }
        return UniversalType(typeArgs, context.runWithGenerics(typeArgs) { ctx.stellatype().accept(this) })
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

    private fun unexpectedTypeError(expectedType: Type, actualType: Type, expression: ExprContext): CheckError =
        if (context.extensions.contains(StellaExtensions.STRUCTURAL_SUBTYPRING)) {
            if (actualType is FuncType && expectedType is FuncType) {
                if (actualType.argTypes.size != expectedType.argTypes.size) {
                    IncorrectNumberOfArgumentsError(
                        actualType.argTypes.size,
                        expectedType.argTypes.size,
                        expression
                    )
                }
            } else if (expectedType is RecordType && actualType is RecordType) {
                val thisFields = actualType.fields.toMap()
                val expectedFields = expectedType.fields.toMap()
                if ((expectedFields - thisFields.keys).isNotEmpty()) {
                    MissingRecordFieldsError(
                        expectedType,
                        actualType,
                        expression,
                        (expectedFields - thisFields.keys).map { it.key }.toSet()
                    )
                }
            } else if (actualType is TupleType && expectedType is TupleType) {
                if (actualType.types.size != expectedType.types.size)  {
                    UnexpectedTupleLengthError(expectedType, expression)
                }
            } else if (actualType is VariantType && expectedType is VariantType) {
                val thisVariants = actualType.variants.toMap()
                val expectedVariants = expectedType.variants.toMap()

                if ((thisVariants - expectedVariants.keys).isNotEmpty()) {
                    UnexpectedVariantLabelError(
                        (thisVariants - expectedVariants.keys).keys.first(),
                        expectedType, expression
                    )
                } else if (!thisVariants.keys.all { expectedVariants.containsKey(it) }) {
                    UnexpectedVariantLabelError(
                        thisVariants.keys.first { !expectedVariants.containsKey(it) },
                        expectedType, expression
                    )
                }
            }

            UnexpectedSubtypeError(expectedType, actualType, expression)
        } else {
            UnexpectedTypeForExpressionError(expectedType, actualType, expression)
        }

    private fun Type.isApplicable(other: Type): Boolean {
        val structuralSubtyping = context.extensions.contains(StellaExtensions.STRUCTURAL_SUBTYPRING)
        return this.isApplicable(other, structuralSubtyping)
    }

    private fun Type.isNotApplicable(other: Type): Boolean = !this.isApplicable(other)

    private fun TypeCheckContext.isTypeReconstructionEnabled(): Boolean =
        extensions.contains(StellaExtensions.TYPE_RECONSTRUCTION)
}