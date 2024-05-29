package me.avrong.stella.solver

import StellaParser
import me.avrong.stella.type.*

fun solveConstraints(constraints: List<Constraint>): List<Pair<TypeVariable, Type>> {
    if (constraints.isEmpty()) {
        return listOf()
    }

    val constraint = constraints.first()
    val tail = constraints.drop(1)

    when {
        constraint.lhv is TypeVariable && constraint.rhv is TypeVariable && constraint.lhv == constraint.rhv -> {
            return solveConstraints(tail)
        }

        constraint.lhv is TypeVariable && !constraint.lhv.containsIn(constraint.rhv, constraint.expression) -> {
            return solveConstraints(tail.map { it.substitute(constraint.lhv, constraint.rhv) }) +
                listOf(Pair(constraint.lhv, constraint.rhv))
        }

        constraint.rhv is TypeVariable && !constraint.rhv.containsIn(constraint.lhv, constraint.expression) -> {
            return solveConstraints(tail.map { it.substitute(constraint.rhv, constraint.lhv) }) +
                listOf(Pair(constraint.rhv, constraint.lhv))
        }

        constraint.lhv is FuncType && constraint.rhv is FuncType -> {
            return solveConstraints(tail + listOf(
                Constraint(constraint.lhv.argTypes.first(), constraint.rhv.argTypes.first(), constraint.expression),
                Constraint(constraint.lhv.returnType, constraint.rhv.returnType, constraint.expression)
            ))
        }

        constraint.lhv is ListType && constraint.rhv is ListType -> {
            return solveConstraints(tail +
                listOf(Constraint(
                    constraint.lhv.contentType,
                    constraint.rhv.contentType,
                    constraint.expression
                ))
            )
        }

        constraint.lhv is SumType && constraint.rhv is SumType -> {
            return solveConstraints(tail + listOf(
                Constraint(constraint.lhv.left, constraint.rhv.left, constraint.expression),
                Constraint(constraint.lhv.right, constraint.rhv.right, constraint.expression)
            ))
        }

        constraint.lhv is TupleType && constraint.rhv is TupleType -> {
            if (constraint.lhv.types.size == 2 && constraint.rhv.types.size == 2) {
                return solveConstraints(tail + listOf(
                    Constraint(constraint.lhv.types[0], constraint.rhv.types[0], constraint.expression),
                    Constraint(constraint.lhv.types[1], constraint.rhv.types[1], constraint.expression)
                ))
            }
        }

        constraint.lhv.isApplicable(constraint.rhv, true) -> {
            return solveConstraints(tail)
        }
    }

    throw SolveFailException(constraint.lhv, constraint.rhv, constraint.expression)
}

private fun TypeVariable.containsIn(type: Type, expression: StellaParser.ExprContext): Boolean {
    val contains = when (type) {
        NatType, BoolType, UnitType -> false
        is FuncType -> containsIn(type.argTypes.first(), expression) || containsIn(type.returnType, expression)
        is TupleType -> containsIn(type.types[0], expression) || containsIn(type.types[1], expression)
        is SumType -> containsIn(type.left, expression) || containsIn(type.right, expression)
        is ListType -> containsIn(type.contentType, expression)
        is TypeVariable -> this == type
        else -> false
    }

    if (contains) throw OccursInfiniteTypeException(this, type, expression)

    return false
}

private fun Constraint.substitute(typeVar: TypeVariable, type: Type): Constraint {
    fun Type.substitute(): Type {
        return when (this) {
            typeVar -> type
            is FuncType -> FuncType(argTypes.map { it.substitute() }, returnType.substitute())
            is TupleType -> TupleType(types.map { it.substitute() })
            is SumType -> SumType(left.substitute(), right.substitute())
            is ListType -> ListType(contentType.substitute())
            else -> this
        }
    }

    return Constraint(lhv.substitute(), rhv.substitute(), expression)
}