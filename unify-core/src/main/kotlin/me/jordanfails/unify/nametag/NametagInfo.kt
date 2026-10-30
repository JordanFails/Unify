package me.jordanfails.unify.nametag

class NametagInfo constructor(
    var name: String,
    var prefix: String,
    var suffix: String,
    val sortWeight: Int = 0,
) {

    val teamAddPacket = ScoreboardTeamPacketMod(name, prefix, suffix, ArrayList(), 0)

    override fun equals(other: Any?): Boolean {
        return other is NametagInfo && (this.name == other.name
                && this.prefix == other.prefix
                && this.suffix == other.suffix
                && this.sortWeight == other.sortWeight)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + prefix.hashCode()
        result = 31 * result + suffix.hashCode()
        result = 31 * result + sortWeight
        return result
    }

}