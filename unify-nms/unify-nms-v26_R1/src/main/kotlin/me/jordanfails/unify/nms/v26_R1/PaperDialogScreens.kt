package me.jordanfails.unify.nms.v26_R1

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import me.jordanfails.unify.screen.Screen
import me.jordanfails.unify.screen.ScreenAction
import me.jordanfails.unify.screen.ScreenAfterAction
import me.jordanfails.unify.screen.ScreenBody
import me.jordanfails.unify.screen.ScreenButton
import me.jordanfails.unify.screen.ScreenClick
import me.jordanfails.unify.screen.ScreenInput
import me.jordanfails.unify.screen.ScreenKind
import me.jordanfails.unify.screen.ScreenValues
import me.jordanfails.unify.screen.Screens
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player

/**
 * Paper Dialog API bridge for Unify [Screen]s.
 */
internal object PaperDialogScreens {

    fun open(player: Player, screen: Screen): Boolean {
        return try {
            player.showDialog(build(player, screen))
            true
        } catch (ex: Exception) {
            ex.printStackTrace()
            false
        }
    }

    fun close(player: Player) {
        try {
            player.closeDialog()
        } catch (_: Exception) {
            player.closeInventory()
        }
    }

    private fun build(player: Player, screen: Screen): Dialog {
        val after = screen.getAfterAction()
        val pause = if (after == ScreenAfterAction.KEEP_OPEN) false else screen.pause()
        val paperAfter = when (after) {
            ScreenAfterAction.CLOSE -> DialogBase.DialogAfterAction.CLOSE
            ScreenAfterAction.KEEP_OPEN -> DialogBase.DialogAfterAction.NONE
            ScreenAfterAction.WAIT -> DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE
        }

        val baseBuilder = DialogBase.builder(adventure(screen.getTitle(player)))
            .canCloseWithEscape(screen.canCloseWithEscape())
            .pause(pause)
            .afterAction(paperAfter)
            .body(screen.getBodies(player).map { toBody(it) })
            .inputs(screen.getInputs(player).map { toInput(it) })

        screen.getExternalTitle(player)?.let { baseBuilder.externalTitle(adventure(it)) }

        val type = when (screen.getKind()) {
            ScreenKind.NOTICE -> {
                val button = screen.getButtons(player).firstOrNull()
                if (button == null) DialogType.notice()
                else DialogType.notice(toActionButton(player, screen, button, after))
            }
            ScreenKind.CONFIRMATION -> {
                val yes = screen.getYesButton(player)
                    ?: ScreenButton("&aConfirm")
                val no = screen.getNoButton(player)
                    ?: ScreenButton("&cCancel")
                DialogType.confirmation(
                    toActionButton(player, screen, yes, after),
                    toActionButton(player, screen, no, after),
                )
            }
            ScreenKind.MULTI -> {
                val actions = screen.getButtons(player)
                if (actions.isEmpty()) {
                    DialogType.notice()
                } else {
                    val builder = DialogType.multiAction(
                        actions.map { toActionButton(player, screen, it, after) }
                    ).columns(screen.getColumns().coerceAtLeast(1))
                    screen.getExitButton(player)?.let {
                        builder.exitAction(toActionButton(player, screen, it, after))
                    }
                    builder.build()
                }
            }
        }

        return Dialog.create { factory ->
            factory.empty()
                .base(baseBuilder.build())
                .type(type)
        }
    }

    private fun toBody(body: ScreenBody): DialogBody = when (body) {
        is ScreenBody.Message -> DialogBody.plainMessage(adventure(body.text), body.width.coerceIn(1, 1024))
        is ScreenBody.Item -> {
            val builder = DialogBody.item(body.item)
                .showDecorations(body.showDecorations)
                .showTooltip(body.showTooltip)
                .width(body.width.coerceIn(1, 256))
                .height(body.height.coerceIn(1, 256))
            body.description?.let {
                builder.description(DialogBody.plainMessage(adventure(it), body.descriptionWidth.coerceIn(1, 1024)))
            }
            builder.build()
        }
    }

