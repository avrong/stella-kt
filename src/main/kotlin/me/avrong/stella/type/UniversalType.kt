package me.avrong.stella.type

data class UniversalType(
    val variables: List<UniversalTypeVariable>,
    val nestedType: Type
) : Type() {
    override val name: String
        get() = "forall ${variables.map { it.name }.joinToString(", ")} + ${nestedType.name}"
}