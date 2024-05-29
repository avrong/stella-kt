package me.avrong.stella.type

abstract class Type {
    abstract val name : String
    override fun toString(): String = name

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is Type) return false

        return name == other.name
    }

    fun isApplicable(other: Type, structuralSubtyping: Boolean): Boolean {
        // Universal types
        if (this is UniversalTypeVariable && other is UniversalTypeVariable)
            return this.name == other.name
        if (this is UniversalType && other is UniversalType) {
            if (this.variables.size != other.variables.size) {
                return false
            }
            return this.substitute(this.variables
                .mapIndexed { index, typeVar -> Pair(typeVar, other.variables[index]) }.toMap())
                .isApplicable(other.nestedType, structuralSubtyping)
        }


        if (this == other) return true
        if (!structuralSubtyping) return false

        // Structural subtyping implementation
        fun Type.isApplicableStruct(other: Type): Boolean = isApplicable(other, true)

        // Top & Bot
        if (this == BotType) return true
        if (other == TopType) return true

        if (this is FuncType && other is FuncType) {
            if (!returnType.isApplicable(other.returnType, structuralSubtyping)) return false
            if (argTypes.size != other.argTypes.size) return false

            return argTypes.withIndex().all { (index, value) -> other.argTypes[index].isApplicableStruct(value) }
        }

        if (this is RecordType && other is RecordType) {
            val thisFields = fields.toMap()
            val expectedFields = other.fields.toMap()

            if ((expectedFields - thisFields.keys).isNotEmpty()) {
                return false
            }

            return (thisFields.filter { expectedFields.containsKey(it.key) })
                .all { (name, type) -> type.isApplicableStruct(expectedFields[name]!!) }
        }

        if (this is TupleType && other is TupleType) {
            if (types.size != other.types.size) return false

            return types.withIndex().all { (index, type) -> type.isApplicableStruct(other.types[index]) }
        }

        if (this is SumType && other is SumType)
            return left.isApplicableStruct(other.left) && right.isApplicableStruct(other.right)

        if (this is VariantType && other is VariantType) {
            val thisVariants = variants.toMap()
            val expectedVariants = other.variants.toMap()

            if ((thisVariants - expectedVariants.keys).isNotEmpty() ||
                !thisVariants.keys.all { expectedVariants.containsKey(it) }) {
                return false
            }

            return thisVariants.all { (name, type) -> type?.isApplicableStruct(expectedVariants[name]!!) ?: true }
        }

        if (this is ListType && other is ListType)
            return contentType.isApplicableStruct(other.contentType)

        if (this is RefType && other is RefType)
            return innerType.isApplicableStruct(other.innerType) && other.innerType.isApplicableStruct(innerType)

        return false
    }

    fun Type.substitute(typesMapping: Map<UniversalTypeVariable, Type>): Type {
        return when (this) {
            is UniversalType -> {
                val substituted = nestedType.substitute(typesMapping)
                if (!typesMapping.keys.all { this.variables.contains(it) }) {
                    UniversalType(this.variables, substituted)
                } else {
                    substituted
                }
            }

            is FuncType -> FuncType(argTypes.map { it.substitute(typesMapping) }, returnType.substitute(typesMapping))
            is ListType -> ListType(contentType.substitute(typesMapping))
            is RecordType -> RecordType(fields.map { Pair(it.first, it.second.substitute(typesMapping)) })
            is SumType -> SumType(right.substitute(typesMapping), left.substitute(typesMapping))
            is TupleType -> TupleType(types.map { it.substitute(typesMapping) })
            is VariantType -> VariantType(variants.map { Pair(it.first, it.second?.substitute(typesMapping)) })

            is UniversalTypeVariable -> typesMapping.getOrDefault(this, this)

            else -> this
        }
    }
}