    private fun toInput(input: ScreenInput): DialogInput = when (input) {
        is ScreenInput.Text -> {
            val builder = DialogInput.text(input.key, adventure(input.label))
                .width(input.width.coerceIn(1, 1024))
                .labelVisible(input.labelVisible)
                .initial(input.initial)
                .maxLength(input.maxLength.coerceAtLeast(1))
            if (input.multiline) {
                builder.multiline(TextDialogInput.MultilineOptions.create(input.maxLines, input.height))
            }
            builder.build()
        }
        is ScreenInput.Bool -> DialogInput.bool(input.key, adventure(input.label))
            .initial(input.initial)
            .onTrue(input.onTrue)
            .onFalse(input.onFalse)
            .build()
        is ScreenInput.Number -> {
            val builder = DialogInput.numberRange(
                input.key,
                adventure(input.label),
                input.start,
                input.end,
            )
                .width(input.width.coerceIn(1, 1024))
                .labelFormat(input.labelFormat)
            input.initial?.let { builder.initial(it) }
            input.step?.let { builder.step(it) }
            builder.build()
        }
        is ScreenInput.Option -> {
            val entries = input.choices.map { choice ->
                SingleOptionDialogInput.OptionEntry.create(
                    choice.id,
                    choice.display?.let { adventure(it) },
                    choice.initial,
                )
            }
            DialogInput.singleOption(input.key, adventure(input.label), entries)
                .width(input.width.coerceIn(1, 1024))
                .labelVisible(input.labelVisible)
                .build()
        }
    }

    private fun toActionButton(
        player: Player,
        screen: Screen,
        button: ScreenButton,
        after: ScreenAfterAction,
    ): ActionButton {
        return ActionButton.create(
            adventure(button.label),
            button.tooltip?.let { adventure(it) },
            button.width.coerceIn(1, 1024),
            toDialogAction(player, screen, button.action, after),
        )
    }

    private fun toDialogAction(
        player: Player,
        screen: Screen,
        action: ScreenAction?,
        after: ScreenAfterAction,
    ): DialogAction? {
        if (action == null) return null
        return when (action) {
            is ScreenAction.Url -> DialogAction.staticAction(ClickEvent.openUrl(action.url))
            is ScreenAction.Command -> {
                val command = action.command.removePrefix("/")
                DialogAction.staticAction(ClickEvent.runCommand(command))
            }
            is ScreenAction.Suggest -> DialogAction.staticAction(ClickEvent.suggestCommand(action.command))
            is ScreenAction.Copy -> DialogAction.staticAction(ClickEvent.copyToClipboard(action.text))
            is ScreenAction.Open -> clickCallback(after) { view, audience ->
                val clicker = audience as? Player ?: player
                Screens.open(clicker, action.screen, track = true)
            }
            is ScreenAction.Click -> clickCallback(after) { view, audience ->
                val clicker = audience as? Player ?: player
                val values = extractValues(view, screen.getInputs(clicker))
                action.handler(ScreenClick(clicker, values, screen))
            }
        }
    }

    private fun clickCallback(
        after: ScreenAfterAction,
        handler: (DialogResponseView?, Audience) -> Unit,
    ): DialogAction {
        val uses = if (after == ScreenAfterAction.KEEP_OPEN) {
            ClickCallback.UNLIMITED_USES
        } else {
            1
        }
        val options = ClickCallback.Options.builder()
            .uses(uses)
            .lifetime(ClickCallback.DEFAULT_LIFETIME)
            .build()
        return DialogAction.customClick({ view, audience ->
            handler(view, audience)
        }, options)
    }

    private fun extractValues(view: DialogResponseView?, inputs: List<ScreenInput>): ScreenValues {
        if (view == null) return ScreenValues(emptyMap())
        val data = linkedMapOf<String, Any?>()
        for (input in inputs) {
            data[input.key] = when (input) {
                is ScreenInput.Bool -> view.getBoolean(input.key)
                is ScreenInput.Number -> view.getFloat(input.key)
                is ScreenInput.Text, is ScreenInput.Option -> view.getText(input.key)
            }
        }
        return ScreenValues(data)
    }

    private fun adventure(text: String): Component {
        if (text.contains('<') && text.contains('>')) {
            try {
                return MiniMessage.miniMessage()
                    .deserialize(text.replace('§', '&'))
                    .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
            } catch (_: Exception) {
            }
        }
        return LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build()
            .deserialize(text.replace('§', '&'))
            .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
    }
}
