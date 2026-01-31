package me.jordanfails.unify.utils

import me.jordanfails.unify.UnifyCore
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.entity.Entity

class Cuboid private constructor(
    val worldName: String,
    val x1: Int,
    val y1: Int,
    val z1: Int,
    val x2: Int,
    val y2: Int,
    val z2: Int
) : Iterable<Block>, Cloneable, ConfigurationSerializable {

    /** Construct a Cuboid from 2 corner locations (must be same world) */
    constructor(l1: Location, l2: Location) : this(
        checkSameWorld(l1, l2).name,
        minOf(l1.blockX, l2.blockX),
        minOf(l1.blockY, l2.blockY),
        minOf(l1.blockZ, l2.blockZ),
        maxOf(l1.blockX, l2.blockX),
        maxOf(l1.blockY, l2.blockY),
        maxOf(l1.blockZ, l2.blockZ)
    )

    /** One-block Cuboid at location */
    constructor(loc: Location) : this(loc, loc)

    /** Copy constructor */
    constructor(other: Cuboid) : this(
        other.worldName,
        other.x1, other.y1, other.z1,
        other.x2, other.y2, other.z2
    )

    /** Construct directly from World coordinates */
    constructor(world: World, x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) : this(
        world.name,
        minOf(x1, x2), minOf(y1, y2), minOf(z1, z2),
        maxOf(x1, x2), maxOf(y1, y2), maxOf(z1, z2)
    )

    /** Construct from serialized map */
    constructor(map: Map<String, Any>) : this(
        map["worldName"] as String,
        map["x1"] as Int, map["y1"] as Int, map["z1"] as Int,
        map["x2"] as Int, map["y2"] as Int, map["z2"] as Int
    )

    override fun serialize(): MutableMap<String, Any> = hashMapOf(
        "worldName" to worldName,
        "x1" to x1, "y1" to y1, "z1" to z1,
        "x2" to x2, "y2" to y2, "z2" to z2
    )

    fun getWorld(): World =
        UnifyCore.instance.server.getWorld(worldName)
            ?: throw IllegalStateException("World '$worldName' is not loaded")

    fun getLowerNE(): Location = Location(getWorld(), x1.toDouble(), y1.toDouble(), z1.toDouble())
    fun getUpperSW(): Location = Location(getWorld(), x2.toDouble(), y2.toDouble(), z2.toDouble())

    fun getBlocks(): List<Block> = this.toList()

    fun getCenter(): Location {
        val x = x2 + 1
        val y = y2 + 1
        val z = z2 + 1
        return Location(
            getWorld(),
            x1 + (x - x1) / 2.0,
            y1 + (y - y1) / 2.0,
            z1 + (z - z1) / 2.0
        )
    }

    fun getSizeX(): Int = (x2 - x1) + 1
    fun getSizeY(): Int = (y2 - y1) + 1
    fun getSizeZ(): Int = (z2 - z1) + 1
    fun getVolume(): Int = getSizeX() * getSizeY() * getSizeZ()

    fun getLowerX(): Int = x1
    fun getLowerY(): Int = y1
    fun getLowerZ(): Int = z1
    fun getUpperX(): Int = x2
    fun getUpperY(): Int = y2
    fun getUpperZ(): Int = z2

    fun corners(): Array<Block> {
        val w = getWorld()
        return arrayOf(
            w.getBlockAt(x1, y1, z1),
            w.getBlockAt(x1, y1, z2),
            w.getBlockAt(x1, y2, z1),
            w.getBlockAt(x1, y2, z2),
            w.getBlockAt(x2, y1, z1),
            w.getBlockAt(x2, y1, z2),
            w.getBlockAt(x2, y2, z1),
            w.getBlockAt(x2, y2, z2)
        )
    }

    fun getChunks(): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        val w = getWorld()

        val x1 = this.x1 and -0x10
        val x2 = this.x2 and -0x10
        val z1 = this.z1 and -0x10
        val z2 = this.z2 and -0x10

        for (x in x1..x2 step 16) {
            for (z in z1..z2 step 16) {
                chunks.add(w.getChunkAt(x shr 4, z shr 4))
            }
        }
        return chunks
    }

    fun loadChunks() = getChunks().forEach { it.load() }
    fun unloadChunks() = getChunks().forEach { it.unload() }

    fun expand(dir: CuboidDirection, amount: Int): Cuboid = when (dir) {
        CuboidDirection.NORTH -> Cuboid(worldName, x1 - amount, y1, z1, x2, y2, z2)
        CuboidDirection.SOUTH -> Cuboid(worldName, x1, y1, z1, x2 + amount, y2, z2)
        CuboidDirection.EAST ->  Cuboid(worldName, x1, y1, z1 - amount, x2, y2, z2)
        CuboidDirection.WEST ->  Cuboid(worldName, x1, y1, z1, x2, y2, z2 + amount)
        CuboidDirection.DOWN ->  Cuboid(worldName, x1, y1 - amount, z1, x2, y2, z2)
        CuboidDirection.UP   ->  Cuboid(worldName, x1, y1, z1, x2, y2 + amount, z2)
        else -> throw IllegalArgumentException("Invalid direction $dir")
    }

    fun shift(dir: CuboidDirection, amount: Int): Cuboid =
        expand(dir, amount).expand(dir.opposite(), -amount)

    fun outset(dir: CuboidDirection, amount: Int): Cuboid = when (dir) {
        CuboidDirection.HORIZONTAL ->
            expand(CuboidDirection.NORTH, amount)
                .expand(CuboidDirection.SOUTH, amount)
                .expand(CuboidDirection.EAST, amount)
                .expand(CuboidDirection.WEST, amount)

        CuboidDirection.VERTICAL ->
            expand(CuboidDirection.DOWN, amount)
                .expand(CuboidDirection.UP, amount)

        CuboidDirection.BOTH ->
            outset(CuboidDirection.HORIZONTAL, amount)
                .outset(CuboidDirection.VERTICAL, amount)

        else -> throw IllegalArgumentException("Invalid direction $dir")
    }

    fun inset(dir: CuboidDirection, amount: Int): Cuboid = outset(dir, -amount)

    fun contains(x: Int, y: Int, z: Int) =
        x in x1..x2 && y in y1..y2 && z in z1..z2

    fun contains(b: Block) = contains(b.location)

    fun contains(l: Location): Boolean =
        l.world?.name == worldName && contains(l.blockX, l.blockY, l.blockZ)

    fun contains(entity: Entity): Boolean {
        val loc = entity.location
        return loc.world?.name == worldName &&
                contains(loc.blockX, loc.blockY, loc.blockZ)
    }

    fun getAverageLightLevel(): Byte {
        var total = 0L
        var n = 0
        for (b in this) {
            if (b.isEmpty) {
                total += b.lightLevel.toLong()
                n++
            }
        }
        return if (n > 0) (total / n).toByte() else 0
    }

    fun contract(): Cuboid =
        contract(CuboidDirection.DOWN)
            .contract(CuboidDirection.SOUTH)
            .contract(CuboidDirection.EAST)
            .contract(CuboidDirection.UP)
            .contract(CuboidDirection.NORTH)
            .contract(CuboidDirection.WEST)

    fun contract(dir: CuboidDirection): Cuboid {
        var face = getFace(dir.opposite())
        return when (dir) {
            CuboidDirection.DOWN -> {
                while (face.containsOnly(Material.AIR.id) && face.getLowerY() > y1) {
                    face = face.shift(CuboidDirection.DOWN, 1)
                }
                Cuboid(worldName, x1, y1, z1, x2, face.getUpperY(), z2)
            }
            CuboidDirection.UP -> {
                while (face.containsOnly(Material.AIR.id) && face.getUpperY() < y2) {
                    face = face.shift(CuboidDirection.UP, 1)
                }
                Cuboid(worldName, x1, face.getLowerY(), z1, x2, y2, z2)
            }
            CuboidDirection.NORTH -> {
                while (face.containsOnly(Material.AIR.id) && face.getLowerX() > x1) {
                    face = face.shift(CuboidDirection.NORTH, 1)
                }
                Cuboid(worldName, x1, y1, z1, face.getUpperX(), y2, z2)
            }
            CuboidDirection.SOUTH -> {
                while (face.containsOnly(Material.AIR.id) && face.getUpperX() < x2) {
                    face = face.shift(CuboidDirection.SOUTH, 1)
                }
                Cuboid(worldName, face.getLowerX(), y1, z1, x2, y2, z2)
            }
            CuboidDirection.EAST -> {
                while (face.containsOnly(Material.AIR.id) && face.getLowerZ() > z1) {
                    face = face.shift(CuboidDirection.EAST, 1)
                }
                Cuboid(worldName, x1, y1, z1, x2, y2, face.getUpperZ())
            }
            CuboidDirection.WEST -> {
                while (face.containsOnly(Material.AIR.id) && face.getUpperZ() < z2) {
                    face = face.shift(CuboidDirection.WEST, 1)
                }
                Cuboid(worldName, x1, y1, face.getLowerZ(), x2, y2, z2)
            }
            else -> throw IllegalArgumentException("Invalid direction $dir")
        }
    }

    fun getFace(dir: CuboidDirection): Cuboid = when (dir) {
        CuboidDirection.DOWN ->
            Cuboid(worldName, x1, y1, z1, x2, y1, z2)
        CuboidDirection.UP ->
            Cuboid(worldName, x1, y2, z1, x2, y2, z2)
        CuboidDirection.NORTH ->
            Cuboid(worldName, x1, y1, z1, x1, y2, z2)
        CuboidDirection.SOUTH ->
            Cuboid(worldName, x2, y1, z1, x2, y2, z2)
        CuboidDirection.EAST ->
            Cuboid(worldName, x1, y1, z1, x2, y2, z1)
        CuboidDirection.WEST ->
            Cuboid(worldName, x1, y1, z2, x2, y2, z2)
        else -> throw IllegalArgumentException("Invalid direction $dir")
    }

    fun containsOnly(blockId: Int): Boolean {
        for (b in this) {
            if (b.type.id != blockId) return false
        }
        return true
    }

    fun getBoundingCuboid(other: Cuboid?): Cuboid {
        if (other == null) return this
        val xMin = minOf(x1, other.x1)
        val yMin = minOf(y1, other.y1)
        val zMin = minOf(z1, other.z1)
        val xMax = maxOf(x2, other.x2)
        val yMax = maxOf(y2, other.y2)
        val zMax = maxOf(z2, other.z2)
        return Cuboid(worldName, xMin, yMin, zMin, xMax, yMax, zMax)
    }

    fun getRelativeBlock(x: Int, y: Int, z: Int): Block =
        getWorld().getBlockAt(x1 + x, y1 + y, z1 + z)

    fun getRelativeBlock(w: World, x: Int, y: Int, z: Int): Block =
        w.getBlockAt(x1 + x, y1 + y, z1 + z)

    fun getWalls(): List<Block> {
        val blocks = mutableListOf<Block>()
        val world = getWorld()

        for (x in x1..x2) {
            for (y in y1..y2) {
                blocks.add(world.getBlockAt(x, y, z1))
                blocks.add(world.getBlockAt(x, y, z2))
            }
        }
        for (y in y1..y2) {
            for (z in z1..z2) {
                blocks.add(world.getBlockAt(x1, y, z))
                blocks.add(world.getBlockAt(x2, y, z))
            }
        }
        return blocks
    }

    fun getFaces(): List<Block> {
        val blocks = mutableListOf<Block>()
        val world = getWorld()
        for (x in x1..x2) {
            for (y in y1..y2) {
                blocks.add(world.getBlockAt(x, y, z1))
                blocks.add(world.getBlockAt(x, y, z2))
            }
        }
        for (y in y1..y2) {
            for (z in z1..z2) {
                blocks.add(world.getBlockAt(x1, y, z))
                blocks.add(world.getBlockAt(x2, y, z))
            }
        }
        for (z in z1..z2) {
            for (x in x1..x2) {
                blocks.add(world.getBlockAt(x, y1, z))
                blocks.add(world.getBlockAt(x, y2, z))
            }
        }
        return blocks
    }

    override fun iterator(): Iterator<Block> =
        CuboidIterator(getWorld(), x1, y1, z1, x2, y2, z2)

    override fun clone(): Cuboid = Cuboid(this)

    override fun toString(): String =
        "Cuboid: $worldName,$x1,$y1,$z1=>$x2,$y2,$z2"

    enum class CuboidDirection {
        NORTH, EAST, SOUTH, WEST, UP, DOWN,
        HORIZONTAL, VERTICAL, BOTH, UNKNOWN;

        fun opposite(): CuboidDirection = when (this) {
            NORTH -> SOUTH
            EAST -> WEST
            SOUTH -> NORTH
            WEST -> EAST
            HORIZONTAL -> VERTICAL
            VERTICAL -> HORIZONTAL
            UP -> DOWN
            DOWN -> UP
            BOTH -> BOTH
            else -> UNKNOWN
        }
    }

    inner class CuboidIterator(
        private val world: World,
        x1: Int, y1: Int, z1: Int,
        x2: Int, y2: Int, z2: Int
    ) : Iterator<Block> {
        private val baseX = minOf(x1, x2)
        private val baseY = minOf(y1, y2)
        private val baseZ = minOf(z1, z2)
        private val sizeX = Math.abs(x2 - x1) + 1
        private val sizeY = Math.abs(y2 - y1) + 1
        private val sizeZ = Math.abs(z2 - z1) + 1
        private var x = 0
        private var y = 0
        private var z = 0

        override fun hasNext(): Boolean =
            x < sizeX && y < sizeY && z < sizeZ

        override fun next(): Block {
            val b = world.getBlockAt(baseX + x, baseY + y, baseZ + z)
            if (++x >= sizeX) {
                x = 0
                if (++y >= sizeY) {
                    y = 0
                    z++
                }
            }
            return b
        }
    }

    companion object {
        private fun checkSameWorld(l1: Location, l2: Location): World {
            require(l1.world == l2.world) { "Locations must be on the same world" }
            return l1.world!!
        }
    }
}