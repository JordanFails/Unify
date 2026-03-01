package me.jordanfails.unify.utils.prompt

import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.sendMessage
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.conversations.ConversationContext
import org.bukkit.conversations.Prompt
import org.bukkit.conversations.StringPrompt
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable

class InputPrompt : StringPrompt() {

    private var promptText: String = "${ChatColor.GREEN}Please input a value."
    private var charLimit: Int = -1
    private var regex: Regex? = null
    private var caseInsensitive: Boolean = false

    private var use: ((String) -> Unit)? = null
    private var fail: ((String) -> Unit)? = null
    private var timeout: (() -> Unit)? = null
    private var cancel: (() -> Unit)? = null

    private var timeLimitSeconds: Int = -1
    private var retryLimit: Int = -1
    private var currentRetries = 0

    private var timeoutTask: BukkitRunnable? = null

    fun withText(text: String): InputPrompt {
        this.promptText = CC.translate(text)
        return this
    }

    fun withLimit(limit: Int): InputPrompt {
        this.charLimit = limit
        return this
    }

    fun withRegex(regex: Regex, caseInsensitive: Boolean = false): InputPrompt {
        this.regex = if (caseInsensitive) Regex(regex.pattern, RegexOption.IGNORE_CASE) else regex
        this.caseInsensitive = caseInsensitive
        return this
    }

    fun withTimeLimit(seconds: Int, onTimeout: (() -> Unit)? = null): InputPrompt {
        this.timeLimitSeconds = seconds
        this.timeout = onTimeout
        return this
    }

    fun withRetryLimit(limit: Int): InputPrompt {
        this.retryLimit = limit
        return this
    }

    fun acceptInput(use: (String) -> Unit): InputPrompt {
        this.use = use
        return this
    }

    fun onFail(fail: (String) -> Unit): InputPrompt {
        this.fail = fail
        return this
    }

    fun onCancel(cancel: () -> Unit): InputPrompt {
        this.cancel = cancel
        return this
    }

    override fun getPromptText(context: ConversationContext): String {
        return promptText
    }

    override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
        val player = context.forWhom as? Player ?: return END_OF_CONVERSATION

        timeoutTask?.cancel()

        val safeInput = input ?: ""

        // Cancel if user types "cancel" or "exit"
        if (safeInput.equals("cancel", ignoreCase = true) || safeInput.equals("exit", ignoreCase = true)) {
            player.sendMessage(Component.text("${ChatColor.YELLOW}Conversation cancelled."))
            cancel?.invoke()
            return END_OF_CONVERSATION
        }

        // Character limit check
        if (charLimit != -1 && safeInput.length > charLimit) {
            player.sendMessage(Component.text("${ChatColor.RED}Input text is too long! (${safeInput.length} > $charLimit)"))
            return failAndRetry(safeInput, player)
        }

        // Regex validation
        regex?.let {
            if (!safeInput.matches(it)) {
                player.sendMessage(Component.text("${ChatColor.RED}Input does not match the expected pattern (${it.pattern})."))
                return failAndRetry(safeInput, player)
            }
        }

        // Execute handler safely
        return try {
            use?.invoke(safeInput)
            END_OF_CONVERSATION
        } catch (e: Exception) {
            player.sendMessage(Component.text("${ChatColor.RED}An error occurred while handling input."))
            fail?.invoke(safeInput)
            END_OF_CONVERSATION
        }
    }

    private fun failAndRetry(input: String, player: Player): Prompt? {
        fail?.invoke(input)
        currentRetries++

        if (retryLimit != -1 && currentRetries >= retryLimit) {
            player.sendMessage(Component.text("${ChatColor.RED}Too many failed attempts. Conversation ended."))
            return END_OF_CONVERSATION
        }

        startTimer(player)
        return this
    }

    private fun startTimer(player: Player) {
        if (timeLimitSeconds <= 0) return

        timeoutTask?.cancel()
        timeoutTask = object : BukkitRunnable() {
            override fun run() {
                player.sendMessage(Component.text("${ChatColor.RED}Input time limit reached. Conversation cancelled."))
                timeout?.invoke()
            }
        }

        timeoutTask!!.runTaskLaterAsynchronously(
            Bukkit.getPluginManager().getPlugin("AscendCore")!!,
            20L * timeLimitSeconds
        )
    }

    fun start(player: Player) {
        require(use != null) { "No acceptInput() handler provided before starting prompt." }

        startTimer(player)
        ConversationUtil.startConversation(player, this)
    }

    fun cancel() {
        timeoutTask?.cancel()
    }
}
