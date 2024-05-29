package me.avrong.stella.context

import me.avrong.stella.solver.Constraint
import me.avrong.stella.type.Type
import me.avrong.stella.type.UniversalTypeVariable

data class TypeCheckContext(
    private val variableTypes: MutableMap<String, MutableList<Type>> = mutableMapOf(),
    private val expectedTypes: MutableList<Type?> = mutableListOf(),
    var declaredExceptionsType: Type? = null,
    val extensions: MutableSet<String> = mutableSetOf(),
    val constraints: MutableList<Constraint> = mutableListOf(),
    var typeVariablesNum: Int = 0,
    val generics: MutableList<List<UniversalTypeVariable>> = mutableListOf()
) {
    fun getType(name: String): Type? = variableTypes[name]?.lastOrNull()

    fun setType(variable: String, type: Type) {
        variableTypes.getOrPut(variable) { mutableListOf() }
        variableTypes[variable]!!.add(type)
    }

    private fun removeType(variable: String) {
        val typesInfo = variableTypes[variable]!!
        typesInfo.removeLast()

        if (typesInfo.isEmpty()) {
            variableTypes.remove(variable)
        }
    }

    fun getExpectedType(): Type? {
        return expectedTypes.lastOrNull()
    }

    fun <T> runWithTypes(types: List<Pair<String, Type>>, action: () -> T): T {
        for (type in types) {
            setType(type.first, type.second)
        }

        try {
            return action()
        } finally {
            types.forEach { removeType(it.first) }
        }
    }

    fun <T> runWithExpected(type: Type?, action: () -> T): T {
        expectedTypes.add(type)

        try {
            return action()
        } finally {
            expectedTypes.removeLast()
        }
    }

    fun <T> runWithGenerics(typeVars: List<UniversalTypeVariable>, action: () -> T): T {
        generics.add(typeVars)
        try {
            return action()
        } finally {
            generics.removeLast()
        }
    }

    fun getGeneric(name: String): UniversalTypeVariable? {
        return generics.asReversed()
            .firstOrNull { it.any { v -> v.name == name } }
            ?.first { it.name == name }
    }
}