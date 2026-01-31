package me.jordanfails.unify.menu.buttons

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.get
import kotlin.math.abs

/**
 * Animated button that cycles through different states
 */
abstract class AnimatedButton : Button() {

    private var currentFrame = 0

    override fun isAnimated(): Boolean = true

    override fun getAnimationInterval(): Long = 10L

    /**
     * Override this to define animation frames
     * Return list of materials, names, or descriptions that cycle
     */
    abstract fun getFrameCount(): Int

    override fun preAnimationUpdate() {
        currentFrame = (currentFrame + 1) % getFrameCount()
    }

    protected fun getCurrentFrame(): Int = currentFrame
}

/**
 * Simple material cycling button
 */
class MaterialCycleButton(
    private val materials: List<Material>,
    private val name: String = " ",
    private val lore: List<String> = emptyList(),
    private val interval: Long = 10L
) : AnimatedButton() {

    override fun getFrameCount(): Int = materials.size

    override fun getMaterial(player: Player): Material {
        return materials[getCurrentFrame()]
    }

    override fun getName(player: Player): String = name

    override fun getDescription(player: Player): MutableList<String> {
        return lore.toMutableList()
    }

    override fun getAnimationInterval(): Long = interval
}

/**
 * Color pulsing button (cycles through similar colored glass panes)
 */
class PulsingButton(
    private val name: String,
    private val colors: List<Material> = listOf(
        Material.RED_STAINED_GLASS_PANE,
        Material.ORANGE_STAINED_GLASS_PANE,
        Material.YELLOW_STAINED_GLASS_PANE,
        Material.ORANGE_STAINED_GLASS_PANE
    ),
    private val interval: Long = 8L
) : AnimatedButton() {

    override fun getFrameCount(): Int = colors.size

    override fun getMaterial(player: Player): Material {
        return colors[getCurrentFrame()]
    }

    override fun getName(player: Player): String = name

    override fun getAnimationInterval(): Long = interval
}

/**
 * Loading/Progress button with changing names
 */
class LoadingButton(
    private val baseName: String,
    private val dots: Boolean = true,
    private val interval: Long = 10L
) : AnimatedButton() {

    private val frames = if (dots) {
        listOf("", ".", "..", "...")
    } else {
        listOf("", "▁", "▂", "▃", "▄", "▅", "▆", "▇", "█", "▇", "▆", "▅", "▄", "▃", "▂", "▁")
    }

    override fun getFrameCount(): Int = frames.size

    override fun getMaterial(player: Player): Material = Material.HOPPER

    override fun getName(player: Player): String {
        return "$baseName${frames[getCurrentFrame()]}"
    }

    override fun getAnimationInterval(): Long = interval
}

/**
 * Animation manager for complex menu animations
 */
class MenuAnimationManager(private val menu: Menu) {

    private val activeAnimations = ConcurrentHashMap<String, BukkitTask>()

    /**
     * Animate border slots with a moving effect
     */
    fun animateBorder(
        player: Player,
        rows: Int,
        button: Material,
        speed: Long = 2L,
        direction: BorderDirection = BorderDirection.CLOCKWISE
    ) {
        val borderSlots = getBorderSlots(rows, direction)
        val animationId = "border_${player.uniqueId}"

        stopAnimation(animationId)

        var currentIndex = 0
        val task = Bukkit.getScheduler().runTaskTimer(
            UnifyCore.instance,
            Runnable {
                if (Menu.currentlyOpenedMenus[player.uniqueId] != menu) {
                    stopAnimation(animationId)
                    return@Runnable
                }

                val inv = player.openInventory.topInventory
                val slot = borderSlots[currentIndex]

                val borderButton = Button.placeholder(button, " ")
                menu.buttons[slot] = borderButton
                inv.setItem(slot, borderButton.getButtonItem(player))

                // Clear previous slot
                val prevIndex = if (currentIndex == 0) borderSlots.size - 1 else currentIndex - 1
                val prevSlot = borderSlots[prevIndex]
                val clearButton = Button.placeholder(Material.BLACK_STAINED_GLASS_PANE, " ")
                menu.buttons[prevSlot] = clearButton
                inv.setItem(prevSlot, clearButton.getButtonItem(player))

                player.updateInventory()
                currentIndex = (currentIndex + 1) % borderSlots.size
            },
            0L,
            speed
        )

        activeAnimations[animationId] = task
    }

