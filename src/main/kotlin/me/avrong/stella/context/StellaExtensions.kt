package me.avrong.stella.context

object StellaExtensions {
    val STRUCTURAL_SUBTYPRING = ext("structural-subtyping")
    val AMBIGUOUS_TYPE_AS_BOTTOM = ext("ambiguous-type-as-bottom")
    val TYPE_RECONSTRUCTION = ext("type-reconstruction")

    private fun ext(name: String): String = "#$name"
}