package me.jordanfails.unify.nametag

class NametagInfo constructor(
    var name: String,
    var prefix: String,
    var suffix: String,
    val sortWeight: Int = 0,
) {

    /** Extra lines above the normal name, ordered from top to bottom. */
    var lines: List<String> = emptyList()
        set(value) {
            field = value.toList()
        }

    override fun equals(other: Any?): Boolean {
        return other is NametagInfo && (this.name == other.name
                && this.prefix == other.prefix
                && this.suffix == other.suffix
                && this.sortWeight == other.sortWeight
                && this.lines == other.lines)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + prefix.hashCode()
        result = 31 * result + suffix.hashCode()
        result = 31 * result + sortWeight
        result = 31 * result + lines.hashCode()
        return result
    }

}