    /**
     * Wave effect across specified slots
     */
    fun waveAnimation(
        player: Player,
        startSlot: Int,
        endSlot: Int,
        material: Material,
        speed: Long = 3L,
        repeat: Boolean = true
    ) {
        val animationId = "wave_${player.uniqueId}_${startSlot}_${endSlot}"
        stopAnimation(animationId)

        val slots = (startSlot..endSlot).toList()
        var currentIndex = 0

        val task = Bukkit.getScheduler().runTaskTimer(
            UnifyCore.instance,
            Runnable {
                if (Menu.currentlyOpenedMenus[player.uniqueId] != menu) {
                    stopAnimation(animationId)
                    return@Runnable
                }

                val inv = player.openInventory.topInventory

                // Light up current slot
                val slot = slots[currentIndex]
                val waveButton = Button.placeholder(material, " ")
                menu.buttons[slot] = waveButton
                inv.setItem(slot, waveButton.getButtonItem(player))

                // Dim previous slots with trail effect
                if (currentIndex > 0) {
                    val prevSlot = slots[currentIndex - 1]
                    val dimButton = Button.placeholder(Material.GRAY_STAINED_GLASS_PANE, " ")
                    menu.buttons[prevSlot] = dimButton
                    inv.setItem(prevSlot, dimButton.getButtonItem(player))
                }

                player.updateInventory()
                currentIndex++

                if (currentIndex >= slots.size) {
                    if (repeat) {
                        currentIndex = 0
                        // Clear all slots before restarting
                        slots.forEach { s ->
                            val clearButton = Button.placeholder(Material.BLACK_STAINED_GLASS_PANE, " ")
                            menu.buttons[s] = clearButton
                            inv.setItem(s, clearButton.getButtonItem(player))
                        }
                    } else {
                        stopAnimation(animationId)
                    }
                }
            },
            0L,
            speed
        )

        activeAnimations[animationId] = task
    }

    /**
     * Fill animation - progressively fills slots
     */
    fun fillAnimation(
        player: Player,
        slots: List<Int>,
        material: Material,
        speed: Long = 1L,
        onComplete: (() -> Unit)? = null
    ) {
        val animationId = "fill_${player.uniqueId}_${UUID.randomUUID()}"
        stopAnimation(animationId)

        var currentIndex = 0

        val task = Bukkit.getScheduler().runTaskTimer(
            UnifyCore.instance,
            Runnable {
                if (Menu.currentlyOpenedMenus[player.uniqueId] != menu) {
                    stopAnimation(animationId)
                    return@Runnable
                }

                val inv = player.openInventory.topInventory
                val slot = slots[currentIndex]

                val fillButton = Button.placeholder(material, " ")
                menu.buttons[slot] = fillButton
                inv.setItem(slot, fillButton.getButtonItem(player))
                player.updateInventory()

                currentIndex++

                if (currentIndex >= slots.size) {
                    stopAnimation(animationId)
                    onComplete?.invoke()
                }
            },
            0L,
            speed
        )

        activeAnimations[animationId] = task
    }

    /**
     * Spiral animation from center outward
     */
    fun spiralAnimation(
        player: Player,
        centerSlot: Int,
        material: Material,
        maxRadius: Int = 2,
        speed: Long = 2L
    ) {
        val animationId = "spiral_${player.uniqueId}"
        stopAnimation(animationId)

        val spiralSlots = getSpiralSlots(centerSlot, maxRadius)
        var currentIndex = 0

        val task = Bukkit.getScheduler().runTaskTimer(
            UnifyCore.instance,
            Runnable {
                if (Menu.currentlyOpenedMenus[player.uniqueId] != menu || currentIndex >= spiralSlots.size) {
                    stopAnimation(animationId)
                    return@Runnable
                }

                val inv = player.openInventory.topInventory
                val slot = spiralSlots[currentIndex]

                if (slot >= 0 && slot < inv.size) {
                    val spiralButton = Button.placeholder(material, " ")
                    menu.buttons[slot] = spiralButton
                    inv.setItem(slot, spiralButton.getButtonItem(player))
                    player.updateInventory()
                }

                currentIndex++
            },
            0L,
            speed
        )

        activeAnimations[animationId] = task
    }

