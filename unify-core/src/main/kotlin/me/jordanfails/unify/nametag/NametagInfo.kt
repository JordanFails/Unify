package me.jordanfails.unify.nametag

class NametagInfo constructor(var name: String, var prefix: String, var suffix: String) {

    val teamAddPacket = ScoreboardTeamPacketMod(name, prefix, suffix, ArrayList(), 0)

    override fun equals(other: Any?): Boolean {
        if (other is NametagInfo) {
            val otherNametag = other as NametagInfo?
            return this.name == otherNametag!!.name && this.prefix == otherNametag.prefix && this.suffix == otherNametag.suffix
        }
        return false
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + prefix.hashCode()
        result = 31 * result + suffix.hashCode()
        return result
    }

}