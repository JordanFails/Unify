package me.jordanfails.unify.screen.dsl

import me.jordanfails.unify.screen.Screen
import me.jordanfails.unify.screen.ScreenAction
import me.jordanfails.unify.screen.ScreenAfterAction
import me.jordanfails.unify.screen.ScreenBody
import me.jordanfails.unify.screen.ScreenButton
import me.jordanfails.unify.screen.ScreenClick
import me.jordanfails.unify.screen.ScreenInput
import me.jordanfails.unify.screen.ScreenKind
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

// ─────────────────────────────────────────────────────────────────────────────
// Multi-action screen (the general "menu" type)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Build a multi-action custom screen.
 *
 * ```kotlin
 * val warps = screen("Warps") {
 *     body("Choose a destination")
 *     columns(2)
 *     button("&aSpawn") {
 *         tooltip("Teleport to spawn")
 *         onClick { it.player.teleport(spawn) }
 *     }
 *     button("&eShop") { onClick { it.open(shopScreen) } }
 *     exit("&7Close")
 * }
 * warps.open(player)
 * ```
 */
fun screen(title: String, block: ScreenBuilder.() -> Unit): Screen =
    ScreenBuilder(title).apply(block).build()

@ScreenDslMarker
class ScreenBuilder(private var title: String) {
    private var externalTitle: String? = null
    private val bodies = mutableListOf<ScreenBody>()
    private val inputs = mutableListOf<ScreenInput>()
    private val buttons = mutableListOf<ScreenButton>()
    private var exit: ScreenButton? = null
    private var columns: Int = 2
    private var canEscape: Boolean = true
    private var pauseGame: Boolean = false
    private var after: ScreenAfterAction = ScreenAfterAction.CLOSE

    fun title(title: String) { this.title = title }
    fun externalTitle(title: String) { this.externalTitle = title }
    fun columns(columns: Int) {
        require(columns >= 1) { "columns must be at least 1" }
        this.columns = columns
    }
    fun canCloseWithEscape(enabled: Boolean = true) { canEscape = enabled }
    fun cannotEscape() { canEscape = false }
    fun pause(enabled: Boolean = true) { pauseGame = enabled }
    fun afterAction(action: ScreenAfterAction) { after = action }
    fun keepOpen() { after = ScreenAfterAction.KEEP_OPEN }
    fun waitForResponse() { after = ScreenAfterAction.WAIT }

    fun body(text: String, width: Int = ScreenBody.DEFAULT_WIDTH) {
        bodies += ScreenBody.Message(text, width)
    }

    fun item(
        stack: ItemStack,
        description: String? = null,
        showDecorations: Boolean = true,
        showTooltip: Boolean = true,
        width: Int = 16,
        height: Int = 16,
    ) {
        bodies += ScreenBody.Item(
            item = stack,
            description = description,
            showDecorations = showDecorations,
            showTooltip = showTooltip,
            width = width,
            height = height,
        )
    }

    fun button(label: String, block: ScreenButtonBuilder.() -> Unit = {}) {
        buttons += ScreenButtonBuilder(label).apply(block).build()
    }

    fun exit(label: String, block: ScreenButtonBuilder.() -> Unit = {}) {
        exit = ScreenButtonBuilder(label).apply(block).build()
    }

    fun text(key: String, label: String, block: TextInputBuilder.() -> Unit = {}) {
        inputs += TextInputBuilder(key, label).apply(block).build()
    }

    fun bool(key: String, label: String, block: BoolInputBuilder.() -> Unit = {}) {
        inputs += BoolInputBuilder(key, label).apply(block).build()
    }

    fun number(
        key: String,
        label: String,
        start: Float,
        end: Float,
        block: NumberInputBuilder.() -> Unit = {},
    ) {
        inputs += NumberInputBuilder(key, label, start, end).apply(block).build()
    }

    fun option(key: String, label: String, block: OptionInputBuilder.() -> Unit) {
        inputs += OptionInputBuilder(key, label).apply(block).build()
    }