    /**
     * Stop specific animation
     */
    fun stopAnimation(animationId: String) {
        activeAnimations[animationId]?.cancel()
        activeAnimations.remove(animationId)
    }

    /**
     * Stop all animations
     */
    fun stopAllAnimations() {
        activeAnimations.values.forEach { it.cancel() }
        activeAnimations.clear()
    }

    private fun getBorderSlots(rows: Int, direction: BorderDirection): List<Int> {
        val slots = mutableListOf<Int>()
        val cols = 9

        when (direction) {
            BorderDirection.CLOCKWISE -> {
                // Top row (left to right)
                for (i in 0 until cols) slots.add(i)
                // Right side (top to bottom)
                for (i in 1 until rows - 1) slots.add(i * cols + (cols - 1))
                // Bottom row (right to left)
                for (i in cols - 1 downTo 0) slots.add((rows - 1) * cols + i)
                // Left side (bottom to top)
                for (i in rows - 2 downTo 1) slots.add(i * cols)
            }
            BorderDirection.COUNTERCLOCKWISE -> {
                // Top row (right to left)
                for (i in cols - 1 downTo 0) slots.add(i)
                // Left side (top to bottom)
                for (i in 1 until rows - 1) slots.add(i * cols)
                // Bottom row (left to right)
                for (i in 0 until cols) slots.add((rows - 1) * cols + i)
                // Right side (bottom to top)
                for (i in rows - 2 downTo 1) slots.add(i * cols + (cols - 1))
            }
        }

        return slots
    }

    private fun getSpiralSlots(center: Int, maxRadius: Int): List<Int> {
        val slots = mutableListOf(center)
        val centerRow = center / 9
        val centerCol = center % 9

        for (radius in 1..maxRadius) {
            for (i in -radius..radius) {
                for (j in -radius..radius) {
                    if (abs(i) == radius || abs(j) == radius) {
                        val row = centerRow + i
                        val col = centerCol + j
                        if (row >= 0 && col >= 0 && col < 9) {
                            slots.add(row * 9 + col)
                        }
                    }
                }
            }
        }

        return slots.distinct()
    }

    enum class BorderDirection {
        CLOCKWISE,
        COUNTERCLOCKWISE
    }
}

/**
 * Add to Menu class:
 *
 * val animationManager: MenuAnimationManager by lazy { MenuAnimationManager(this) }
 *
 * And in onClose():
 * animationManager.stopAllAnimations()
 */

/* EXAMPLE USAGE:

class AnimatedMenu : Menu("&b&lAnimated Menu") {

    private val animationManager = MenuAnimationManager(this)

    init {
        animated = true
        autoUpdate = true
        autoUpdateInterval = 10L
    }

    override fun getButtons(player: Player): Map<Int, Button> {
        val buttons = hashMapOf<Int, Button>()

        // Static button
        buttons[22] = object : Button() {
            override fun getName(player: Player) = "&a&lClick Me!"
            override fun getMaterial(player: Player) = Material.DIAMOND
            override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
                playSuccess(player)
            }
        }

        // Animated pulsing button
        buttons[13] = PulsingButton("&c&lLive")

        // Loading button
        buttons[4] = LoadingButton("&e&lLoading")

        // Material cycle button
        buttons[40] = MaterialCycleButton(
            materials = listOf(
                Material.RED_WOOL,
                Material.ORANGE_WOOL,
                Material.YELLOW_WOOL,
                Material.LIME_WOOL,
                Material.CYAN_WOOL,
                Material.BLUE_WOOL,
                Material.PURPLE_WOOL,
                Material.MAGENTA_WOOL
            ),
            name = "&d&lRainbow",
            interval = 5L
        )

        return buttons
    }

    override fun onOpen(player: Player) {
        // Start border animation
        animationManager.animateBorder(player, 6, Material.CYAN_STAINED_GLASS_PANE, 2L)

        // Start wave animation on bottom row
        animationManager.waveAnimation(player, 45, 53, Material.LIGHT_BLUE_STAINED_GLASS_PANE, 3L)
    }

    override fun onClose(player: Player, manualClose: Boolean) {
        animationManager.stopAllAnimations()
    }
}

*/