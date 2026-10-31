package completion

enum class SampleEnum {
    FIRST, SECOND, THIRD
}

fun checkEnum() {
    SampleEnum.<caret>
}
