package me.jordanfails.unify.nametag.update

import java.util.UUID

/** Refresh [target]'s plate for one [viewer], or for every viewer when it's null. */
internal data class NametagUpdate(val target: UUID, val viewer: UUID?)