    fun build(): Screen {
        val self = this
        return object : Screen() {
            override fun getTitle(player: Player): String = self.title
            override fun getExternalTitle(player: Player): String? = self.externalTitle
            override fun getKind(): ScreenKind = ScreenKind.MULTI
            override fun getBodies(player: Player): List<ScreenBody> = self.bodies
            override fun getInputs(player: Player): List<ScreenInput> = self.inputs
            override fun getButtons(player: Player): List<ScreenButton> = self.buttons
            override fun getExitButton(player: Player): ScreenButton? = self.exit
            override fun getColumns(): Int = self.columns
            override fun canCloseWithEscape(): Boolean = self.canEscape
            override fun pause(): Boolean = self.pauseGame
            override fun getAfterAction(): ScreenAfterAction = self.after
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Notice
// ─────────────────────────────────────────────────────────────────────────────

fun noticeScreen(title: String, block: NoticeScreenBuilder.() -> Unit = {}): Screen =
    NoticeScreenBuilder(title).apply(block).build()

@ScreenDslMarker
class NoticeScreenBuilder(private var title: String) {
    private var externalTitle: String? = null
    private val bodies = mutableListOf<ScreenBody>()
    private var button: ScreenButton? = null
    private var canEscape: Boolean = true
    private var pauseGame: Boolean = false
    private var after: ScreenAfterAction = ScreenAfterAction.CLOSE

    fun title(title: String) { this.title = title }
    fun externalTitle(title: String) { this.externalTitle = title }
    fun canCloseWithEscape(enabled: Boolean = true) { canEscape = enabled }
    fun cannotEscape() { canEscape = false }
    fun pause(enabled: Boolean = true) { pauseGame = enabled }
    fun afterAction(action: ScreenAfterAction) { after = action }

    fun body(text: String, width: Int = ScreenBody.DEFAULT_WIDTH) {
        bodies += ScreenBody.Message(text, width)
    }

    fun item(stack: ItemStack, description: String? = null) {
        bodies += ScreenBody.Item(stack, description)
    }

    fun button(label: String = "OK", block: ScreenButtonBuilder.() -> Unit = {}) {
        button = ScreenButtonBuilder(label).apply(block).build()
    }

    fun build(): Screen {
        val self = this
        return object : Screen() {
            override fun getTitle(player: Player): String = self.title
            override fun getExternalTitle(player: Player): String? = self.externalTitle
            override fun getKind(): ScreenKind = ScreenKind.NOTICE
            override fun getBodies(player: Player): List<ScreenBody> = self.bodies
            override fun getButtons(player: Player): List<ScreenButton> =
                listOfNotNull(self.button)
            override fun canCloseWithEscape(): Boolean = self.canEscape
            override fun pause(): Boolean = self.pauseGame
            override fun getAfterAction(): ScreenAfterAction = self.after
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confirmation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Build a yes/no custom screen.
 *
 * ```kotlin
 * confirmScreen("Delete plot?") {
 *     body("This cannot be undone.")
 *     cannotEscape()
 *     onConfirm { plot.delete() }
 *     onCancel { }
 * }
 * ```
 */
fun confirmScreen(title: String, block: ConfirmScreenBuilder.() -> Unit): Screen =
    ConfirmScreenBuilder(title).apply(block).build()

@ScreenDslMarker
class ConfirmScreenBuilder(private var title: String) {
    private var externalTitle: String? = null
    private val bodies = mutableListOf<ScreenBody>()
    private var yes: ScreenButton = ScreenButton("&aConfirm")
    private var no: ScreenButton = ScreenButton("&cCancel")
    private var canEscape: Boolean = true
    private var pauseGame: Boolean = false
    private var after: ScreenAfterAction = ScreenAfterAction.CLOSE
    private var onConfirmHandler: ((ScreenClick) -> Unit)? = null
    private var onCancelHandler: ((ScreenClick) -> Unit)? = null

    fun title(title: String) { this.title = title }
    fun externalTitle(title: String) { this.externalTitle = title }
    fun canCloseWithEscape(enabled: Boolean = true) { canEscape = enabled }
    fun cannotEscape() { canEscape = false }
    fun pause(enabled: Boolean = true) { pauseGame = enabled }
    fun afterAction(action: ScreenAfterAction) { after = action }

    fun body(text: String, width: Int = ScreenBody.DEFAULT_WIDTH) {
        bodies += ScreenBody.Message(text, width)
    }

    fun item(stack: ItemStack, description: String? = null) {
        bodies += ScreenBody.Item(stack, description)
    }

    fun yes(label: String = "&aConfirm", block: ScreenButtonBuilder.() -> Unit = {}) {
        yes = ScreenButtonBuilder(label).apply(block).build()
    }

    fun no(label: String = "&cCancel", block: ScreenButtonBuilder.() -> Unit = {}) {
        no = ScreenButtonBuilder(label).apply(block).build()
    }

    fun onConfirm(handler: (ScreenClick) -> Unit) {
        onConfirmHandler = handler
    }

    fun onCancel(handler: (ScreenClick) -> Unit) {
        onCancelHandler = handler
    }

    fun build(): Screen {
        val self = this
        val yesButton = if (self.yes.action == null && self.onConfirmHandler != null) {
            self.yes.copy(action = ScreenAction.Click(self.onConfirmHandler!!))
        } else {
            self.yes
        }
        val noButton = if (self.no.action == null && self.onCancelHandler != null) {
            self.no.copy(action = ScreenAction.Click(self.onCancelHandler!!))
        } else {
            self.no
        }
        return object : Screen() {
            override fun getTitle(player: Player): String = self.title
            override fun getExternalTitle(player: Player): String? = self.externalTitle
            override fun getKind(): ScreenKind = ScreenKind.CONFIRMATION
            override fun getBodies(player: Player): List<ScreenBody> = self.bodies
            override fun getYesButton(player: Player): ScreenButton = yesButton
            override fun getNoButton(player: Player): ScreenButton = noButton
            override fun canCloseWithEscape(): Boolean = self.canEscape
            override fun pause(): Boolean = self.pauseGame
            override fun getAfterAction(): ScreenAfterAction = self.after
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Form (confirmation + inputs)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Build a confirmation screen with input controls (text, checkbox, slider, choices).
 *
 * ```kotlin
 * formScreen("Settings") {
 *     text("name", "Nickname") { initial(player.name); maxLength(16) }
 *     bool("fly", "Allow flight") { initial(player.allowFlight) }
 *     number("speed", "Walk speed", 0.1f, 1f) { step(0.05f); initial(0.2f) }
 *     option("color", "Name color") {
 *         choice("red", "&cRed")
 *         choice("blue", "&9Blue", initial = true)
 *     }
 *     onSubmit { click ->
 *         player.allowFlight = click.values.bool("fly")
 *         player.walkSpeed = click.values.number("speed")
 *     }
 *     onCancel { click -> click.player.sendMessage("Cancelled") }
 * }
 * ```
 */
fun formScreen(title: String, block: FormScreenBuilder.() -> Unit): Screen =
    FormScreenBuilder(title).apply(block).build()

@ScreenDslMarker
class FormScreenBuilder(private var title: String) {
    private var externalTitle: String? = null
    private val bodies = mutableListOf<ScreenBody>()
    private val inputs = mutableListOf<ScreenInput>()
    private var submit: ScreenButton = ScreenButton("&aSubmit")
    private var cancel: ScreenButton = ScreenButton("&cCancel")
    private var canEscape: Boolean = true
    private var pauseGame: Boolean = false
    private var after: ScreenAfterAction = ScreenAfterAction.CLOSE
    private var onSubmitHandler: ((ScreenClick) -> Unit)? = null
    private var onCancelHandler: ((ScreenClick) -> Unit)? = null

    fun title(title: String) { this.title = title }
    fun externalTitle(title: String) { this.externalTitle = title }
    fun canCloseWithEscape(enabled: Boolean = true) { canEscape = enabled }
    fun cannotEscape() { canEscape = false }
    fun pause(enabled: Boolean = true) { pauseGame = enabled }
    fun afterAction(action: ScreenAfterAction) { after = action }
    fun keepOpen() { after = ScreenAfterAction.KEEP_OPEN }

    fun body(text: String, width: Int = ScreenBody.DEFAULT_WIDTH) {
        bodies += ScreenBody.Message(text, width)
    }

    fun item(stack: ItemStack, description: String? = null) {
        bodies += ScreenBody.Item(stack, description)
    }

    fun text(key: String, label: String, block: TextInputBuilder.() -> Unit = {}) {
        inputs += TextInputBuilder(key, label).apply(block).build()
    }

    fun bool(key: String, label: String, block: BoolInputBuilder.() -> Unit = {}) {
        inputs += BoolInputBuilder(key, label).apply(block).build()
    }

    fun number(
        key: String,
        label: String,
        start: Float,
        end: Float,
        block: NumberInputBuilder.() -> Unit = {},
    ) {
        inputs += NumberInputBuilder(key, label, start, end).apply(block).build()
    }

    fun option(key: String, label: String, block: OptionInputBuilder.() -> Unit) {
        inputs += OptionInputBuilder(key, label).apply(block).build()
    }

    fun submit(label: String = "&aSubmit", block: ScreenButtonBuilder.() -> Unit = {}) {
        submit = ScreenButtonBuilder(label).apply(block).build()
    }

    fun cancel(label: String = "&cCancel", block: ScreenButtonBuilder.() -> Unit = {}) {
        this.cancel = ScreenButtonBuilder(label).apply(block).build()
    }

    fun onSubmit(handler: (ScreenClick) -> Unit) {
        onSubmitHandler = handler
    }

    fun onCancel(handler: (ScreenClick) -> Unit) {
        onCancelHandler = handler
    }

    fun build(): Screen {
        val self = this
        val submitButton = if (self.submit.action == null && self.onSubmitHandler != null) {
            self.submit.copy(action = ScreenAction.Click(self.onSubmitHandler!!))
        } else {
            self.submit
        }
        val cancelButton = if (self.cancel.action == null && self.onCancelHandler != null) {
            self.cancel.copy(action = ScreenAction.Click(self.onCancelHandler!!))
        } else {
            self.cancel
        }
        return object : Screen() {
            override fun getTitle(player: Player): String = self.title
            override fun getExternalTitle(player: Player): String? = self.externalTitle
            override fun getKind(): ScreenKind = ScreenKind.CONFIRMATION
            override fun getBodies(player: Player): List<ScreenBody> = self.bodies
            override fun getInputs(player: Player): List<ScreenInput> = self.inputs
            override fun getYesButton(player: Player): ScreenButton = submitButton
            override fun getNoButton(player: Player): ScreenButton = cancelButton
            override fun canCloseWithEscape(): Boolean = self.canEscape
            override fun pause(): Boolean = self.pauseGame
            override fun getAfterAction(): ScreenAfterAction = self.after
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Button builder
// ─────────────────────────────────────────────────────────────────────────────

@ScreenDslMarker
class ScreenButtonBuilder(private var label: String) {
    private var tooltip: String? = null
    private var width: Int = ScreenButton.DEFAULT_WIDTH
    private var action: ScreenAction? = null

    fun label(label: String) { this.label = label }
    fun tooltip(tooltip: String) { this.tooltip = tooltip }
    fun width(width: Int) {
        require(width in 1..1024) { "button width must be 1..1024" }
        this.width = width
    }

    fun onClick(handler: (ScreenClick) -> Unit) {
        action = ScreenAction.Click(handler)
    }

    fun url(url: String) { action = ScreenAction.Url(url) }
    fun command(command: String) { action = ScreenAction.Command(command) }
    fun suggest(command: String) { action = ScreenAction.Suggest(command) }
    fun copy(text: String) { action = ScreenAction.Copy(text) }
    fun open(screen: Screen) { action = ScreenAction.Open(screen) }

    fun build(): ScreenButton = ScreenButton(label, tooltip, width, action)
}

// ─────────────────────────────────────────────────────────────────────────────
// Input builders
// ─────────────────────────────────────────────────────────────────────────────

@ScreenDslMarker
class TextInputBuilder(private val key: String, private val label: String) {
    private var initial: String = ""
    private var maxLength: Int = 32
    private var width: Int = ScreenInput.DEFAULT_WIDTH
    private var labelVisible: Boolean = true
    private var maxLines: Int? = null
    private var height: Int? = null

    fun initial(value: String) { initial = value }
    fun maxLength(value: Int) { maxLength = value }
    fun width(value: Int) { width = value }
    fun labelVisible(visible: Boolean = true) { labelVisible = visible }
    fun multiline(maxLines: Int? = null, height: Int? = null) {
        this.maxLines = maxLines
        this.height = height
    }

    fun build(): ScreenInput.Text {
        ScreenInput.requireValidKey(key)
        return ScreenInput.Text(key, label, initial, maxLength, width, labelVisible, maxLines, height)
    }
}

@ScreenDslMarker
class BoolInputBuilder(private val key: String, private val label: String) {
    private var initial: Boolean = false
    private var onTrue: String = "true"
    private var onFalse: String = "false"

    fun initial(value: Boolean) { initial = value }
    fun onTrue(value: String) { onTrue = value }
    fun onFalse(value: String) { onFalse = value }

    fun build(): ScreenInput.Bool {
        ScreenInput.requireValidKey(key)
        return ScreenInput.Bool(key, label, initial, onTrue, onFalse)
    }
}

@ScreenDslMarker
class NumberInputBuilder(
    private val key: String,
    private val label: String,
    private val start: Float,
    private val end: Float,
) {
    private var initial: Float? = null
    private var step: Float? = null
    private var width: Int = ScreenInput.DEFAULT_WIDTH
    private var labelFormat: String = "options.generic_value"

    fun initial(value: Float) { initial = value }
    fun step(value: Float) { step = value }
    fun width(value: Int) { width = value }
    fun labelFormat(format: String) { labelFormat = format }

    fun build(): ScreenInput.Number {
        ScreenInput.requireValidKey(key)
        return ScreenInput.Number(key, label, start, end, initial, step, width, labelFormat)
    }
}

@ScreenDslMarker
class OptionInputBuilder(private val key: String, private val label: String) {
    private val choices = mutableListOf<ScreenInput.Choice>()
    private var width: Int = ScreenInput.DEFAULT_WIDTH
    private var labelVisible: Boolean = true

    fun width(value: Int) { width = value }
    fun labelVisible(visible: Boolean = true) { labelVisible = visible }

    fun choice(id: String, display: String? = null, initial: Boolean = false) {
        choices += ScreenInput.Choice(id, display, initial)
    }

    fun build(): ScreenInput.Option {
        ScreenInput.requireValidKey(key)
        require(choices.isNotEmpty()) { "option input '$key' needs at least one choice" }
        if (choices.none { it.initial }) {
            choices[0] = choices[0].copy(initial = true)
        }
        return ScreenInput.Option(key, label, choices.toList(), width, labelVisible)
    }
}
