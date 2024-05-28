object StellaExtensions {
    val STRUCTURAL_SUBTYPRING = ext("structural-subtyping")
    val AMBIGUOUS_TYPE_AS_BOTTOM = ext("ambiguous-type-as-bottom")

    private fun ext(name: String): String = "#$name"
}