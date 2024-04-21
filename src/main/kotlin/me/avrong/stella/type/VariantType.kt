package me.avrong.stella.type

class VariantType(val variants: List<Pair<String, Type?>>) : Type() {
    private val variantsStr: String = variants.joinToString(", ") {
        "${it.first} = ${it.second}"
    }

    override val name: String = """<| $variantsStr |>"""
}