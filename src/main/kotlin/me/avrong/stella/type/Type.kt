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
}