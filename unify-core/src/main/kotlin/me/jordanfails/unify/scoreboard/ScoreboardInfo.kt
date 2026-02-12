package me.jordanfails.unify.scoreboard

data class ScoreboardInfo(
    val title: String,
    val lines: List<String>
) {
    override fun equals(other: Any?): Boolean {
        if (other is ScoreboardInfo) {
            return this.title == other.title && this.lines == other.lines
        }
        return false
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + lines.hashCode()
        return result
    }
}
